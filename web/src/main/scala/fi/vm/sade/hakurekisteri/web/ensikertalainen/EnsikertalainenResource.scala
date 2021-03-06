package fi.vm.sade.hakurekisteri.web.ensikertalainen

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.ask
import fi.vm.sade.hakurekisteri.ensikertalainen.{Ensikertalainen, EnsikertalainenQuery, HetuNotFoundException}
import fi.vm.sade.hakurekisteri.integration.PreconditionFailedException
import fi.vm.sade.hakurekisteri.rest.support.HakurekisteriJsonSupport
import fi.vm.sade.hakurekisteri.web.HakuJaValintarekisteriStack
import fi.vm.sade.hakurekisteri.web.rest.support.{Security, SecuritySupport, IncidentReport}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerEngine}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class ParamMissingException(message: String) extends IllegalArgumentException(message)

class EnsikertalainenResource(ensikertalainenActor: ActorRef)
                             (implicit val sw: Swagger, system: ActorSystem, val security: Security) extends HakuJaValintarekisteriStack with HakurekisteriJsonSupport with EnsikertalainenSwaggerApi with JacksonJsonSupport with FutureSupport with CorsSupport with SecuritySupport {

  override protected def applicationDescription: String = "Korkeakouluhakujen kiintiöiden ensikertalaisuuden kyselyrajapinta"
  override protected implicit def swagger: SwaggerEngine[_] = sw
  override protected implicit def executor: ExecutionContext = system.dispatcher
  override val logger: LoggingAdapter = Logging.getLogger(system, this)

  before() {
    contentType = formats("json")
  }

  options("/*") {
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
  }

  get("/", operation(query)) {
    try {
      val henkiloOid = params("henkilo")
      new AsyncResult() {
        override implicit def timeout: Duration = 90.seconds
        override val is = (ensikertalainenActor ? EnsikertalainenQuery(henkiloOid))(90.seconds).mapTo[Ensikertalainen]
      }
    } catch {
      case t: NoSuchElementException => throw ParamMissingException("parameter henkilo missing")
    }
  }

  incident {
    case t: ParamMissingException => (id) => BadRequest(IncidentReport(id, t.getMessage))
    case t: HetuNotFoundException => (id) => BadRequest(IncidentReport(id, "henkilo does not have hetu; add hetu and try again"))
    case t: PreconditionFailedException => (id) => InternalServerError(IncidentReport(id, "backend service failed"))
  }

}

