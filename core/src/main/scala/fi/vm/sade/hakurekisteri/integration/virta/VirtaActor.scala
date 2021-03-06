package fi.vm.sade.hakurekisteri.integration.virta

import java.util.concurrent.TimeUnit

import akka.actor.Status.Failure
import akka.actor.{Cancellable, ActorLogging, Actor, ActorRef}
import akka.pattern.ask
import fi.vm.sade.hakurekisteri.{Oids, Config}
import fi.vm.sade.hakurekisteri.integration.hakemus.Trigger
import fi.vm.sade.hakurekisteri.integration.haku.{HakuNotFoundException, Haku, GetHaku}
import fi.vm.sade.hakurekisteri.integration.organisaatio.Organisaatio
import fi.vm.sade.hakurekisteri.opiskeluoikeus.Opiskeluoikeus
import fi.vm.sade.hakurekisteri.suoritus.{Suoritus, VirallinenSuoritus, yksilollistaminen}
import org.joda.time.{LocalTime, DateTime, LocalDateTime, LocalDate}

import scala.collection.mutable
import scala.compat.Platform
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import fi.vm.sade.hakurekisteri.healthcheck.Status
import fi.vm.sade.hakurekisteri.healthcheck.Status.Status

import Virta.at

import scala.util.Success


case class VirtaQuery(oppijanumero: String, hetu: Option[String])
case class VirtaQueuedQuery(q: VirtaQuery)
case class KomoNotFoundException(message: String) extends Exception(message)
case class VirtaData(opiskeluOikeudet: Seq[Opiskeluoikeus], suoritukset: Seq[Suoritus])
case class VirtaStatus(lastProcessDone: Option[DateTime] = None,
                       nextProcessStart: Option[DateTime] = None,
                       queueLength: Long,
                       status: Status)
case class RescheduleProcessing(time: String)
case class QueryProsessed(q: VirtaQuery)

object StartVirta
object PrintStats
object VirtaHealth
object CancelSchedule

class VirtaQueue(virtaActor: ActorRef, hakemusActor: ActorRef, hakuActor: ActorRef) extends Actor with ActorLogging {
  implicit val executionContext: ExecutionContext = context.dispatcher

  val virtaQueue: mutable.Set[VirtaQuery] = mutable.LinkedHashSet()
  private var lastProcessDone: Option[DateTime] = None
  private var scheduledProcessTime: Option[LocalTime] = None
  private var startedProcessing: Boolean = false
  private var processing: Cancellable = scheduleProcessing("04:00")
  private val statPrinter: Cancellable = context.system.scheduler.schedule(5.minutes, 10.minutes, self, PrintStats)

  def nextProcessTime: Option[DateTime] = scheduledProcessTime.map(t => {
    val atToday: DateTime = t.toDateTimeToday
    if (atToday.isBefore(new DateTime())) {
      atToday.plusDays(1)
    } else atToday
  })

  def scheduleProcessing(time: String): Cancellable = {
    val duration: FiniteDuration = at(time)
    scheduledProcessTime = Some(new LocalTime(Platform.currentTime + duration.toMillis))
    context.system.scheduler.schedule(duration, 24.hours, self, StartVirta)
  }

  override def postStop(): Unit = {
    processing.cancel()
    statPrinter.cancel()
  }
  
  def receive: Receive = {
    case VirtaQueuedQuery(q) if !virtaQueue.contains(q) =>
      virtaQueue.add(q)

    case StartVirta if !startedProcessing =>
      log.info("started to process virta queries")
      if (virtaQueue.nonEmpty) {
        startedProcessing = true
        virtaActor ! virtaQueue.head
      } else log.info("no queries to process")

    case QueryProsessed(q) =>
      virtaQueue.remove(q)
      if (virtaQueue.nonEmpty) {
        virtaActor ! virtaQueue.head
      } else {
        log.info(s"all virta queries processed, queue length ${virtaQueue.size}")
        lastProcessDone = Some(new DateTime())
        startedProcessing = false
      }

    case PrintStats => log.info(s"queue length ${virtaQueue.size}")

    case VirtaHealth => sender ! VirtaStatus(lastProcessDone, nextProcessTime, virtaQueue.size, Status.OK)

    case CancelSchedule =>
      processing.cancel()
      virtaQueue.clear()
      scheduledProcessTime = None
      log.info(s"cancelled scheduled processing")

    case RescheduleProcessing(time) =>
      processing = scheduleProcessing(time)
      log.info(s"rescheduled to $scheduledProcessTime")
  }

  override def preStart(): Unit = {
    hakemusActor ! Trigger((oid, hetu, hakuOid) =>
      if (!isYsiHetu(hetu))
        (hakuActor ? GetHaku(hakuOid))(1.hour).mapTo[Haku].map(haku => haku.kkHaku).recoverWith {
          case t: HakuNotFoundException => Future.successful(true)
        }.map(isKkHaku => if (isKkHaku) self ! VirtaQueuedQuery(VirtaQuery(oid, Some(hetu))))
    )
    super.preStart()
  }

