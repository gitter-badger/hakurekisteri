import javax.servlet.ServletContextEvent

import org.apache.catalina.Globals
import org.apache.catalina.deploy.ContextResource
import org.scalatra.servlet.ScalatraListener

class ScalatraListenerTest extends ScalatraListener {


  override def configureServletContext(sce: ServletContextEvent) {
    val x = sce.getServletContext.getInitParameterNames

    super.configureServletContext(sce)
  }


  override def configureExecutionContext(sce: ServletContextEvent): Unit = {
    println("blob")

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
