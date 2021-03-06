package fi.vm.sade.hakurekisteri

import java.io.InputStream
import java.nio.file.{Path, Files, Paths}

import fi.vm.sade.hakurekisteri.integration.ServiceConfig
import fi.vm.sade.hakurekisteri.integration.hakemus.HakemusConfig
import fi.vm.sade.hakurekisteri.integration.virta.VirtaConfig
import fi.vm.sade.hakurekisteri.integration.ytl.YTLConfig
import org.joda.time.LocalTime
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.util.{Success, Try}
import fi.vm.sade.hakurekisteri.tools.RicherString

object Config {
  def fromString(profile: String) = profile match {
    case "it" => mockConfig
    case "default" => new DefaultConfig
  }
  lazy val globalConfig = fromString(sys.props.getOrElse("hakurekisteri.profile", "default"))
  lazy val mockConfig = new MockConfig
}

object Oids {
  val ophOrganisaatioOid = "1.2.246.562.10.00000000001"
  val ytlOrganisaatioOid = "1.2.246.562.10.43628088406"
  val cscOrganisaatioOid = "1.2.246.562.10.2013112012294919827487"
  val tuntematonOrganisaatioOid = "1.2.246.562.10.57118763579"

  val yotutkintoKomoOid = "1.2.246.562.5.2013061010184237348007"
  val perusopetusKomoOid = "1.2.246.562.13.62959769647"
  val lisaopetusKomoOid = "1.2.246.562.5.2013112814572435044876"
  val lisaopetusTalousKomoOid = "1.2.246.562.5.2013061010184614853416"
  val ammattistarttiKomoOid = "1.2.246.562.5.2013112814572438136372"
  val valmentavaKomoOid = "1.2.246.562.5.2013112814572435755085"
  val ammatilliseenvalmistavaKomoOid = "1.2.246.562.5.2013112814572441001730"
  val ulkomainenkorvaavaKomoOid = "1.2.246.562.13.86722481404"
  val lukioKomoOid = "TODO lukio komo oid"
  val ammatillinenKomoOid = "TODO ammatillinen komo oid"
  val lukioonvalmistavaKomoOid = "1.2.246.562.5.2013112814572429142840"
}

class DefaultConfig extends Config {
  def mockMode = false
  val h2DatabaseUrl= "jdbc:h2:file:data/development"
  private lazy val homeDir = sys.props.getOrElse("user.home", "")
  lazy val ophConfDir: Path = Paths.get(homeDir, "/oph-configuration/")
}

class MockConfig extends Config {
  def mockMode = true
  val h2DatabaseUrl = "jdbc:h2:file:data/integration-test"
  override val importBatchProcessingInitialDelay = 1.seconds
  lazy val ophConfDir = Paths.get(ProjectRootFinder.findProjectRoot().getAbsolutePath, "web/src/test/resources/oph-configuration")
}

abstract class Config {
  def mockMode: Boolean

  val h2DatabaseUrl: String

  val log = LoggerFactory.getLogger(getClass)
  def ophConfDir: Path

  val propertyLocations = Seq("suoritusrekisteri.properties", "common.properties")

  val jndiName = "java:comp/env/jdbc/suoritusrekisteri"

  val importBatchProcessingInitialDelay = 20.minutes

  // by default the service urls point to QA
  val hostQa = "testi.virkailija.opintopolku.fi"


  lazy val resources = propertyLocations.map(ophConfDir.resolve(_))

  log.info(s"lazy loading properties from paths $resources")

  lazy val properties: Map[String, String] = {
    val propertyFiles = resources.map(f => {
      val t = Try(Files.newInputStream(f))
      if (t.isFailure) log.error("could not load property file", t.failed.get)
      t
    }).collect {
      case Success(is) => is
    }
    loadProperties(propertyFiles)
  }

  val integrations = new IntegrationConfig(hostQa, properties)

  val ensikertalainenCacheHours = properties.getOrElse("suoritusrekisteri.cache.hours.ensikertalainen", "6").toInt

  val tiedonsiirtoStorageDir = properties.getOrElse("suoritusrekisteri.tiedonsiirto.storage.dir", System.getProperty("java.io.tmpdir"))

  def loadProperties(resources: Seq[InputStream]): Map[String, String] = {
    import scala.collection.JavaConversions._
    val rawMap = resources.map((reader) => {val prop = new java.util.Properties; prop.load(reader); Map(prop.toList: _*)}).foldLeft(Map[String, String]())(_ ++ _)

    resolve(rawMap)
  }

  def resolve(source: Map[String, String]): Map[String, String] = {
    val converted = source.mapValues(_.replace("${","€{"))
    val unResolved = Set(converted.map((s) => (for (found <- "€\\{(.*?)\\}".r findAllMatchIn s._2) yield found.group(1)).toList).foldLeft(List[String]())(_ ++ _):_*)
    val unResolvable = unResolved.filter((s) => converted.get(s).isEmpty)
    if ((unResolved -- unResolvable).isEmpty)
      converted.mapValues(_.replace("€{","${"))
    else
      resolve(converted.mapValues((s) => "€\\{(.*?)\\}".r replaceAllIn (s, m => {converted.getOrElse(m.group(1), "€{" + m.group(1) + "}") })))
  }
}

class IntegrationConfig(hostQa: String, properties: Map[String, String]) {
  val casUrlQa = s"https://$hostQa/cas"
  val organisaatioServiceUrlQa = s"https://$hostQa/organisaatio-service"
  val hakuappServiceUrlQa = s"https://$hostQa/haku-app"
  val koodistoServiceUrlQa = s"https://$hostQa/koodisto-service"
  val parameterServiceUrlQa = s"https://$hostQa/ohjausparametrit-service"
  val valintaTulosServiceUrlQa = s"https://$hostQa/valinta-tulos-service"

