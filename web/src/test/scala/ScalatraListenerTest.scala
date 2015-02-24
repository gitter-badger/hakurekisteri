import javax.naming.spi.NamingManager
import javax.servlet.ServletContextEvent

import org.apache.catalina.Globals
import org.apache.catalina.deploy.ContextResource
import org.scalatra.servlet.ScalatraListener

class ScalatraListenerTest extends ScalatraListener {


  override def configureServletContext(sce: ServletContextEvent) {
    //val x = sce.getServletContext.getInitParameterNames
    //val jndiName = "java:comp/env/jdbc/suoritusrekisteri"

    //sce.getServletContext.setAttribute("java.naming.factory.initial", "org.springframework.jndi.JndiObjectFactoryBean")
    sce.getServletContext.setAttribute("java:comp/env/jdbc/suoritusrekisteri", createDbResource("suoritusrekisteri"))
    sce.getServletContext.setAttribute("jdbc/suoritusrekisteri", createDbResource("suoritusrekisteri"))


//    sce.getServletContext.setAttribute("java:comp/env/jdbc/tiedonsiirto", createDbResource("tiedonsiirto"))

    /*
    names = sce.getServletContext.getAttributeNames()
    while (names.hasMoreElements) {
      println("name bef: " + names.nextElement())
    }
*/

    super.configureServletContext(sce)
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