  val ysiHetu = "\\d{6}[+-AB]9\\d{2}[0123456789ABCDEFHJKLMNPRSTUVWXY]"
  def isYsiHetu(hetu: String): Boolean = hetu.matches(ysiHetu)
}


class VirtaActor(virtaClient: VirtaClient, organisaatioActor: ActorRef, suoritusActor: ActorRef, opiskeluoikeusActor: ActorRef) extends Actor with ActorLogging {
  implicit val executionContext: ExecutionContext = context.dispatcher

  import akka.pattern.pipe

  def receive: Receive = {
    case q: VirtaQuery =>
      val from = sender()
      val tiedot = getOpiskelijanTiedot(q.oppijanumero, q.hetu)
      tiedot.onComplete(t => from ! QueryProsessed(q))
      tiedot.pipeTo(self)(ActorRef.noSender)
      
    case Some(r: VirtaResult) => 
      save(r)
      
    case Failure(t: VirtaValidationError) => 
      log.warning(s"virta validation error: $t")
      
    case Failure(t: Throwable) => 
      log.error(t, "error occurred in virta query")
  }

  def getOpiskelijanTiedot(oppijanumero: String, hetu: Option[String]): Future[Option[VirtaResult]] =
    virtaClient.getOpiskelijanTiedot(oppijanumero = oppijanumero, hetu = hetu)

  def getKoulutusUri(koulutuskoodi: Option[String]): String =
    s"koulutus_${resolveKoulutusKoodiOrUnknown(koulutuskoodi)}"

  def resolveKoulutusKoodiOrUnknown(koulutuskoodi: Option[String]): String = {
    val tuntematon = "999999"
    koulutuskoodi.getOrElse(tuntematon)
  }

  def opiskeluoikeus(oppijanumero: String)(o: VirtaOpiskeluoikeus): Future[Opiskeluoikeus] =
    for (
      oppilaitosOid <- resolveOppilaitosOid(o.myontaja)
    ) yield Opiskeluoikeus(
          alkuPaiva = o.alkuPvm,
          loppuPaiva = o.loppuPvm,
          henkiloOid = oppijanumero,
          komo = getKoulutusUri(o.koulutuskoodit.headOption),
          myontaja = oppilaitosOid,
          source = Virta.CSC)

  def tutkinto(oppijanumero: String)(t: VirtaTutkinto): Future[Suoritus] =
    for (
      oppilaitosOid <- resolveOppilaitosOid(t.myontaja)
    ) yield VirallinenSuoritus(
          komo = getKoulutusUri(t.koulutuskoodi),
          myontaja = oppilaitosOid,
          valmistuminen = t.suoritusPvm,
          tila = tila(t.suoritusPvm),
          henkilo = oppijanumero,
          yksilollistaminen = yksilollistaminen.Ei,
          suoritusKieli = t.kieli,
          vahv = true,
          lahde = Virta.CSC)

  def tila(valmistuminen: LocalDate): String = valmistuminen match {
    case v: LocalDate if v.isBefore(new LocalDate()) => "VALMIS"
    case _ => "KESKEN"
  }

  def save(r: VirtaResult): Unit = {
    val opiskeluoikeudet: Future[Seq[Opiskeluoikeus]] = Future.sequence(r.opiskeluoikeudet.map(opiskeluoikeus(r.oppijanumero)))
    val suoritukset: Future[Seq[Suoritus]] = Future.sequence(r.tutkinnot.map(tutkinto(r.oppijanumero)))
    for {
      o <- opiskeluoikeudet
      s <- suoritukset
    } yield {
      o.foreach(opiskeluoikeusActor ! _)
      s.foreach(suoritusActor ! _)
    }
  }

  import akka.pattern.ask

  def resolveOppilaitosOid(oppilaitosnumero: String): Future[String] = oppilaitosnumero match {
    case o if Seq("XX", "UK", "UM").contains(o) => Future.successful(Oids.tuntematonOrganisaatioOid)
    case o =>
      (organisaatioActor ? o)(1.hour).mapTo[Option[Organisaatio]] map {
          case Some(org) => org.oid
          case _ => log.error(s"oppilaitos not found with oppilaitosnumero $o"); throw OppilaitosNotFoundException(s"oppilaitos not found with oppilaitosnumero $o")
      }
  }
}

object Virta {
  val CSC = Oids.cscOrganisaatioOid

  val timeFormat = "^[0-9]{2}:[0-9]{2}$"

  def at(time: String): FiniteDuration = {
    if (!time.matches(timeFormat)) throw new IllegalArgumentException("time format is not HH:mm")

    val t = time.split(":")
    val todayAtTime = new LocalDateTime().withTime(t(0).toInt, t(1).toInt, 0, 0)
    todayAtTime match {
      case d if d.isBefore(new LocalDateTime()) => new FiniteDuration(d.plusDays(1).toDate.getTime - Platform.currentTime, TimeUnit.MILLISECONDS)
      case d => new FiniteDuration(d.toDate.getTime - Platform.currentTime, TimeUnit.MILLISECONDS)
    }
  }
}