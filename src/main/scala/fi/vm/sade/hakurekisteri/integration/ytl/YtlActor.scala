package fi.vm.sade.hakurekisteri.integration.ytl

import akka.actor.{ActorRef, Actor}
import java.util.UUID
import scala.xml.{Node, Elem}
import fi.vm.sade.hakurekisteri.suoritus.{yksilollistaminen, Suoritus}
import fi.vm.sade.hakurekisteri.arvosana.{ArvioYo, Arvosana}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.event.Logging
import akka.pattern.ask
import fi.vm.sade.hakurekisteri.integration.henkilo.{HenkiloResponse, HetuQuery}
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.joda.time.{MonthDay, LocalDate}
import fi.vm.sade.hakurekisteri.storage.Identified


class YtlActor(henkiloActor: ActorRef, suoritusRekisteri: ActorRef, arvosanaRekisteri: ActorRef) extends Actor {

  implicit val ec = context.dispatcher

  var batch = Batch[KokelasRequest]()
  var sent = Seq[Batch[KokelasRequest]]()


  val log = Logging(context.system, this)

  var kokelaat = Map[String, Kokelas]()

  override def receive: Actor.Receive = {
    case k:KokelasRequest => batch = k +: batch
    case Send => send(batch)
                 sent = batch +: sent
                 batch = Batch[KokelasRequest]()
    case Poll => poll(sent)
    case YtlResult(id, data) =>
      val requested = sent.find(_.id == id)
      sent = sent.filterNot(_.id == id)
      handleResponse(requested, data)
    case k: Kokelas =>
      log.debug(s"sending ytl data for ${k.oid} yo: ${k.yo} lukio: ${k.lukio}")
      k.yo foreach {
        yotutkinto =>
          suoritusRekisteri ! yotutkinto
          kokelaat = kokelaat + (k.oid -> k)
      }
      k.lukio foreach (suoritusRekisteri ! _)
    case s: Suoritus with Identified[UUID] if s.komo == "YOTUTKINTO" =>
      for (
        kokelas <- kokelaat.get(s.henkiloOid)
      ) {
        kokelas.yoTodistus.map(_.toArvosana(s)) foreach (arvosanaRekisteri ! _)
        kokelaat = kokelaat - s.henkiloOid
      }


  }

  def send(batch: Batch[KokelasRequest]) {}

  def poll(batches: Seq[Batch[KokelasRequest]]): Unit = {}

  def handleResponse(requested: Option[Batch[KokelasRequest]], data: Elem) = {

    def batch2Finder(batch:Batch[KokelasRequest])(hetu:String):Future[String] = {
      val hetuMap = batch.items.map{case KokelasRequest(oid, kokelasHetu) => kokelasHetu -> oid}.toMap
      hetuMap.get(hetu).map(Future.successful).getOrElse(Future.failed(new NoSuchElementException("can't find oid for hetu in requested data")))
    }

    val finder = requested.map(batch2Finder).getOrElse(resolveOidFromHenkiloPalvelu _)

    import YTLXml._

    val kokelaat = parseKokelaat(data, finder)

    import akka.pattern.pipe

    for (
      kokelas <- kokelaat
    ) kokelas pipeTo self


    Future.sequence(kokelaat).onComplete{
      case Success(parsed) if requested.isDefined =>
        val batch = requested.get
        val found = parsed.map(_.oid).toSet
        val missing = batch.items.map(_.oid).toSet -- found
        for (problem <- missing) log.warning(s"Missing result from YTL for oid $problem in batch ${batch.id}")
      case Failure(t) if requested.isDefined => log.error(s"failure in fetching results for ${requested.get.id}", t)
      case Failure(t) => log.error("failure fetching results from YTL", t)
      case _ =>  log.warning("no request in memory for a result from YTL")
    }


  }

  def resolveOidFromHenkiloPalvelu(hetu: String): Future[String] =
  {
    implicit val timeout: Timeout = Timeout(60, TimeUnit.SECONDS)
    (henkiloActor ? HetuQuery(hetu)).mapTo[HenkiloResponse].map(_.hetu).flatMap(
      _.map(Future.successful).getOrElse(Future.failed(new NoSuchElementException("can't find oid for hetu in henkilopalvelu"))))


  }







}

