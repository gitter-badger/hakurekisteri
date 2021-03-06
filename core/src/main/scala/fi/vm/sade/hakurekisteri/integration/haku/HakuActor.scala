package fi.vm.sade.hakurekisteri.integration.haku

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.pipe
import fi.vm.sade.hakurekisteri.Config
import fi.vm.sade.hakurekisteri.dates.{Ajanjakso, InFuture}
import fi.vm.sade.hakurekisteri.integration.hakemus.ReloadHaku
import fi.vm.sade.hakurekisteri.integration.parametrit.{HakuParams, KierrosRequest}
import fi.vm.sade.hakurekisteri.integration.tarjonta._
import fi.vm.sade.hakurekisteri.integration.valintatulos.UpdateValintatulos
import fi.vm.sade.hakurekisteri.integration.ytl.HakuList
import fi.vm.sade.hakurekisteri.tools.RicherString._
import org.joda.time.{DateTime, ReadableInstant}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions


class HakuActor(tarjonta: ActorRef, parametrit: ActorRef, hakemukset: ActorRef, valintaTulos: ActorRef, ytl: ActorRef, config: Config) extends Actor with ActorLogging {
  implicit val ec = context.dispatcher

  var activeHakus: Seq[Haku] = Seq()
  val hakuRefreshTime = config.integrations.hakuRefreshTimeHours.hours
  val hakemusRefreshTime = config.integrations.hakemusRefreshTimeHours.hours
  val valintatulosRefreshTimeHours = config.integrations.valintatulosRefreshTimeHours.hours
  var starting = true

  context.system.scheduler.schedule(1.second, hakuRefreshTime, self, Update)

  import FutureList._

  def getHaku(q: GetHaku): Haku = {
    activeHakus.find(_.oid == q.oid) match {
      case None => throw HakuNotFoundException(s"haku not found with oid ${q.oid}")
      case Some(h) => h
    }
  }

  override def receive: Actor.Receive = {
    case Update =>
      log.info(s"updating all hakus for ${self.path.toString}")
      tarjonta ! GetHautQuery

    case HakuRequest => sender ! activeHakus

    case q: GetHaku => sender ! getHaku(q)

    case RestHakuResult(hakus: List[RestHaku]) => enrich(hakus).waitForAll pipeTo self

    case rh:ReloadHaku if activeHakus.exists(_.oid == rh.haku) => hakemukset forward rh

    case sq: Seq[_] =>
      val s = sq.collect{ case h: Haku => h}
      activeHakus = s.filter(_.aika.isCurrently)
      ytl ! HakuList(activeHakus.filter(_.kkHaku).map(_.oid).toSet)
      log.info(s"current hakus [${activeHakus.map(h => h.oid).mkString(", ")}]")
      if (starting) {
        starting = false
        context.system.scheduler.schedule(1.second, valintatulosRefreshTimeHours, self, RefreshSijoittelu)
        context.system.scheduler.schedule(1.second, hakemusRefreshTime, self, ReloadHakemukset)
      }

    case RefreshSijoittelu => refreshKeepAlives()

    case ReloadHakemukset =>
      for(
        haku <- activeHakus
      ) hakemukset ! ReloadHaku(haku.oid)

    case Failure(t: GetHautQueryFailedException) =>
      log.error(s"${t.getMessage}, retrying in a minute")
      context.system.scheduler.scheduleOnce(1.minute, self, Update)

    case Failure(t) =>
      log.error(t, s"got failure from ${sender()}")

  }

  def enrich(hakus: List[RestHaku]): List[Future[Haku]] = {
    for (
      haku <- hakus
      if haku.oid.isDefined && haku.hakuaikas.nonEmpty
    ) yield getKierrosEnd(haku.oid.get).map(Haku(haku))
  }

  def getKierrosEnd(hakuOid: String): Future[ReadableInstant] = {
    import akka.pattern.ask
    import akka.util.Timeout

    import scala.concurrent.duration._
    implicit val to: Timeout = 2.minutes
    (parametrit ? KierrosRequest(hakuOid)).mapTo[HakuParams].map(_.end).recover {
      case _ => InFuture
    }
  }

  def refreshKeepAlives() {
    activeHakus.foreach((haku: Haku) => valintaTulos.!(UpdateValintatulos(haku.oid))(ActorRef.noSender))
  }
}

object Update

object HakuRequest

object ReloadHakemukset

object RefreshSijoittelu

case class HakuNotFoundException(m: String) extends Exception(m)

case class GetHaku(oid: String)

case class Kieliversiot(fi: Option[String], sv: Option[String], en: Option[String])

case class Haku(nimi: Kieliversiot, oid: String, aika: Ajanjakso, kausi: String, vuosi: Int, koulutuksenAlkamiskausi: Option[String], koulutuksenAlkamisvuosi: Option[Int], kkHaku: Boolean)

object Haku {
  def apply(haku: RestHaku)(loppu: ReadableInstant): Haku = {
    val ajanjakso = Ajanjakso(findStart(haku), loppu)
    Haku(
      Kieliversiot(haku.nimi.get("kieli_fi").flatMap(Option(_)).flatMap(_.blankOption), haku.nimi.get("kieli_sv").flatMap(Option(_)).flatMap(_.blankOption), haku.nimi.get("kieli_en").flatMap(Option(_)).flatMap(_.blankOption)), haku.oid.get,
      ajanjakso,
      haku.hakukausiUri,
      haku.hakukausiVuosi,
      haku.koulutuksenAlkamiskausiUri,
      haku.koulutuksenAlkamisVuosi,
      kkHaku = haku.kohdejoukkoUri.exists(_.startsWith("haunkohdejoukko_12"))
    )
  }

  def findStart(haku: RestHaku): DateTime = {
    new DateTime(haku.hakuaikas.map(_.alkuPvm).sorted.head)
  }
}

class FutureList[A](futures: Seq[Future[A]]) {
  def waitForAll(implicit ec: ExecutionContext): Future[Seq[A]] = Future.sequence(futures)
}

object FutureList {
  implicit def futures2FutureList[A](futures: Seq[Future[A]]): FutureList[A] = new FutureList(futures)
}
