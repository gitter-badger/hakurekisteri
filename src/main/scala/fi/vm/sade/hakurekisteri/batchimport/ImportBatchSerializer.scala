package fi.vm.sade.hakurekisteri.batchimport

import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JString, JObject}
import org.json4s.JsonDSL._

import org.json4s.Xml.{toJson, toXml}

import scala.xml.Elem


class ImportBatchSerializer extends CustomSerializer[ImportBatch] (format => (
  {
    case json: JObject =>
      val rawData = json \ "data"
      val external = json.findField(jf => jf._1 == "externalid").map(_._2).collect{ case JString(id) => id}
      val JString(batchType) = json \ "batchType"
      val JString(source) = json \ "source"
      ImportBatch(toXml(rawData).collectFirst{case e:Elem => e}.get, external, batchType, source)
  },
  {
    case ib: ImportBatch =>
      val result =  ("data" -> toJson(ib.data)) ~
                    ("batchType" -> ib.batchType) ~
                    ("source" -> ib.source)
      ib.externalId.map(id => result ~ ("externalId" -> id)).getOrElse(result)
  }
  )
)
