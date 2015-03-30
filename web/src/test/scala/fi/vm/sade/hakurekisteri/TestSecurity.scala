package fi.vm.sade.hakurekisteri

import javax.servlet.http.HttpServletRequest

import fi.vm.sade.hakurekisteri.rest.support.User
import fi.vm.sade.hakurekisteri.web.rest.support.SecuritySupport

/**
 * Created by laastine on 11/03/15.
 */
trait TestSecurity extends SecuritySupport {

  object TestUser extends User {

    override def orgsFor(action: String, resource: String): Set[String] = Set("1.2.246.562.10.00000000001")

    override val username: String = "Test"
  }

  override def currentUser(implicit request: HttpServletRequest): Option[fi.vm.sade.hakurekisteri.rest.support.User] = Some(TestUser)
}