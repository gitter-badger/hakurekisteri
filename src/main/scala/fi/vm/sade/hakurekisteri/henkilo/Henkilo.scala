package fi.vm.sade.hakurekisteri.henkilo

import fi.vm.sade.hakurekisteri.storage.Identified
import java.util.UUID
import fi.vm.sade.hakurekisteri.rest.support.Resource


case class Henkilo (
                     yhteystiedotRyhma: Seq[YhteystiedotRyhma],
                     yksiloity: Boolean,
                     sukunimi: String,
                     kielisyys: Seq[Kieli],
                     yksilointitieto: Option[Yksilointitieto],
                     henkiloTyyppi: String,
                     oidHenkilo: String,
                     duplicate: Boolean,
                     oppijanumero: String,
                     kayttajatiedot: Option[Kayttajatiedot],
                     kansalaisuus: Seq[Kansalaisuus],
                     passinnumero: String,
                     asiointiKieli: Kieli,
                     kutsumanimi: String,
                     passivoitu: Boolean,
                     eiSuomalaistaHetua: Boolean,
                     etunimet: String,
                     sukupuoli: String,
                     turvakielto: Boolean,
                     hetu: String,
                     syntymaaika: String,
                     markkinointilupa: Option[Boolean]) extends Resource{

  override def identify[R <: Henkilo](id: UUID): R with Identified = Henkilo.identify(this,id).asInstanceOf[R with Identified]
}


case class Yhteystiedot (id: Int,
                         yhteystietoTyyppi: String,
                         yhteystietoArvo: String)


case class Kansalaisuus (
                          kansalaisuusKoodi: String)

case class Kayttajatiedot (
                            username: String)

case class Kieli (
                   kieliKoodi: String,
                   kieliTyyppi: String)

case class YhteystiedotRyhma (
                               id: Int,
                               ryhmaKuvaus: String,
                               ryhmaAlkuperaTieto: String,
                               readOnly: Boolean,
                               yhteystiedot: Seq[Yhteystiedot])

object YhteystiedotRyhma {
  implicit def yhteystietoryhmatToMap(yhteystiedot: Seq[YhteystiedotRyhma]): Map[(String, String), Seq[Yhteystiedot]] = {
    yhteystiedot.map((y) => (y.ryhmaAlkuperaTieto, y.ryhmaKuvaus) -> y.yhteystiedot).toMap
  }

}

object Yhteystiedot {

  implicit def yhteystiedotToMap(yhteystiedot: Seq[Yhteystiedot]): Map[String, String] = {
    yhteystiedot.map((y) => y.yhteystietoTyyppi -> y.yhteystietoArvo).toMap
  }


}

case class Yksilointitieto (
                             hetu: String)


object Henkilo extends {
  def identify(o:Henkilo): Henkilo with Identified = o match {
    case o: Henkilo with Identified => o
    case _ => o.identify(UUID.randomUUID())
  }

  def identify(o:Henkilo, identity: UUID): Henkilo with Identified = {
    new Henkilo(o.yhteystiedotRyhma,
      o.yksiloity: Boolean,
      o.sukunimi: String,
      o.kielisyys: Seq[Kieli],
      o.yksilointitieto: Option[Yksilointitieto],
      o.henkiloTyyppi: String,
      o.oidHenkilo: String,
      o.duplicate: Boolean,
      o.oppijanumero: String,
      o.kayttajatiedot: Option[Kayttajatiedot],
      o.kansalaisuus: Seq[Kansalaisuus],
      o.passinnumero: String,
      o.asiointiKieli: Kieli,
      o.kutsumanimi: String,
      o.passivoitu: Boolean,
      o.eiSuomalaistaHetua: Boolean,
      o.etunimet: String,
      o.sukupuoli: String,
      o.turvakielto: Boolean,
      o.hetu: String,
      o.syntymaaika: String,
      o.markkinointilupa: Option[Boolean]) with Identified{
      override val id: UUID = identity
    }
  }
}