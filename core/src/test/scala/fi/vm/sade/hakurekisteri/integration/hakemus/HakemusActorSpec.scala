package fi.vm.sade.hakurekisteri.integration.hakemus

import java.util.UUID

import akka.actor.Actor.Receive
import akka.actor.Status.Success
import akka.actor._
import akka.testkit.TestActorRef
import com.ning.http.client.AsyncHttpClient
import fi.vm.sade.hakurekisteri.{suoritus, SpecsLikeMockito}
import fi.vm.sade.hakurekisteri.arvosana.{Arvio410, Arvosana, ArvosanaQuery, ArvosanaActor}
import fi.vm.sade.hakurekisteri.integration._
import fi.vm.sade.hakurekisteri.rest.support.Resource
import fi.vm.sade.hakurekisteri.storage.Identified
import fi.vm.sade.hakurekisteri.suoritus._
import fi.vm.sade.hakurekisteri.test.tools.FutureWaiting
import org.json4s.JsonAST.JObject
import org.mockito.Mockito._
import org.scalatest.{Matchers, FlatSpec}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.collection
import scala.collection.{immutable, Seq}
import scala.collection.immutable.List
import scala.concurrent.{Await, Future}
import akka.testkit.TestActorRef
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern.ask
import org.scalatest._
import matchers._

/**
 * @author Jussi Jartamo
 */
class HakemusActorSpec extends FlatSpec with Matchers with FutureWaiting with SpecsLikeMockito with DispatchSupport {

  implicit val formats = DefaultFormats
  import scala.language.implicitConversions
  implicit val system = ActorSystem("test-hakemus-system")
  import scala.concurrent.duration._
  implicit val timeout: Timeout = 5.second
  import CustomMatchers._

  it should "add arvosanat only once" in {
    // Tätä ei suoriteta synkronisesti koko testijoukon suorituksen yhteydessä
    /*
    val suoritusRekisteri = TestActorRef(new SuoritusActor)
    val arvosanaRekisteri = TestActorRef(new ArvosanaActor)

    IlmoitetutArvosanatTrigger.muodostaSuorituksetJaArvosanat(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("LK_MA","8")
        .build,
      suoritusRekisteri, arvosanaRekisteri)
    IlmoitetutArvosanatTrigger.muodostaSuorituksetJaArvosanat(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("LK_MA","9")
        .build,
      suoritusRekisteri, arvosanaRekisteri)
    val future = (arvosanaRekisteri ? ArvosanaQuery(None)).mapTo[Seq[Arvosana with Identified[UUID]]]
    val result = future.value.get

    result.isSuccess should be (true)
    result.get should containArvosanat (Seq(
      Arvosana(null,Arvio410("8"),"MA",None,false,None,"person1")
    ))

    val suoritusFut = (suoritusRekisteri ? SuoritusQuery(None))
    val suoritusRes = suoritusFut.value.get
    suoritusRes.isSuccess should be (true)
    */
  }

  it should "create suoritukset and arvosanat to rekisteri" in {
    // Tätä ei suoriteta synkronisesti koko testijoukon suorituksen yhteydessä
    /*
    val suoritusRekisteri = TestActorRef(new SuoritusActor)
    val arvosanaRekisteri = TestActorRef(new ArvosanaActor)

    IlmoitetutArvosanatTrigger.muodostaSuorituksetJaArvosanat(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("LK_MA","8")
        .putArvosana("PK_AI","7")
        .putArvosana("PK_AI_OPPIAINE","FI")
        .build,
      suoritusRekisteri, arvosanaRekisteri)

    val future = (arvosanaRekisteri ? ArvosanaQuery(None)).mapTo[Seq[Arvosana with Identified[UUID]]]
    val result = future.value.get

    result.isSuccess should be (true)
    result.get should containArvosanat (Seq(
      Arvosana(null,Arvio410("7"),"AI",Some("FI"),false,None,"person1"),
      Arvosana(null,Arvio410("8"),"MA",None,false,None,"person1")
      ))

    val suoritusFut = (suoritusRekisteri ? SuoritusQuery(None))
    val suoritusRes = suoritusFut.value.get
    suoritusRes.isSuccess should be (true)
    */
  }

