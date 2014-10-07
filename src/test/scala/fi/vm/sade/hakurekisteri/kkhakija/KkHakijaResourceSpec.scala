package fi.vm.sade.hakurekisteri.kkhakija

import akka.actor.{Actor, Props}
import fi.vm.sade.hakurekisteri.acceptance.tools.{TestSecurity, HakeneetSupport}
import fi.vm.sade.hakurekisteri.dates.InFuture
import fi.vm.sade.hakurekisteri.integration.hakemus.HakemusQuery
import fi.vm.sade.hakurekisteri.integration.haku.{Haku, GetHaku}
import fi.vm.sade.hakurekisteri.integration.koodisto._
import fi.vm.sade.hakurekisteri.integration.tarjonta._
import fi.vm.sade.hakurekisteri.integration.valintatulos.{ValintaTulos, ValintaTulosQuery}
import fi.vm.sade.hakurekisteri.integration.ytl.YTLXml
import fi.vm.sade.hakurekisteri.rest.support.HakurekisteriSwagger
import fi.vm.sade.hakurekisteri.suoritus.{VirallinenSuoritus, SuoritusQuery}
import org.joda.time.LocalDate
import org.scalatra.swagger.Swagger
import org.scalatra.test.scalatest.ScalatraFunSuite

class KkHakijaResourceSpec extends ScalatraFunSuite with HakeneetSupport {
  implicit val swagger: Swagger = new HakurekisteriSwagger

  val hakemusMock = system.actorOf(Props(new MockedHakemusActor()))
  val tarjontaMock = system.actorOf(Props(new MockedTarjontaActor()))
  val hakuMock = system.actorOf(Props(new MockedHakuActor()))
  val suoritusMock = system.actorOf(Props(new MockedSuoritusActor()))
  val valintaTulosMock = system.actorOf(Props(new MockedValintaTulosActor()))
  val koodistoMock = system.actorOf(Props(new MockedKoodistoActor()))

  addServlet(new KkHakijaResource(hakemusMock, tarjontaMock, hakuMock, koodistoMock, suoritusMock, valintaTulosMock) with TestSecurity, "/")

  test("should return 200 OK") {
    get("/") {
      println(s"body: $body")
      status should be (200)
    }
  }

  import fi.vm.sade.hakurekisteri.suoritus.yksilollistaminen._

  val haku1 = RestHaku(Some("1.2"), List(RestHakuAika(1L)), Map("fi" -> "testihaku"), "kausi_s#1", 2014, Some("kohdejoukko_12#1"))
  val koulutus1 = Hakukohteenkoulutus("1.5.6", "123456", Some("AABB5tga"))
  val suoritus1 = VirallinenSuoritus(YTLXml.yotutkinto, YTLXml.YTL, "VALMIS", new LocalDate(), "1.2.3", Ei, "FI", None, true, "1")

  class MockedHakemusActor extends Actor {
    override def receive: Receive = {
      case q: HakemusQuery => println(q); sender ! Seq(FullHakemus1, FullHakemus2)
    }
  }

  class MockedTarjontaActor extends Actor {
    override def receive: Actor.Receive = {
      case oid: HakukohdeOid => println(oid); sender ! HakukohteenKoulutukset(oid.oid, Some("joku tunniste"), Seq(koulutus1))
    }
  }

  class MockedHakuActor extends Actor {
    override def receive: Actor.Receive = {
      case q: GetHaku => println(q); sender ! Haku(haku1)(InFuture)
    }
  }

  class MockedSuoritusActor extends Actor {
    override def receive: Actor.Receive = {
      case q: SuoritusQuery => println(q); sender ! Seq(suoritus1)
    }
  }

  class MockedValintaTulosActor extends Actor {
    override def receive: Actor.Receive = {
      case q: ValintaTulosQuery => println(q); sender ! ValintaTulos(q.hakemusOid, Seq())
    }
  }

  class MockedKoodistoActor extends Actor {
    override def receive: Actor.Receive = {
      case q: GetRinnasteinenKoodiArvoQuery => println(q); sender ! "246"
      case q: GetKoodi => println(q); sender ! Some(Koodi(q.koodiUri.split("_").last.split("#").head, q.koodiUri, Koodisto(q.koodistoUri), Seq(KoodiMetadata(q.koodiUri, "FI"))))
    }
  }
}
