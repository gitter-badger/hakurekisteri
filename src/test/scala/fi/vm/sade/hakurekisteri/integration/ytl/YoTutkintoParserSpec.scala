package fi.vm.sade.hakurekisteri.integration.ytl

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.joda.time.format.DateTimeFormat
import YtlData._

class YoTutkintoParserSpec extends FlatSpec with ShouldMatchers {


  behavior of "YTL YO Suoritus parser"

  import YTLXml.extractYo


  it should "return None for a person whos not YO" in {
    extractYo("oid", eiValmis) should be (None)

  }


  it should "parse a suoritus with given oid oid" in {
    extractYo("oid", suorittanut).get.henkiloOid should be ("oid")
  }

  it should "use date of exam as graduation date if just marked graduating" in {
    val kevat1995 = DateTimeFormat.forPattern("dd.MM.yyyy").parseLocalDate("01.06.1995")

    extractYo("oid", suorittanut).get.valmistuminen should be (kevat1995)

  }

  it should "use date of graduation if given" in {

    val kevat2005 = DateTimeFormat.forPattern("dd.MM.yyyy").parseLocalDate("01.06.2005")

    extractYo("oid", ylioppilas).get.valmistuminen should be (kevat2005)
  }

  it should "have the same language as given from YTL as exam language" in {

    extractYo("oid", ylioppilas).get.suoritusKieli should be ("FI")

  }


}