package fi.vm.sade.hakurekisteri.arvosana

import org.scalatra.swagger._
import scala.Some
import org.scalatra.swagger.AllowableValues.AnyValue
import fi.vm.sade.hakurekisteri.rest.support.HakurekisteriResource
import fi.vm.sade.hakurekisteri.opiskelija.Opiskelija
import java.util.UUID

trait ArvosanaSwaggerApi  { this: HakurekisteriResource[Arvosana, CreateArvosanaCommand] =>

  override protected val applicationName = Some("arvosanat")
  protected val applicationDescription = "Arvosanojen rajapinta"

  val arvioFields = Seq(ModelField("arvosana", "arvosana", DataType.String),
    ModelField("asteikko", "arvosanan asteikko", DataType.String, Some(Arvio.ASTEIKKO_4_10), AllowableValues(Arvio.asteikot.toList)))

  val arvioModel = Model("Arvio", "Arviotiedot", arvioFields.map(t => (t.name, t)).toMap)

  registerModel(arvioModel)

  val fields = Seq(ModelField("id", "arvosanan uuid", DataType.String, None, AnyValue, required = false),
    ModelField("suoritus", "suorituksen uuid", DataType.String),
    ModelField("arvio", "arvosana", DataType("Arvio")),
    ModelField("aine", "aine josta arvosana on annettu", DataType.String),
    ModelField("lisatieto", "aineen lisätieto. esim kieli", DataType.String, required = false),
    ModelField("valinnainen", "onko aine ollut valinnainen", DataType.Boolean, Some("false"), required = false))

  val arvosanaModel = Model("Arvosana", "Arvosanatiedot", fields.map(t => (t.name, t)).toMap)

  registerModel(arvosanaModel)

  val query = apiOperation[Arvosana]("haeArvosanat")
    .summary("näyttää kaikki arvosanat")
    .notes("Näyttää kaikki arvosanat. Voit myös hakea suorituksella.")
    .parameter(queryParam[Option[String]]("suoritus").description("suorituksen uuid"))

  val create = apiOperation[Arvosana]("lisääArvosana")
    .summary("luo arvosanan ja palauttaa sen tiedot")
    .parameter(bodyParam[Arvosana]("arvosana").description("uusi arvosana").required)

  val update = apiOperation[Arvosana]("päivitäArvosana")
    .summary("päivittää olemassa olevaa arvosanaa ja palauttaa sen tiedot")
    .parameter(pathParam[String]("id").description("arvosanan uuid").required)
    .parameter(bodyParam[Arvosana]("arvosana").description("päivitettävä arvosana").required)

  val read = apiOperation[Arvosana]("haeArvosana")
    .summary("hakee arvosanan tiedot")
    .parameter(pathParam[String]("id").description("arvosanan uuid").required)

}




