function opiskelijatiedotPage() {
    function isLocalhost() {
        return location.host.indexOf('localhost') > -1
    }

    var opiskelijatiedotPage = openPage((isLocalhost() ? '' : '/suoritusrekisteri') + "/#/opiskelijat", function () {
        return S("#filterForm").length === 1
    })

    var pageFunctions = {
        filterForm: function () {
            return S("#filterForm").first()
        },
        openPage: function (done) {
            return opiskelijatiedotPage()
                .then(wait.until(function () {
                    var pageReady = pageFunctions.filterForm().length === 1
                    if (pageReady) {
                        done()
                    }
                    return pageReady
                }))
        }
    };
    return pageFunctions;
}

opiskelijatiedot = initSelectors({
    organizationSearch: "#organisaatioTerm",
    searchButton: "#filterForm button[type=submit]",
    resultsTable: "#table-scroller tr",
    resultsTableChild: function(n) {return "#table-scroller tr:nth-child("+n+")"},
    typeaheadMenuChild: function(n) {
        return "ul.dropdown-menu > li:nth-child("+n+"):has(a)"
    },
    henkiloSearch: "#henkiloTerm",
    henkiloTiedot: "#henkiloTiedot",
    vuosiSearch: "#vuosiTerm",
    hetu: ".test-hetu",
    etunimi: ".test-etunimi",
    sukunimi: ".test-sukunimi",
    suoritusTiedot: "#suoritusTiedot",
    luokkaTiedot: "#luokkaTiedot",
    hakijanIlmoittamaAlert: ".alert-info",
    suoritusMyontaja: ".test-suoritusMyontaja:visible",
    suoritusKoulutus: ".test-suoritusKoulutus:visible",
    suoritusYksilollistetty: ".test-suoritusYksilollistetty:visible",
    suoritusKieli: ".test-suoritusKieli:visible",
    suoritusValmistuminen: ".test-suoritusValmistuminen:visible",
    suoritusTila: ".test-suoritusTila:visible",
    luokkatietoOppilaitos: ".test-luokkatietoOppilaitos",
    luokkatietoLuokka: ".test-luokkatietoLuokka",
    luokkatietoLuokkaTaso: ".test-luokkatietoLuokkaTaso",
    luokkatietoAlkuPaiva: ".test-luokkatietoAlkuPaiva",
    luokkatietoLoppuPaiva: ".test-luokkatietoLoppuPaiva",
    luokkatietoLisaa: ".test-luokkatietoLisaa",
    luokkatietoPoista: ".test-luokkatietoPoista",
    opiskeluoikeusAlkuPaiva: ".test-opiskeluoikeusAlkuPaiva",
    opiskeluoikeusLoppuPaiva: ".test-opiskeluoikeusLoppuPaiva",
    opiskeluoikeusMyontaja: ".test-opiskeluoikeusMyontaja",
    opiskeluoikeusKoulutus: ".test-opiskeluoikeusKoulutus",
    saveButton: ".test-saveButton",
    arvosanaAineRivi: ".test-aineRivi:visible",
    arvosanaAineNimi: ".test-aineNimi",
    arvosanaMyonnetty: ".test-arvosanaMyonnetty:visible",
    arvosanaLisatieto: ".test-arvosanaLisatieto:visible",
    arvosanaPakollinenArvosana: ".test-arvosanaPakollinenArvosana:visible",
    arvosanaValinnainenArvosana: ".test-arvosanaValinnainenArvosana:visible",
    arvosanaValinnainenHeader: ".test-valinnaiset-header",
    yoArvosanaAddKoe: ".test-addKoe",
    yoArvosanaAine: ".test-yoArvosanaAine:visible",
    yoArvosanaTaso: ".test-yoArvosanaTaso:visible",
    yoArvosanaArvosana: ".test-yoArvosanaArvosana:visible",
    yoArvosanaPistemaara: ".test-yoArvosanaPistemaara:visible",
    yoArvosanaMyonnetty: ".test-yoArvosanaMyonnetty:visible",
    suoritusPoista: ".test-suoritusPoista",
    yoTxt: ".test-yoTxt:visible",
    editArvosanat: ".test-editArvosanat",
    arvosana: function(rowIndex, selectIndex) {
        return ".test-aineRivi:eq(" + rowIndex + ") select.test-arvosanaSelect:eq(" + selectIndex + ")"
    }
})
