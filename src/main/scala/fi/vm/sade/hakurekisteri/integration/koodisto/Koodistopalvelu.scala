package fi.vm.sade.hakurekisteri.integration.koodisto

import java.net.URL

import com.stackmob.newman.ApacheHttpClient
import com.stackmob.newman.dsl._
import com.stackmob.newman.response.HttpResponseCode
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

case class Koodisto(koodistoUri: String)
case class Koodi(koodiArvo: String, koodiUri: String, koodisto: Koodisto)

trait Koodistopalvelu {

  def getRinnasteinenKoodiArvo(koodiUri: String, rinnasteinenKoodistoUri: String): Future[String]

}

class RestKoodistopalvelu(serviceUrl: String = "https://itest-virkailija.oph.ware.fi/koodisto-service")(implicit val ec: ExecutionContext) extends Koodistopalvelu {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val httpClient = new ApacheHttpClient()()

  override def getRinnasteinenKoodiArvo(koodiUri: String, rinnasteinenKoodistoUri: String): Future[String] = {
    val url = new URL(serviceUrl + "/rest/json/relaatio/rinnasteinen/" + koodiUri)
    logger.debug("calling koodisto-service [{}]", url)
    GET(url).apply.map(response => {
      if (response.code == HttpResponseCode.Ok) {
        val koodiList = response.bodyAsCaseClass[Koodi].toList
        logger.debug("got response: [{}]", koodiList)
        if (!koodiList.isEmpty) {
          val filtered = koodiList.filter(_.koodisto.koodistoUri == rinnasteinenKoodistoUri)
          if (!filtered.isEmpty) filtered.head.koodiArvo else throw new RuntimeException("rinnasteista koodia ei löytynyt kyseiseen koodistoon")
        } else {
          throw new RuntimeException("rinnasteisia koodeja ei löytynyt koodiurilla [%s]".format(koodiUri))
        }
      } else {
        logger.error("call to koodisto-service [{}] failed: {}", Array(url, response.code))
        throw new RuntimeException("virhe kutsuttaessa koodistopalvelua: %s".format(response.code))
      }
    })
  }

}