package fi.vm.sade.hakurekisteri.integration.organisaatio

import akka.actor.{Actor, Cancellable}
import akka.event.Logging
import akka.pattern.pipe

import scala.compat.Platform
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class OrganisaatioActor(palvelu: Organisaatiopalvelu) extends Actor {
  implicit val executionContext: ExecutionContext = context.dispatcher
  private var cache:Map[String,(Long, Future[Option[Organisaatio]])] = Map()
  val log = Logging(context.system, this)

  class Refresh
  private object refresh  extends Refresh

  case class Refetch(oid:String)

  var cancellable: Option[Cancellable] = None

  override def preStart(): Unit = {
    palvelu.getAll.onSuccess {
      case s:Seq[String] =>
        fetchOrgs(s)
    }
    cancellable = Some(context.system.scheduler.schedule(10.minutes,
      10.minutes,
      self,
      refresh)(context.dispatcher, self))
  }

  def fetchOrgs(s:Seq[String], mf: String => AnyRef = (s) => s) = s.grouped(10).zipWithIndex.foreach((t: (Seq[String], Int)) => {
    t._1 foreach(oid => {context.system.scheduler.scheduleOnce(t._2.second, self, mf(oid))})
  })

  override def postStop(): Unit = {
    cancellable.foreach(_.cancel())
  }

  val timeToLive = 24.hours

  case class Save(oid:String, value: (Long, Future[Option[Organisaatio]]))

  override def receive: Receive = {
    case oid:String => find(oid)._2 pipeTo sender
    case Refetch(oid) => val result = newValue(oid)
                         result._2.onSuccess {case _ => self ! Save(oid, result)}
                         result._2.onFailure {case _ => log.warning("fetching organisation data for %s failed. Trying again".format(oid))
                                                        self ! Refetch(oid)}
    case Save(oid,result) => cache = cache + (oid -> result)
    case refresh:Refresh => Future(fetchOrgs(cache.toSeq.filter(t => t._2._1 < Platform.currentTime).map(_._1), Refetch))
  }

  def find(oid: String): (Long, Future[Option[Organisaatio]]) = {
    Try(cache(oid)).recoverWith{ case e: NoSuchElementException => Try({val result = newValue(oid); cache = cache + (oid -> result); result})}.get
  }

  def newValue(oid: String): (Long, Future[Option[Organisaatio]]) = {
    (Platform.currentTime + timeToLive.toMillis, palvelu.get(oid))
  }
}