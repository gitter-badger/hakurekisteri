package fi.vm.sade.hakurekisteri.integration.virta

import akka.actor.{Actor, Props, ActorSystem}
import akka.pattern.ask
import akka.testkit.TestActorRef
import akka.util.Timeout
import fi.vm.sade.hakurekisteri.dates.{InFuture, Ajanjakso}
import fi.vm.sade.hakurekisteri.integration.haku.{Kieliversiot, Haku, GetHaku}
import fi.vm.sade.hakurekisteri.test.tools.FutureWaiting
import org.joda.time.DateTime
import org.scalatest.{WordSpec, Matchers}

import scala.concurrent.Await

class VirtaQueueSpec extends WordSpec with Matchers with FutureWaiting {
  implicit val system = ActorSystem("test-virta-queue")

  "VirtaQueue" when {
    val virtaWaiter = new Waiter()
    val virtaHandler: PartialFunction[Any, Any] = {
      case q: VirtaQuery =>
        virtaWaiter { q.oppijanumero should be("foo") }
        virtaWaiter.dismiss()
        QueryProsessed(q)
    }
    val hakuHandler: PartialFunction[Any, Any] = {
      case q: GetHaku => Haku(Kieliversiot(Some("haku"), None, None), "1.2", Ajanjakso(new DateTime(), InFuture), "kausi_s#1", 2014, Some("kausi_k#1"), Some(2015), true)
    }

    val virtaActor = TestActorRef[MockActor](Props(new MockActor(virtaHandler)))
    val hakemusActor = TestActorRef[MockActor](Props(new MockActor()))
    val hakuActor = TestActorRef[MockActor](Props(new MockActor(hakuHandler)))

    "receiving query" should {
      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor, hakuActor)))
      val q = VirtaQuery("foo", Some("bar"))
      virtaQueue ! VirtaQueuedQuery(q)

      "put it in queue" in {
        virtaQueue.underlyingActor.virtaQueue should contain(q)
      }
    }

    "consuming all" should {
      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor, hakuActor)))
      virtaQueue ! VirtaQueuedQuery(VirtaQuery("foo", Some("bar")))
      virtaQueue ! VirtaQueuedQuery(VirtaQuery("foo", Some("bar2")))
      virtaQueue ! StartVirta

      "start consuming queries in the queue" in {
        import org.scalatest.time.SpanSugar._
        virtaWaiter.await(timeout(10.seconds), dismissals(2))
        virtaQueue.underlyingActor.virtaQueue.size should be(0)
      }
    }

    "receiving the same query multiple times" should {
      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor, hakuActor)))
      val q1 = VirtaQuery("foo", Some("bar"))
      val q2 = VirtaQuery("foo", Some("bar"))
      virtaQueue ! VirtaQueuedQuery(q1)
      virtaQueue ! VirtaQueuedQuery(q2)

      "put it in the queue only once" in {
        virtaQueue.underlyingActor.virtaQueue.size should be(1)
      }
    }

    "receiving 1 000 000 queries" should {
      import scala.concurrent.duration._
      implicit val timeout: Timeout = 30.seconds

      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor, hakuActor)))
      (0 until 1000000).foreach(i => virtaQueue ! VirtaQueuedQuery(VirtaQuery(s"foo$i", None)))

      val healthcheck = Await.result((virtaQueue ? VirtaHealth).mapTo[VirtaStatus], 30.seconds)
      "be fast enough to respond to healthcheck in 30 seconds" in {
        healthcheck.queueLength should be (1000000)
      }
    }
  }
}

class MockActor(handler: PartialFunction[Any, Any] = { case a: Any => a }) extends Actor {
  override def receive: Receive = {
    case a: Any =>
      val q = handler(a)
      sender ! q
  }
}
