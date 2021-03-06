package fi.vm.sade.hakurekisteri.web.suoritus

import org.scalatra.commands._
import org.joda.time.LocalDate
import fi.vm.sade.hakurekisteri.suoritus.yksilollistaminen.Yksilollistetty
import java.util.Locale
import fi.vm.sade.hakurekisteri.web.rest.support.{LocalDateSupport, HakurekisteriCommand}
import fi.vm.sade.hakurekisteri.suoritus.{VirallinenSuoritus, Suoritus}


class CreateSuoritusCommand extends HakurekisteriCommand[Suoritus] with LocalDateSupport {

  val komo: Field[String] = asType[String]("komo").notBlank
  val myontaja: Field[String] = asType[String]("myontaja").notBlank
  val tila: Field[String] = asType[String]("tila").notBlank
  val valmistuminen: Field[LocalDate] = asType[LocalDate]("valmistuminen").required
  val henkiloOid: Field[String]  = asType[String]("henkiloOid").notBlank
  val yks: Field[Yksilollistetty]  = asType[Yksilollistetty]("yksilollistaminen")
  val languages = Seq(Locale.getISOLanguages:_*) ++ Seq(Locale.getISOLanguages:_*).map(_.toUpperCase)
  val suoritusKieli: Field[String] = asType[String]("suoritusKieli").required.allowableValues(languages:_*)

  override def toResource(user: String): Suoritus = VirallinenSuoritus(komo.value.get, myontaja.value.get, tila.value.get, valmistuminen.value.get, henkiloOid.value.get, yks.value.get, suoritusKieli.value.get, lahde = user)}


