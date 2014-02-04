package fi.vm.sade.hakurekisteri.rest

import fi.vm.sade.hakurekisteri.HakuJaValintarekisteriStack
import org.scalatra.swagger._
import org.scalatra.json.{JacksonJsonSupport, JacksonJsonOutput}
import scala.concurrent.{Future, ExecutionContext}
import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem}
import org.scalatra.{Params, AsyncResult, FutureSupport}
import fi.vm.sade.hakurekisteri.domain.Suoritus
import _root_.akka.pattern.ask
import org.scalatra.swagger.SwaggerSupportSyntax.{SwaggerOperationBuilder, OperationBuilder}


abstract class HakurekisteriResource[A](actor:ActorRef)(implicit system: ActorSystem, mf: Manifest[A])extends HakuJaValintarekisteriStack with HakurekisteriJsonSupport with JacksonJsonSupport with SwaggerSupport with FutureSupport {

  protected implicit def executor: ExecutionContext = system.dispatcher

  val timeout = 10

  implicit val defaultTimeout = Timeout(timeout)

  before() {
    contentType = formats("json")
  }


  post("/") {
    new AsyncResult() {
      val is = actor ? parsedBody.extract[A]
    }
  }

  def readOperation(op: OperationBuilder, pb: Params => AnyRef) {
    get("/", operation(op))(resourceQuery(pb(params)))
  }

  def resourceQuery(query: AnyRef): AsyncResult {val is: Future[Any]} = {
    new AsyncResult() {
      val is = actor ? query
    }
  }

}