case class Batch[A](id: UUID = UUID.randomUUID(), items: Seq[A] = Seq[A]()) {

  def +:[B >: A](elem: B): Batch[B] = Batch(this.id, elem +: this.items)
}

case class KokelasRequest(oid: String, hetu: String)

case class YtlResult(batch: UUID, data: Elem)

case class Kokelas(oid: String,
                   yo: Option[Suoritus],
                   lukio: Option[Suoritus],
                   yoTodistus: Seq[Koe])

object Send

object Poll


object YTLXml {


  def parseKokelaat(data:Elem, oidFinder: String => Future[String])(implicit ec: ExecutionContext): Seq[Future[Kokelas]] = {

    val kokelaat = data \\ "YLIOPPILAS"
    kokelaat map {
      (kokelas) =>
        val hetu = (kokelas \ "HENKILOTUNNUS").text
        parseKokelas(oidFinder(hetu), kokelas)
    }

  }


  def parseKokelas(oidFuture: Future[String], kokelas: Node)(implicit ec: ExecutionContext): Future[Kokelas] = {
    for {
      oid <- oidFuture
    } yield {
      val yo = extractYo(oid, kokelas)
      Kokelas(oid, yo , extractLukio(oid, kokelas), yo.map((tutkinto) => extractTodistus(tutkinto, kokelas)).getOrElse(Seq()) )
    }
  }

  object YoTutkinto {

    def apply(suorittaja:String, valmistuminen: LocalDate, kieli:String) = Suoritus(
      komo = "YOTUTKINTO",
    myontaja = "YTL",
    tila = "VALMIS",
    valmistuminen = valmistuminen,
    henkiloOid = suorittaja,
    yksilollistaminen = yksilollistaminen.Ei,
    suoritusKieli = kieli)
  }

  val kevat = "(\\d{4})K".r
  val syksy = "(\\d{4})S".r
  val suoritettu = "suor".r

  def parseKausi(kausi: String) = kausi match {
    case kevat(vuosi) => Some(new MonthDay(6, 1).toLocalDate(vuosi.toInt))
    case syksy(vuosi) => Some(new MonthDay(12, 21).toLocalDate(vuosi.toInt))
    case _ => None
  }


  def extractYo(oid: String, kokelas: Node): Option[Suoritus] =
    for (
      valmistuminen <- parseValmistuminen(kokelas)
    ) yield {
      val kieli = (kokelas \ "TUTKINTOKIELI").text
      YoTutkinto(suorittaja = oid, valmistuminen = valmistuminen, kieli = kieli)
    }




  def parseValmistuminen(kokelas: Node): Option[LocalDate] = {

    val yoSuoritettu = (kokelas \ "YLIOPPILAAKSITULOAIKA").text
    if (yoSuoritettu.isEmpty) None
    else {
      yoSuoritettu match {
        case suoritettu() =>
          val koeTehty = (kokelas \ "TUTKINTOAIKA").text
          parseKausi(koeTehty)
        case _ =>
          parseKausi(yoSuoritettu)
      }
    }
  }

  def extractLukio(oid:String, kokelas:Node): Option[Suoritus] = None

  def extractTodistus(yo: Suoritus, kokelas: Node): Seq[Koe] = {
    (kokelas \\ "KOE").map{
      (koe: Node) =>
       val arvio = ArvioYo((koe \ "ARVOSANA").text, (koe \ "YHTEISPISTEMAARA").text.toInt)
       val valinnaisuus = (koe \ "AINEYHDISTELMAROOLI").text.toInt >= 60
        Koe(arvio, (koe \ "KOETUNNUS").text, valinnainen = valinnaisuus, parseKausi((koe \ "TUTKINTOKERTA").text).get)
    }

  }




}

case class Koe(arvio: ArvioYo, aine: String, valinnainen: Boolean, myonnetty: LocalDate) {

  def toArvosana(suoritus: Suoritus with Identified[UUID]) = {
    Arvosana(suoritus.id, arvio, aine: String, None, valinnainen: Boolean, Some(myonnetty))
  }
}