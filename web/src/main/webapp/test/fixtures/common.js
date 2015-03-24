var httpFixtures = function () {
    var httpBackend = testFrame().httpBackend
    var fixtures = {}
    fixtures.organisaatioService = {
        pikkarala: function() {
            httpBackend.when('GET', /.*\/organisaatio-service\/rest\/organisaatio\/v2\/hae\?aktiiviset=true&lakkautetut=false&organisaatiotyyppi=Oppilaitos&searchStr=Pik&suunnitellut=true$/).respond({"numHits":1,"organisaatiot":[{"oid":"1.2.246.562.10.39644336305","alkuPvm":694216800000,"parentOid":"1.2.246.562.10.80381044462","parentOidPath":"1.2.246.562.10.39644336305/1.2.246.562.10.80381044462/1.2.246.562.10.00000000001","oppilaitosKoodi":"06345","oppilaitostyyppi":"oppilaitostyyppi_11#1","match":true,"nimi":{"fi":"Pikkaralan ala-aste"},"kieletUris":["oppilaitoksenopetuskieli_1#1"],"kotipaikkaUri":"kunta_564","children":[],"organisaatiotyypit":["OPPILAITOS"],"aliOrganisaatioMaara":0}]})
        },
        pikkaralaKoodi: function () {
            httpBackend.when('GET', /.*\/organisaatio-service\/rest\/organisaatio\/06345$/).respond({"oid":"1.2.246.562.10.39644336305","nimi":{"fi":"Pikkaralan ala-aste"},"alkuPvm":"1992-01-01","postiosoite":{"osoiteTyyppi":"posti","yhteystietoOid":"1.2.246.562.5.75344290822","postinumeroUri":"posti_90310","osoite":"Vasantie 121","postitoimipaikka":"OULU","ytjPaivitysPvm":null,"lng":null,"lap":null,"coordinateType":null,"osavaltio":null,"extraRivi":null,"maaUri":null},"parentOid":"1.2.246.562.10.80381044462","parentOidPath":"|1.2.246.562.10.00000000001|1.2.246.562.10.80381044462|","vuosiluokat":[],"oppilaitosKoodi":"06345","kieletUris":["oppilaitoksenopetuskieli_1#1"],"oppilaitosTyyppiUri":"oppilaitostyyppi_11#1","yhteystiedot":[{"kieli":"kieli_fi#1","id":"22913","yhteystietoOid":"1.2.246.562.5.11296174961","email":"kaisa.tahtinen@ouka.fi"},{"tyyppi":"faksi","kieli":"kieli_fi#1","id":"22914","yhteystietoOid":"1.2.246.562.5.18105745956","numero":"08  5586 1582"},{"tyyppi":"puhelin","kieli":"kieli_fi#1","id":"22915","yhteystietoOid":"1.2.246.562.5.364178776310","numero":"08  5586 9514"},{"kieli":"kieli_fi#1","id":"22916","yhteystietoOid":"1.2.246.562.5.94533742915","www":"http://www.edu.ouka.fi/koulut/pikkarala"},{"osoiteTyyppi":"posti","kieli":"kieli_fi#1","id":"22917","yhteystietoOid":"1.2.246.562.5.75344290822","osoite":"Vasantie 121","postinumeroUri":"posti_90310","postitoimipaikka":"OULU","ytjPaivitysPvm":null,"coordinateType":null,"lap":null,"lng":null,"osavaltio":null,"extraRivi":null,"maaUri":null},{"osoiteTyyppi":"kaynti","kieli":"kieli_fi#1","id":"22918","yhteystietoOid":"1.2.246.562.5.58988409759","osoite":"Vasantie 121","postinumeroUri":"posti_90310","postitoimipaikka":"OULU","ytjPaivitysPvm":null,"coordinateType":null,"lap":null,"lng":null,"osavaltio":null,"extraRivi":null,"maaUri":null}],"kuvaus2":{},"tyypit":["Oppilaitos"],"yhteystietoArvos":[],"nimet":[{"nimi":{"fi":"Pikkaralan ala-aste"},"alkuPvm":"1992-01-01","version":1}],"ryhmatyypit":[],"kayttoryhmat":[],"kayntiosoite":{"osoiteTyyppi":"kaynti","yhteystietoOid":"1.2.246.562.5.58988409759","postinumeroUri":"posti_90310","osoite":"Vasantie 121","postitoimipaikka":"OULU","ytjPaivitysPvm":null,"lng":null,"lap":null,"coordinateType":null,"osavaltio":null,"extraRivi":null,"maaUri":null},"kotipaikkaUri":"kunta_564","maaUri":"maatjavaltiot1_fin","version":1,"status":"AKTIIVINEN"})
        },
        pikkaralaOid: function () {
            httpBackend.when('GET', /.*\/organisaatio-service\/rest\/organisaatio\/1\.2\.246\.562\.10\.39644336305$/).respond({"numHits":1,"organisaatiot":[{"oid":"1.2.246.562.10.39644336305","alkuPvm":694216800000,"parentOid":"1.2.246.562.10.80381044462","parentOidPath":"1.2.246.562.10.39644336305/1.2.246.562.10.80381044462/1.2.246.562.10.00000000001","oppilaitosKoodi":"06345","oppilaitostyyppi":"oppilaitostyyppi_11#1","match":true,"nimi":{"fi":"Pikkaralan ala-aste"},"kieletUris":["oppilaitoksenopetuskieli_1#1"],"kotipaikkaUri":"kunta_564","children":[],"aliOrganisaatioMaara":0,"organisaatiotyypit":["OPPILAITOS"]}]})
        }
    }
    fixtures.authenticationService = {
        aarne: function() {
            httpBackend.when('GET', /.*\/authentication-service\/resources\/henkilo\/1\.2\.246\.562\.24\.71944845619$/).respond({"id":90176,"etunimet":"Aarne","syntymaaika":"1958-10-12","passinnumero":null,"hetu":"123456-789","kutsumanimi":"aa","oidHenkilo":"1.2.246.562.24.71944845619","oppijanumero":null,"sukunimi":"AA","sukupuoli":"1","turvakielto":null,"henkiloTyyppi":"OPPIJA","eiSuomalaistaHetua":false,"passivoitu":false,"yksiloity":false,"yksiloityVTJ":true,"yksilointiYritetty":false,"duplicate":false,"created":null,"modified":null,"kasittelijaOid":null,"asiointiKieli":null,"aidinkieli":null,"kayttajatiedot":null,"kielisyys":[],"kansalaisuus":[]})
        },
        tyyne: function() {
            httpBackend.when('GET', /.*\/authentication-service\/resources\/henkilo\/1\.2\.246\.562\.24\.98743797763$/).respond({"id":90177,"etunimet":"Tyyne","syntymaaika":"1919-07-01","passinnumero":null,"hetu":"010719-917S","kutsumanimi":"aaa","oidHenkilo":"1.2.246.562.24.98743797763","oppijanumero":null,"sukunimi":"aaa","sukupuoli":"1","turvakielto":null,"henkiloTyyppi":"OPPIJA","eiSuomalaistaHetua":false,"passivoitu":false,"yksiloity":false,"yksiloityVTJ":true,"yksilointiYritetty":false,"duplicate":false,"created":null,"modified":null,"kasittelijaOid":null,"asiointiKieli":null,"aidinkieli":null,"huoltaja":null,"kayttajatiedot":null,"kielisyys":[],"kansalaisuus":[],"yhteystiedotRyhma":[]})
        },
        foobar: function() {
            httpBackend.when('GET', /.*\/authentication-service\/resources\/henkilo\?index=0&count=1&no=true&p=false&s=true&q=FOOBAR$/).respond({"totalCount":0,"results":[]})
        }
    }
    return fixtures
}