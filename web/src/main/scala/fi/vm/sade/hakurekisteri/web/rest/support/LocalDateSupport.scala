package fi.vm.sade.hakurekisteri.web.rest.support

import org.scalatra.servlet.FileItem
import org.scalatra.{DefaultValue, util}
import org.joda.time.{DateTime, LocalDate}
import org.scalatra.util.conversion.TypeConverter
import org.joda.time.format.DateTimeFormat
import org.json4s._

trait LocalDateSupport { this: HakurekisteriCommand[_] =>
  import util.RicherString._

  implicit def OptionLocalDateDefaultValue: DefaultValue[Option[LocalDate]] = org.scalatra.DefaultValueMethods.default(None)
  implicit def LocalDateDefaultValue: DefaultValue[LocalDate] = org.scalatra.DefaultValueMethods.default(LocalDate.now())

  implicit val stringToLocalDate: TypeConverter[String, LocalDate] = safeOption((in: String) => in.blankOption map DateTimeFormat.forPattern("dd.MM.yyyy").parseLocalDate)
  implicit val jsonToLocalDate: TypeConverter[JValue, LocalDate] = safeOption(_.extractOpt[LocalDate])
  implicit val fileToLocalDate: TypeConverter[FileItem, LocalDate] = cantConvert

  implicit val stringToOptionLocalDate: TypeConverter[String, Option[LocalDate]] = safe((in: String) => in.blankOption map DateTimeFormat.forPattern("dd.MM.yyyy").parseLocalDate)
  implicit val jsonToOptionLocalDate: TypeConverter[JValue, Option[LocalDate]] = safe(_.extractOpt[LocalDate])
  implicit val fileToOptionLocalDate: TypeConverter[FileItem, Option[LocalDate]] = cantConvert

}