  it should "split arvosanat correctly in RicherOsaaminen" in {
    RicherOsaaminen(Map("roskaa" -> "1")).groupByKomoAndGroupByAine should be (empty)


    RicherOsaaminen(Map(
      "LK_AI" -> "8",
      "LK_AI_OPPIAINE" -> "FI"
    )).groupByKomoAndGroupByAine should be (Map("LK" -> Map("AI" -> Map("" -> "8", "OPPIAINE" -> "FI"))))

    RicherOsaaminen(Map(
      "LK_AI_" -> "ROSKAA",
      "_" -> "ROSKAA",
      "LK_AI_OPPIAINE" -> "4"
    )).groupByKomoAndGroupByAine should be (empty)


    val r = RicherOsaaminen(Map(
      "LK_AI" -> "8",
      "LK_AI_OPPIAINE" -> "FI"
    )).groupByKomoAndGroupByAine


    r("LK")("AI")("") should be ("8")
    r("LK")("AI")("OPPIAINE") should be ("FI")

  }
  it should "include valinnaiset arvosanat" in {
    IlmoitetutArvosanatTrigger.createSuorituksetJaArvosanatFromOppimiset(
      Hakemus()
        .setPersonOid("person1")
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("PK_MA","8")
        .putArvosana("PK_MA_VAL1","6")
        .putArvosana("PK_MA_VAL2","5")
        .putArvosana("PK_MA_VAL3","7")
        .build
    ) should contain theSameElementsAs Seq(
      (ItseilmoitettuPeruskouluTutkinto("person1", 1988, "FI"),
        Seq(
          Arvosana(suoritus = null, arvio = Arvio410("6"), "MA", lisatieto = None, valinnainen = true, myonnetty = None, source = "person1"),
          Arvosana(suoritus = null, arvio = Arvio410("5"), "MA", lisatieto = None, valinnainen = true, myonnetty = None, source = "person1"),
          Arvosana(suoritus = null, arvio = Arvio410("7"), "MA", lisatieto = None, valinnainen = true, myonnetty = None, source = "person1"),
          Arvosana(suoritus = null, arvio = Arvio410("8"), "MA", lisatieto = None, valinnainen = false, myonnetty = None, source = "person1"))
        ))
  }
  it should "create suorituksia from koulutustausta" in {
    IlmoitetutArvosanatTrigger.createSuorituksetKoulutustausta(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("LK_MA","8")
        .build
    ) should contain theSameElementsAs Seq(ItseilmoitettuLukioTutkinto("person1", 2000, "FI"), ItseilmoitettuPeruskouluTutkinto("person1", 1988, "FI"))
  }
  it should "handle 'ei arvosanaa' and 'S' arvosanat" in {
    IlmoitetutArvosanatTrigger.createSuorituksetJaArvosanatFromOppimiset(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("LK_MA","Ei arvosanaa")
        .putArvosana("PK_AI","S")
        .build
    ) should contain theSameElementsAs Seq(
      (ItseilmoitettuLukioTutkinto("person1", 2000, "FI"),
        Seq()),
      (ItseilmoitettuPeruskouluTutkinto("person1", 1988, "FI"),
        Seq()
        ))
  }
  it should "create suorituksia ja arvosanoja from oppimiset" in {
    IlmoitetutArvosanatTrigger.createSuorituksetJaArvosanatFromOppimiset(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .setPerusopetuksenPaattotodistusvuosi(1988)
        .putArvosana("LK_MA","8")
        .putArvosana("PK_AI","7")
        .build
    ) should contain theSameElementsAs Seq(
      (ItseilmoitettuLukioTutkinto("person1", 2000, "FI"),
        Seq(Arvosana(suoritus = null, arvio = Arvio410("8"), "MA", lisatieto = None, valinnainen = false, myonnetty = None, source = "person1"))),
      (ItseilmoitettuPeruskouluTutkinto("person1", 1988, "FI"),
        Seq(Arvosana(suoritus = null, arvio = Arvio410("7"), "AI", lisatieto = None, valinnainen = false, myonnetty = None, source = "person1"))
        ))
  }

  //
  it should "handle arvosana special cases" in {
    IlmoitetutArvosanatTrigger.createSuorituksetJaArvosanatFromOppimiset(
      Hakemus()
        .setPersonOid("person1")
        .setLukionPaattotodistusvuosi(2000)
        .putArvosana("LK_AI", "8")
        .putArvosana("LK_AI_OPPIAINE", "FI")
        .putArvosana("LK_B1", "7")
        .putArvosana("LK_B1_OPPIAINE", "SV")
        .putArvosana("LK_", "ROSKAA")
        .putArvosana("LK_SA_SDF", "ROSKAA")
        .putArvosana("LK_SA_SDF_ASDF_ASDF_ASDF_ASDF", "ROSKAA")
        .build
    ) should contain theSameElementsAs Seq(
      (ItseilmoitettuLukioTutkinto("person1", 2000, "FI"),
        Seq(
          Arvosana(suoritus = null, arvio = Arvio410("7"), "B1", lisatieto = Some("SV"), valinnainen = false, myonnetty = None, source = "person1"),
          Arvosana(suoritus = null, arvio = Arvio410("8"), "AI", lisatieto = Some("FI"), valinnainen = false, myonnetty = None, source = "person1")
        )))
  }


