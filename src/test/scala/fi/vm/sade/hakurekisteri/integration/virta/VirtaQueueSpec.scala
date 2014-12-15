package fi.vm.sade.hakurekisteri.integration.virta

import akka.actor.{Actor, Props, ActorSystem}
import akka.testkit.TestActorRef
import fi.vm.sade.hakurekisteri.test.tools.FutureWaiting
import org.scalatest.{WordSpec, Matchers}

class VirtaQueueSpec extends WordSpec with Matchers with FutureWaiting {
  implicit val system = ActorSystem("test-virta-queue")

  "VirtaQueue" when {
    val virtaWaiter = new Waiter()
    val virtaHandler: PartialFunction[Any, Any] = {
      case VirtaQuery(o, h) =>
        virtaWaiter { o should be("foo") }
        virtaWaiter.dismiss()
    }

    val virtaActor = TestActorRef[MockActor](Props(new MockActor(virtaHandler)))
    val hakemusActor = TestActorRef[MockActor](Props(new MockActor()))

    "receiving query" should {
      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor)))
      val q = VirtaQuery("foo", Some("bar"))
      virtaQueue ! VirtaQueuedQuery(q)

      "put it in queue" in {
        virtaQueue.underlyingActor.virtaQueue should contain(q)
      }
    }

    "consuming all" should {
      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor)))
      val q = VirtaQuery("foo", Some("bar"))
      virtaQueue ! VirtaQueuedQuery(q)
      virtaQueue ! StartVirta

      "start consuming queries in the queue" in {
        import org.scalatest.time.SpanSugar._
        virtaWaiter.await(timeout(10.seconds), dismissals(1))
      }
    }

    "receiving the same query multiple times" should {
      val virtaQueue: TestActorRef[VirtaQueue] = TestActorRef[VirtaQueue](Props(new VirtaQueue(virtaActor, hakemusActor)))
      val q1 = VirtaQuery("foo", Some("bar"))
      val q2 = VirtaQuery("foo", Some("bar"))
      virtaQueue ! VirtaQueuedQuery(q1)
      virtaQueue ! VirtaQueuedQuery(q2)

      "put it in the queue only once" in {
        virtaQueue.underlyingActor.virtaQueue.length should be(1)
      }
    }
  }
}

class MockActor(handler: PartialFunction[Any, Any] = { case a: Any => }) extends Actor {
  override def receive: Receive = {
    case a: Any =>
      handler(a)
      sender ! a
  }
}
