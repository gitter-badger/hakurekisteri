package fi.vm.sade.hakurekisteri.batchimport

import java.io.StringReader
import java.util.UUID

import akka.actor.{ActorSystem, Props}
import fi.vm.sade.hakurekisteri.acceptance.tools.{FakeAuthorizer, TestSecurity}
import fi.vm.sade.hakurekisteri.rest.support.HakurekisteriDriver.simple._
import fi.vm.sade.hakurekisteri.rest.support.{HakurekisteriSwagger, JDBCJournal}
import org.scalatra.swagger.Swagger
import org.scalatra.test.{Uploadable, BytesPart}
import org.scalatra.test.scalatest.ScalatraFunSuite
import org.scalatra.validation.{FieldName, ValidationError}
import siirto.{SchemaDefinition, ValidXml}

import scala.xml.Elem
import scala.xml.Source._


class ImportBatchResourceSpec extends ScalatraFunSuite {
  implicit val swagger: Swagger = new HakurekisteriSwagger
  implicit val system = ActorSystem("test-import-batch")

  implicit val database = Database.forURL("jdbc:h2:mem:importbatchtest;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
  val eraJournal = new JDBCJournal[ImportBatch, UUID, ImportBatchTable](TableQuery[ImportBatchTable])
  val eraRekisteri = system.actorOf(Props(new ImportBatchActor(eraJournal, 5)))
  val authorized = system.actorOf(Props(new FakeAuthorizer(eraRekisteri)))

  override def stop(): Unit = {
    system.shutdown()
    system.awaitTermination()
    super.stop()
  }

  object TestSchema extends SchemaDefinition {
    override val schemaLocation: String = "test.xsd"
    override val schema: Elem =
      <xs:schema attributeFormDefault="unqualified"
                 elementFormDefault="qualified"
                 xmlns:xs="http://www.w3.org/2001/XMLSchema">



        <xs:element name="batch">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="data" minOccurs="1" maxOccurs="1"></xs:element>
            </xs:sequence>
          </xs:complexType>

        </xs:element>
      </xs:schema>
  }

  val validator = new ValidXml(TestSchema)

  addServlet(
    new ImportBatchResource(
      authorized,
      (foo) => ImportBatchQuery(None))
    ("identifier",
        "test",
        "data",
        (elem) => validator.load(fromReader(new StringReader(elem.toString))).leftMap(_.map{ case (level, ex) => ValidationError(ex.getMessage, FieldName("data"), ex)})) with TestSecurity, "/")



  test("post should return 201 created") {
    post("/", "<batch><data>foo</data></batch>") {
      response.status should be(201)
    }
  }

  test("post with fileupload should return 201 created") {
    val fileData = XmlPart("test.xml", <batch><data>foo</data></batch>)

    post("/", Map[String, String](), List("data" -> fileData)) {
      response.status should be(201)
    }
  }


  test("post with bad file should return 400") {
    val fileData = XmlPart("test.xml", <batch><bata>foo</bata></batch>)

    post("/", Map[String, String](), List("data" -> fileData)) {

      println("FOO: " + response.body)
      response.status should be(400)
    }
  }


  case class XmlPart(fileName: String, xml:Elem) extends Uploadable {
    override lazy val content: Array[Byte] = xml.toString().getBytes("UTF-8")

    override lazy val contentLength: Long = content.length

    override val contentType: String = "application/xml"
  }




}
