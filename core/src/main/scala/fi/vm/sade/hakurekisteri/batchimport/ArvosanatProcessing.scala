package fi.vm.sade.hakurekisteri.batchimport

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import fi.vm.sade.hakurekisteri.Config
import fi.vm.sade.hakurekisteri.arvosana.{Arvio410, Arvosana}
import fi.vm.sade.hakurekisteri.integration.henkilo._
import fi.vm.sade.hakurekisteri.integration.koodisto.{GetKoodistoKoodiArvot, KoodistoKoodiArvot}
import fi.vm.sade.hakurekisteri.integration.organisaatio.{Oppilaitos, OppilaitosResponse}
import fi.vm.sade.hakurekisteri.storage.Identified
import fi.vm.sade.hakurekisteri.suoritus.{yksilollistaminen, Suoritus, SuoritusQuery, VirallinenSuoritus}
import fi.vm.sade.hakurekisteri.tools.RicherString._
import org.joda.time.{DateTime, LocalDate}

import scala.collection.immutable.Iterable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Node

class ArvosanatProcessing(organisaatioActor: ActorRef, henkiloActor: ActorRef, suoritusrekisteri: ActorRef, arvosanarekisteri: ActorRef, importBatchActor: ActorRef, koodistoActor: ActorRef)(implicit val system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = 15.minutes

  def process(batch: ImportBatch): Future[ImportBatch with Identified[UUID]] = {
    fetchOppiaineetKoodisto() flatMap
      importArvosanat(batch) flatMap
      saveDoneBatch(batch) recoverWith
      saveFailedBatch(batch)
  }

  private def saveFailedBatch(batch: ImportBatch): PartialFunction[Throwable, Future[ImportBatch with Identified[UUID]]] = {
    case t: Throwable => (importBatchActor ? batch.copy(status = batch.status.copy(
      processedTime = Some(new DateTime()),
      savedReferences = None,
      totalRows = None,
      successRows = None,
      failureRows = None,
      messages = Map("virhe" -> Set(t.toString))
    ), state = BatchState.FAILED)).mapTo[ImportBatch with Identified[UUID]]
  }

  private def saveDoneBatch(batch: ImportBatch)(statukset: Seq[ImportArvosanaStatus]): Future[ImportBatch with Identified[UUID]] = {
    val refs = extractReferences(statukset)
    val b = batch.copy(status = batch.status.copy(
      processedTime = Some(new DateTime()),
      savedReferences = Some(refs),
      totalRows = Some(statukset.size),
      successRows = Some(refs.size),
      failureRows = Some(statukset.size - refs.size),
      messages = extractMessages(statukset)
    ), state = BatchState.DONE)

    (importBatchActor ? b).mapTo[ImportBatch with Identified[UUID]]
  }

  private def importArvosanat(batch: ImportBatch)(oppiaineet: Seq[String]): Future[Seq[ImportArvosanaStatus]] =
    Future.sequence(processBatch(batch)(oppiaineet))

  private def extractMessages(statukset: Seq[ImportArvosanaStatus]): Map[String, Set[String]] = statukset.collect {
    case FailureStatus(tunniste: String, errors: Seq[Throwable]) => tunniste -> errors.map(_.toString).toSet
  }.toMap

  private def extractReferences(statukset: Seq[ImportArvosanaStatus]): Map[String, Map[String, String]] = statukset.collect {
    case OkStatus(tunniste, refs) => tunniste -> refs.map(t => t._1.toString -> t._2.map(_.toString).mkString(", ")).toMap
  }.toMap

  private def fetchOppiaineetKoodisto(): Future[Seq[String]] =
    (koodistoActor ? GetKoodistoKoodiArvot("oppiaineetyleissivistava")).mapTo[KoodistoKoodiArvot].map(arvot => arvot.arvot)

  private def processBatch(batch: ImportBatch)(oppiaineet: Seq[String]): Seq[Future[ImportArvosanaStatus]] = {
    def saveArvosana(s: Suoritus with Identified[UUID], arvosana: ImportArvosana): Future[Arvosana with Identified[UUID]] = {
      (arvosanarekisteri ? toArvosana(arvosana)(s.id)(batch.source)).mapTo[Arvosana with Identified[UUID]]
    }
    def saveTodistus(henkilo: (String, String, Seq[(ImportTodistus, String)]), todistus: (ImportTodistus, String)): Future[Seq[ArvosanaStatus]] = {
      val savedTodistus = for (
        s <- fetchSuoritus(henkilo._2, todistus._1, todistus._2, batch.source)
      ) yield for (
          arvosana <- todistus._1.arvosanat
        ) yield saveArvosana(s, arvosana)

      savedTodistus.flatMap((arvosanat: Seq[Future[Arvosana with Identified[UUID]]]) => {
        Future.sequence(arvosanat.map(arvosana => {
          arvosana.map(a => OkArvosanaStatus(a.id, a.suoritus, henkilo._1)).recoverWith {
            case th: Throwable => Future.successful(FailureArvosanaStatus(henkilo._1, th))
          }
        }))
      }).recoverWith {
        case th: Throwable => Future.successful(Seq(FailureArvosanaStatus(henkilo._1, th)))
      }
    }
    for (
      henkilot <- enrich(parseData(batch)(oppiaineet))
    ) yield henkilot.flatMap(henkilo => {
      val todistukset = for (
        todistus <- henkilo._3
      ) yield saveTodistus(henkilo, todistus)

      Future.sequence(todistukset).map((tods: Seq[Seq[ArvosanaStatus]]) => {
        val arvosanaStatukset = tods.foldLeft[Seq[ArvosanaStatus]](Seq())(_ ++ _)
        arvosanaStatukset.find(_.isInstanceOf[FailureArvosanaStatus]) match {
          case Some(FailureArvosanaStatus(tunniste, _)) =>
            FailureStatus(tunniste, arvosanaStatukset.filter(_.isInstanceOf[FailureArvosanaStatus]).asInstanceOf[Seq[FailureArvosanaStatus]].map(_.t))
          case None =>
            OkStatus(henkilo._1, arvosanaStatukset.asInstanceOf[Seq[OkArvosanaStatus]].groupBy(_.suoritus).map(t => t._1 -> t._2.map(_.id)))
        }
      })
    })
  }

  private trait ArvosanaStatus
  private case class OkArvosanaStatus(id: UUID, suoritus: UUID, tunniste: String) extends ArvosanaStatus
  private case class FailureArvosanaStatus(tunniste: String, t: Throwable) extends ArvosanaStatus

  private def toArvosana(arvosana: ImportArvosana)(suoritus: UUID)(source: String): Arvosana =
    Arvosana(suoritus, Arvio410(arvosana.arvosana), arvosana.aine, arvosana.lisatieto, arvosana.valinnainen, None, source)

  private def fetchSuoritus(henkiloOid: String, todistus: ImportTodistus, oppilaitosOid: String, lahde: String): Future[Suoritus with Identified[UUID]] = {
    (suoritusrekisteri ? SuoritusQuery(henkilo = Some(henkiloOid), myontaja = Some(oppilaitosOid))).mapTo[Seq[Suoritus with Identified[UUID]]].map(_.find(matchSuoritus(todistus))).flatMap {
      case Some(s) => Future.successful(s)
      case None if todistus.komo == Config.lukioKomoOid => createLukioSuoritus(henkiloOid, todistus, oppilaitosOid, lahde)
      case None => Future.failed(SuoritusNotFoundException(henkiloOid, todistus, oppilaitosOid))
    }
  }

  private def createLukioSuoritus(henkiloOid: String, todistus: ImportTodistus, oppilaitosOid: String, lahde: String): Future[Suoritus with Identified[UUID]] =
    (suoritusrekisteri ? VirallinenSuoritus(todistus.komo, oppilaitosOid, "KESKEN", todistus.valmistuminen, henkiloOid, yksilollistaminen.Ei, todistus.suoritusKieli, None, vahv = true, lahde)).mapTo[Suoritus with Identified[UUID]]

  private def matchSuoritus(todistus: ImportTodistus)(suoritus: Suoritus): Boolean = (todistus, suoritus) match {
    case (ImportTodistus(Config.perusopetusKomoOid, _, _, v, _), s: VirallinenSuoritus) if s.komo == Config.perusopetusKomoOid && s.valmistuminen == v => true
    case (ImportTodistus(Config.lisaopetusKomoOid, _, _, v, _), s: VirallinenSuoritus) if s.komo == Config.lisaopetusKomoOid && s.valmistuminen == v => true
    case (ImportTodistus(Config.lukioKomoOid, _, _, v, _), s: VirallinenSuoritus) if s.komo == Config.lukioKomoOid && s.valmistuminen == v => true
    case _ => false
  }

  private def enrich(henkilot: Map[String, ImportArvosanaHenkilo]): Seq[Future[(String, String, Seq[(ImportTodistus, String)])]] = {
    val enriched: Iterable[Future[(String, String, Seq[Future[(ImportTodistus, String)]])]] = for (
      (tunniste: String, henkilo: ImportArvosanaHenkilo) <- henkilot
    ) yield {
      val q: HenkiloQuery = henkilo.tunniste match {
        case ImportHetu(hetu) => HenkiloQuery(None, Some(hetu), tunniste)
        case ImportOppijanumero(oid) => HenkiloQuery(Some(oid), None, tunniste)
        case ImportHenkilonTunniste(_, _, _) => throw HenkiloTunnisteNotSupportedException
      }
      for (
        henk <- (henkiloActor ? q).mapTo[FoundHenkilos]
      ) yield {
        if (henk.henkilot.isEmpty) throw HenkiloNotFoundException(q.oppijanumero.getOrElse(q.hetu.getOrElse("")))
        val todistukset: Seq[Future[(ImportTodistus, String)]] = for (
          todistus: ImportTodistus <- henkilo.todistukset
        ) yield for (
            oppilaitos <- (organisaatioActor ? Oppilaitos(todistus.myontaja)).mapTo[OppilaitosResponse]
          ) yield (todistus, oppilaitos.oppilaitos.oid)

        (tunniste, henk.henkilot.head.oidHenkilo, todistukset)
      }
    }

    enriched.map(_.flatMap(h => Future.sequence(h._3).map(tods => (h._1, h._2, tods)))).toSeq
  }

  private def parseData(batch: ImportBatch)(oppiaineet: Seq[String]): Map[String, ImportArvosanaHenkilo] =
    (batch.data \ "henkilot" \ "henkilo").map(ImportArvosanaHenkilo(_)(batch.source)(oppiaineet)).groupBy(_.tunniste.tunniste).mapValues(_.head)

  case class SuoritusNotFoundException(henkiloOid: String, todistus: ImportTodistus, oppilaitosOid: String) extends Exception(s"suoritus not found for henkilo $henkiloOid with myontaja $oppilaitosOid for todistus $todistus")
  object HenkiloTunnisteNotSupportedException extends Exception("henkilo tunniste not yet supported in arvosana batch")
  case class ImportArvosana(aine: String, arvosana: String, lisatieto: Option[String], valinnainen: Boolean)
  case class ImportTodistus(komo: String, myontaja: String, arvosanat: Seq[ImportArvosana], valmistuminen: LocalDate, suoritusKieli: String)
  case class ImportArvosanaHenkilo(tunniste: ImportTunniste, todistukset: Seq[ImportTodistus])
  object ImportArvosanaHenkilo {
    def getField(name: String)(h: Node): String = (h \ name).head.text
    def getOptionField(name: String)(h: Node): Option[String] = (h \ name).headOption.flatMap(_.text.blankOption)

    def arvosanat(h: Node)(oppiaineet: Seq[String]): Seq[ImportArvosana] = {
      oppiaineet.map(name => {
        (h \ name).headOption.collect {
          case s =>
            val lisatieto = name match {
              case "AI" => Some(getField("tyyppi")(s))
              case _ => getOptionField("kieli")(s)
            }
            (s \ "valinnainen").map(a => ImportArvosana(name, a.text, lisatieto, valinnainen = true)) :+ ImportArvosana(name, getField("yhteinen")(s), lisatieto, valinnainen = false)
        }
      }).flatten.foldLeft[Seq[ImportArvosana]](Seq())(_ ++ _)
    }

    def todistus(name: String, komoOid: String, oppijanumero: Option[String])(h: Node)(lahde: String)(oppiaineet: Seq[String]): Option[ImportTodistus] = (h \ name).headOption.map(s => {
      val valmistuminen = getField("valmistuminen")(s)
      val myontaja = getField("myontaja")(s)
      val suoritusKieli = getField("suorituskieli")(s)
      ImportTodistus(komoOid, myontaja, arvosanat(s)(oppiaineet), new LocalDate(valmistuminen), suoritusKieli)
    })

    val tyypit = Map(
      "perusopetus" -> Config.perusopetusKomoOid,
      "perusopetuksenlisaopetus" -> Config.lisaopetusKomoOid,
      "ammattistartti" -> Config.ammattistarttiKomoOid,
      "valmentava" -> Config.valmentavaKomoOid,
      "maahanmuuttajienlukioonvalmistava" -> Config.lukioonvalmistavaKomoOid,
      "maahanmuuttajienammvalmistava" -> Config.ammatilliseenvalmistavaKomoOid,
      "ulkomainen" -> Config.ulkomainenkorvaavaKomoOid,
      "lukio" -> Config.lukioKomoOid,
      "ammatillinen" -> Config.ammatillinenKomoOid
    )

    def apply(h: Node)(lahde: String)(oppiaineet: Seq[String]): ImportArvosanaHenkilo = {
      val hetu = getOptionField("hetu")(h)
      val oppijanumero = getOptionField("oppijanumero")(h)
      val henkiloTunniste = getOptionField("henkiloTunniste")(h)
      val syntymaAika = getOptionField("syntymaAika")(h)

      val tunniste = (hetu, oppijanumero, henkiloTunniste, syntymaAika) match {
        case (Some(henkilotunnus), _, _, _) => ImportHetu(henkilotunnus)
        case (_, Some(o), _, _) => ImportOppijanumero(o)
        case (_, _, Some(t), Some(sa)) => ImportHenkilonTunniste(t, sa, "0")
        case t =>
          throw new IllegalArgumentException(s"henkilo could not be identified: hetu, oppijanumero or henkiloTunniste+syntymaAika missing $t")
      }

      val todistuksetNode = (h \ "todistukset").head
      val todistukset = tyypit.map(t => todistus(t._1, t._2, oppijanumero)(todistuksetNode)(lahde)(oppiaineet)).toSeq.flatten

      ImportArvosanaHenkilo(
        tunniste = tunniste,
        todistukset = todistukset
      )
    }
  }
  trait ImportArvosanaStatus {
    val tunniste: String
  }
  case class OkStatus(tunniste: String, todistukset: Map[UUID, Seq[UUID]]) extends ImportArvosanaStatus
  case class FailureStatus(tunniste: String, errors: Seq[Throwable]) extends ImportArvosanaStatus
}
