package fi.vm.sade.hakurekisteri.opiskelija

import java.util.UUID
import fi.vm.sade.hakurekisteri.storage.Identified
import org.joda.time.DateTime
import fi.vm.sade.hakurekisteri.rest.support.{UUIDResource, Resource}

case class Opiskelija(oppilaitosOid: String, luokkataso: String, luokka: String, henkiloOid: String, alkuPaiva: DateTime, loppuPaiva: Option[DateTime] = None, source: String) extends UUIDResource[Opiskelija] {
   override def identify(identity: UUID): Opiskelija with Identified[UUID] = new IdentifiedOpiskelija(this, identity)

}

class IdentifiedOpiskelija(o: Opiskelija, identity: UUID) extends Opiskelija(o.oppilaitosOid, o.luokkataso, o.luokka, o.henkiloOid, o.alkuPaiva, o.loppuPaiva, o.source) with Identified[UUID]{
val id: UUID = identity
}