  trait CustomMatchers {

    class ArvosanatMatcher(expectedArvosanat: Seq[Arvosana]) extends Matcher[Seq[Arvosana]] {

      def apply(left: Seq[Arvosana]) = {
        MatchResult(
          if(left.isEmpty) false else left.map(l => expectedArvosanat.exists(p => l.arvio.equals(p.arvio) && l.lisatieto.equals(p.lisatieto) )).reduceLeft(_ && _),
          s"""Arvosanat\n ${left.toList}\nwas not expected\n $expectedArvosanat""",
          s"""Arvosanat\n ${stripId(left).toList}\nwas expected\n $expectedArvosanat"""
        )
      }

      private def stripId(l: Seq[Arvosana]) = {
        l.map(l0 => {
          l0.copy(suoritus=null)
        })
      }
    }

    def containArvosanat(expectedExtension: Seq[Arvosana]) = new ArvosanatMatcher(expectedExtension)
  }
  object CustomMatchers extends CustomMatchers

}



class TestActor(handler: PartialFunction[Any, Unit]) extends Actor {

  override def receive: Receive = handler
}

object Triggered

object Hakemus {
  def apply(): HakemusBuilder = HakemusBuilder(Map.empty, None, None, None)
}

case class HakemusBuilder(osaaminen: Map[String, String], personOid: Option[String], PK_PAATTOTODISTUSVUOSI: Option[String], lukioPaattotodistusVuosi: Option[String]) {

  def setPersonOid(pOid: String): HakemusBuilder =
    HakemusBuilder(osaaminen, Some(pOid), PK_PAATTOTODISTUSVUOSI, lukioPaattotodistusVuosi)

  def setPerusopetuksenPaattotodistusvuosi(paattotodistusvuosi: Int): HakemusBuilder =
    HakemusBuilder(osaaminen, personOid, Some(paattotodistusvuosi.toString), lukioPaattotodistusVuosi)

  def setLukionPaattotodistusvuosi(paattotodistusvuosi: Int): HakemusBuilder =
    HakemusBuilder(osaaminen, personOid, PK_PAATTOTODISTUSVUOSI, Some(paattotodistusvuosi.toString))

  def putArvosana(aine: String, arvosana: String): HakemusBuilder = HakemusBuilder(osaaminen + (aine -> arvosana), personOid, PK_PAATTOTODISTUSVUOSI, lukioPaattotodistusVuosi)

  def build: FullHakemus = FullHakemus("oid1", personOid, "hakuoid1", Some(HakemusAnswers(
    Some(HakemusHenkilotiedot(Henkilotunnus = Some("110388-9241"),
      aidinkieli = None,
      lahiosoite = None,
      Postinumero = None,
      osoiteUlkomaa = None,
      postinumeroUlkomaa = None,
      kaupunkiUlkomaa = None,
      asuinmaa = None,
      matkapuhelinnumero1 = None,
      matkapuhelinnumero2 = None,
      Sähköposti = None,
      kotikunta = None,
      Sukunimi = None,
      Etunimet = None,
      Kutsumanimi = None,
      kansalaisuus = None,
      onkoSinullaSuomalainenHetu = None,
      sukupuoli = None,
      syntymaaika = None,
      koulusivistyskieli = None))
    , Some(Koulutustausta(
      lahtokoulu = None,
      POHJAKOULUTUS = None,
      lukioPaattotodistusVuosi,
      PK_PAATTOTODISTUSVUOSI,
      LISAKOULUTUS_KYMPPI = None,
      LISAKOULUTUS_VAMMAISTEN = None,
      LISAKOULUTUS_TALOUS = None,
      LISAKOULUTUS_AMMATTISTARTTI = None,
      LISAKOULUTUS_KANSANOPISTO = None,
      LISAKOULUTUS_MAAHANMUUTTO = None,
      luokkataso = None,
      lahtoluokka = None,
      perusopetuksen_kieli = None,
      pohjakoulutus_yo = None,
      pohjakoulutus_yo_vuosi = None,
      pohjakoulutus_am = None,
      pohjakoulutus_am_vuosi = None,
      pohjakoulutus_amt = None,
      pohjakoulutus_amt_vuosi = None,
      pohjakoulutus_kk = None,
      pohjakoulutus_kk_pvm = None,
      pohjakoulutus_ulk = None,
      pohjakoulutus_ulk_vuosi = None,
      pohjakoulutus_avoin = None,
      pohjakoulutus_muu = None,
      pohjakoulutus_muu_vuosi = None,
      aiempitutkinto_tutkinto = None,
      aiempitutkinto_korkeakoulu = None,
      aiempitutkinto_vuosi = None
    )), None, None, Some(osaaminen))), Some("ACTIVE"), Nil)

}