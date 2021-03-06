package fi.vm.sade.hakurekisteri.db

import org.joda.time.DateTime
import org.scalatest.{Matchers, FlatSpec}
import fi.vm.sade.hakurekisteri.rest.support.HakurekisteriDriver
import HakurekisteriDriver.simple._
import scala.slick.jdbc.meta.MTable
import fi.vm.sade.hakurekisteri.batchimport.{ImportStatus, BatchState, ImportBatch, ImportBatchTable}
import fi.vm.sade.hakurekisteri.storage.repository.Updated
import java.util.UUID

class TableSpec extends FlatSpec with Matchers {

  behavior of "ImportBatchTable"

  val table = TableQuery[ImportBatchTable]

  it should "be able create itself" in {
    val db = Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

    val tables = db withSession {
      implicit session =>
        table.ddl.create
        MTable.getTables(table.baseTableRow.tableName).list
    }

    tables.size should be(1)
  }

  it should "be able to store updates" in {
    val xml = <batchdata>data</batchdata>
    val batch = ImportBatch(xml, Some("externalId"), "test", "test", BatchState.READY, ImportStatus()).identify(UUID.randomUUID())

    val result = withDb {
      implicit session =>
        table += Updated(batch)
    }

    result should be(1)
  }

  it should "be able to retrieve updates" in {
    val xml = <batchdata>data</batchdata>
    val batch = ImportBatch(xml, Some("externalId"), "test", "test", BatchState.READY, ImportStatus(new DateTime(), Some(new DateTime()), Map("foo" -> Set("foo exception")), Some(1), Some(0), Some(1))).identify(UUID.randomUUID())
    val table = TableQuery[ImportBatchTable]

    val result = withDb {
      implicit session =>
        table += Updated(batch)
        val results = for (
          result <- table
          if result.resourceId === batch.id

        ) yield result

        val Updated(current) = results.list.head
        current
    }

    result should be(batch)
  }

  def withDb[R](action: Session => R): R = {
    val db = Database.forURL("jdbc:h2:mem:test", driver = "org.h2.Driver")

    db withSession {
      implicit session =>
        if (MTable.getTables(table.baseTableRow.tableName).list.isEmpty) {
          table.ddl.create
        }

        action(session)
    }
  }
}
