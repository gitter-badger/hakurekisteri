package fi.vm.sade.hakurekisteri

import java.io.File

import org.apache.catalina.deploy.ContextResource
import org.slf4j.LoggerFactory
import fi.vm.sade.integrationtest.tomcat.EmbeddedTomcat
import fi.vm.sade.integrationtest.tomcat.SharedTomcat
import fi.vm.sade.integrationtest.util.PortChecker
import fi.vm.sade.integrationtest.util.ProjectRootFinder
import HakuRekisteriTomcatSettings._
import scala.collection.JavaConversions._

class HakurekisteriTomcat(port: Int, moduleRoot: String, contextPath: String, resources: List[ContextResource]) extends EmbeddedTomcat(port, moduleRoot, contextPath, resources) {
  def startShared = SharedTomcat.start(HAKU_MODULE_ROOT, HAKU_CONTEXT_PATH)

  def startForIntegrationTestIfNotRunning {
    useIntegrationTestSettings
    if (PortChecker.isFreeLocalPort(DEFAULT_PORT)) {
      new HakurekisteriTomcat(DEFAULT_PORT, HAKU_MODULE_ROOT, HAKU_CONTEXT_PATH, List.empty).start
    }
    else {
      LoggerFactory.getLogger(classOf[HakurekisteriTomcat]).info("Not starting Tomcat: seems to be running on port " + DEFAULT_PORT)
    }
  }

  def useIntegrationTestSettings {
    System.setProperty("application.system.cache", "false")
    if (System.getProperty("spring.profiles.active") == null) {
      System.setProperty("spring.profiles.active", "it")
    }
  }
}

object HakuRekisteriTomcatSettings {
  val HAKU_MODULE_ROOT = new File(".").getAbsolutePath + "/web/"
  val HAKU_CONTEXT_PATH = "/suoritusrekisteri"
  val DEFAULT_PORT = 9091
}

object HakurekisteriTomcat {
  def main(args: Array[String]) {
    //val tomcat = new HakurekisteriTomcat(System.getProperty("hakurekisteri-app.port", String.valueOf(DEFAULT_PORT)).toInt, HAKU_MODULE_ROOT, HAKU_CONTEXT_PATH, List(createDbResource("suoritusrekisteri"), createDbResource("tiedonsiirto")))

    val tomcat = new HakurekisteriTomcat(System.getProperty("hakurekisteri-app.port", String.valueOf(DEFAULT_PORT)).toInt, HAKU_MODULE_ROOT, HAKU_CONTEXT_PATH, List())
    tomcat.useIntegrationTestSettings


    //tomcat.addResource(createDbResource("suoritusrekisteri"))
    //tomcat.addResource(createDbResource("tiedonsiirto"))
    tomcat.start.await()

  }

  def createDbResource(dbName: String) = {
    val resource = new ContextResource
    resource.setName(s"java:comp/env/jdbc/$dbName")
    resource.setAuth("Container")
    resource.setType("javax.sql.DataSource")
    resource.setScope("Sharable")
    resource.setProperty("initialSize", 5)
    resource.setProperty("maxActive", 50)
    resource.setProperty("minIdle", 5)
    resource.setProperty("initialSize", 10)
    resource.setProperty("driverClassName", "org.postgresql.Driver")
    resource.setProperty("username", "vagrant")
    resource.setProperty("password", "")
    resource.setProperty("url", s"jdbc:postgresql://kehitys-virkailija.oph.ware.fi:5432/$dbName")
    resource
  }
}
