package fi.vm.sade.hakurekisteri.integration.sijoittelu

import akka.actor.{Cancellable, Actor}
import scala.concurrent.Future
import scala.compat.Platform
import akka.event.Logging

class SijoitteluActor(cachedService: Sijoittelupalvelu, keepAlive: String*) extends Actor {
  import akka.pattern._

  import scala.concurrent.duration._

  implicit val ec = context.dispatcher
  var keepAlives: Seq[Cancellable] = Seq()
  val expiration = 4.hour
  val touchInterval = expiration / 2

  override def preStart(): Unit = {
    keepAlives = keepAlive.map((haku) => context.system.scheduler.schedule(1.seconds, touchInterval, self, SijoitteluQuery(haku)))
  }

  override def postStop(): Unit = {
    keepAlives.foreach(_.cancel())
  }

  val retry: FiniteDuration = 60.seconds
  val log = Logging(context.system, this)
  var cache = Map[String, Future[SijoitteluTulos]]()
  var cacheHistory = Map[String, Long]()
  private val refetch: FiniteDuration = 2.hours

  override def receive: Receive = {
    case SijoitteluQuery(haku) =>
      getSijoittelu(haku) pipeTo sender
    case Update(haku) if !inUse(haku) =>
      cache = cache - haku
    case Update(haku) =>
      val result = sijoitteluTulos(haku)
      result.onFailure{ case t =>
        log.error(t, s"failed to fetch sijoittelu for haku $haku")
        rescheduleHaku(haku, retry)}
      result map (Sijoittelu(haku, _)) pipeTo self
    case Sijoittelu(haku, st) =>
      cache = cache + (haku -> Future.successful(st))
      rescheduleHaku(haku)
  }

  def inUse(haku: String):Boolean = cacheHistory.getOrElse(haku,0L) > (Platform.currentTime - expiration.toMillis)

  def getSijoittelu(haku:String):  Future[SijoitteluTulos] = {
    cacheHistory = cacheHistory + (haku -> Platform.currentTime)
    cache.get(haku) match {
      case Some(s)  =>
        s
      case None =>
        updateCacheFor(haku)
    }
  }

  def updateCacheFor(haku: String): Future[SijoitteluTulos] = {
    val result: Future[SijoitteluTulos] = sijoitteluTulos(haku)
    cache = cache + (haku -> result)

    result.onFailure{ case t =>
      log.error(t, s"failed to fetch sijoittelu for haku $haku")
      rescheduleHaku(haku, retry)}
    result.onSuccess{
      case _ => rescheduleHaku(haku)
    }
    result
  }

  def sijoitteluTulos(haku: String): Future[SijoitteluTulos] = {
    cachedService.getSijoitteluTila(haku).map(
      _.results)
      .map(SijoitteluTulos(_))
  }

  def rescheduleHaku(haku: String, time: FiniteDuration = refetch) {
    log.debug(s"rescheduling haku $haku in $time")
    context.system.scheduler.scheduleOnce(time, self, Update(haku))
  }

  case class Update(haku:String)
  case class Sijoittelu(haku: String, st: SijoitteluTulos)
}