  val sijoitteluServiceUrlQa = s"https://$hostQa/sijoittelu-service"
  val tarjontaServiceUrlQa = s"https://$hostQa/tarjonta-service"
  val henkiloServiceUrlQa = s"https://$hostQa/authentication-service"
  val virtaServiceUrlTest = "http://virtawstesti.csc.fi/luku/OpiskelijanTiedot"
  val virtaJarjestelmaTest = ""
  val virtaTunnusTest = ""
  val virtaAvainTest = "salaisuus"

  val casUrl = Some(properties.getOrElse("web.url.cas", casUrlQa))
  val sijoitteluServiceUrl = properties.getOrElse("cas.service.sijoittelu-service", sijoitteluServiceUrlQa)
  val tarjontaServiceUrl = properties.getOrElse("cas.service.tarjonta-service", tarjontaServiceUrlQa)
  val henkiloServiceUrl = properties.getOrElse("cas.service.authentication-service", henkiloServiceUrlQa)
  val hakuappServiceUrl = properties.getOrElse("cas.service.haku-service", hakuappServiceUrlQa)
  val koodistoServiceUrl = properties.getOrElse("cas.service.koodisto-service", koodistoServiceUrlQa)
  val parameterServiceUrl = properties.getOrElse("cas.service.ohjausparametrit-service", parameterServiceUrlQa)
  val organisaatioServiceUrl = properties.getOrElse("cas.service.organisaatio-service", organisaatioServiceUrlQa)
  val valintaTulosServiceUrl = properties.getOrElse("cas.service.valintatulos-service", valintaTulosServiceUrlQa)
  val organisaatioSoapServiceUrl = properties.getOrElse("cas.service.organisaatio-service", organisaatioServiceUrlQa) + "/services/organisaatioService"
  val maxApplications = properties.getOrElse("suoritusrekisteri.hakijat.max.applications", "2000").toInt
  val virtaServiceUrl = properties.getOrElse("suoritusrekisteri.virta.service.url", virtaServiceUrlTest)
  val virtaJarjestelma = properties.getOrElse("suoritusrekisteri.virta.jarjestelma", virtaJarjestelmaTest)
  val virtaTunnus = properties.getOrElse("suoritusrekisteri.virta.tunnus", virtaTunnusTest)
  val virtaAvain = properties.getOrElse("suoritusrekisteri.virta.avain", virtaAvainTest)

  val serviceUser = properties.get("suoritusrekisteri.app.username")
  val servicePassword = properties.get("suoritusrekisteri.app.password")

  val virtaConfig = VirtaConfig(virtaServiceUrl, virtaJarjestelma, virtaTunnus, virtaAvain, properties)
  val henkiloConfig = ServiceConfig(casUrl, henkiloServiceUrl, serviceUser, servicePassword, properties)
  val sijoitteluConfig = ServiceConfig(casUrl, sijoitteluServiceUrl, serviceUser, servicePassword, properties)
  val parameterConfig = ServiceConfig(serviceUrl = parameterServiceUrl, properties = properties)
  val hakemusConfig = HakemusConfig(ServiceConfig(casUrl, hakuappServiceUrl, serviceUser, servicePassword, properties), maxApplications)
  val tarjontaConfig = ServiceConfig(serviceUrl = tarjontaServiceUrl, properties = properties)
  val koodistoConfig = ServiceConfig(serviceUrl = koodistoServiceUrl, properties = properties)
  val organisaatioConfig = ServiceConfig(serviceUrl = organisaatioServiceUrl, properties = properties)
  val valintaTulosConfig = ServiceConfig(serviceUrl = valintaTulosServiceUrl, properties = properties)

  val koodistoCacheHours = properties.getOrElse("suoritusrekisteri.cache.hours.koodisto", "12").toInt
  val organisaatioCacheHours = properties.getOrElse("suoritusrekisteri.cache.hours.organisaatio", "12").toInt
  val tarjontaCacheHours = properties.getOrElse("suoritusrekisteri.cache.hours.tarjonta", "12").toInt
  val valintatulosCacheHours = properties.getOrElse("suoritusrekisteri.cache.hours.valintatulos", "4").toInt
  val hakuRefreshTimeHours = properties.getOrElse("suoritusrekisteri.refresh.time.hours.haku", "12").toInt
  val hakemusRefreshTimeHours = properties.getOrElse("suoritusrekisteri.refresh.time.hours.hakemus", "2").toInt
  val valintatulosRefreshTimeHours = properties.getOrElse("suoritusrekisteri.refresh.time.hours.valintatulos", "2").toInt

  import RicherString._

  val ytlConfig = for (
    host <- properties.get("suoritusrekisteri.ytl.host").flatMap(_.blankOption);
    user <- properties.get("suoritusrekisteri.ytl.user").flatMap(_.blankOption);
    password <- properties.get("suoritusrekisteri.ytl.password").flatMap(_.blankOption);
    inbox <- properties.get("suoritusrekisteri.ytl.inbox").flatMap(_.blankOption);
    outbox <- properties.get("suoritusrekisteri.ytl.outbox").flatMap(_.blankOption);
    poll <- properties.get("suoritusrekisteri.ytl.poll").flatMap(_.blankOption);
    localStore <- properties.get("suoritusrekisteri.ytl.localstore").flatMap(_.blankOption)
  ) yield YTLConfig(host, user, password, inbox, outbox, poll.split(";").map(LocalTime.parse), localStore)
}
