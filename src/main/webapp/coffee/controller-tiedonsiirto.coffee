app.controller "TiedonsiirtoCtrl", [
  "$scope"
  "MurupolkuService"
  ($scope, MurupolkuService) ->
    isImportBatchResponse = (content) ->
      (typeof content is "string" and content.match(/.*"batchType".*/g)) or (typeof content is "object" and content.batchType)
    isIncidentResponse = (content) ->
      (typeof content is "string" and content.match(/.*"incidentId".*/g)) or (typeof content is "object" and content.incidentId)
    MurupolkuService.addToMurupolku
      key: "suoritusrekisteri.tiedonsiirto.muru"
      text: "Tiedonsiirto"
    , true
    $scope.send = ->
      $scope.sending = true
      delete $scope.uploadResult

      return

    clearFile = () ->
      try
        elem = document.getElementById("tiedosto")
        if elem
          elem.value = ''
          if elem.value
            elem.type = 'text'
            elem.type = 'file'
      catch e
        $log.error(e)


    $scope.uploadComplete = (content) ->
      if isImportBatchResponse(content)
        response = (if typeof content is "object" then content else angular.fromJson(content))
        $scope.uploadResult =
          type: "success"
          message: "Tiedosto lähetetty."
          messageKey: "suoritusrekisteri.tiedonsiirto.tiedostolahetetty"
          id: response.id
      else if isIncidentResponse(content)
        response = (if typeof content is "object" then content else angular.fromJson(content))
        $scope.uploadResult =
          type: "danger"
          message: "Virhe lähettäessä tiedostoa."
          messageKey: "suoritusrekisteri.tiedonsiirto.virhe"
          description: response.message
          validationErrors: response.validationErrors
      delete $scope.sending

      clearFile()

      return

    $scope.reset = ->
      document.getElementById("uploadForm").reset()
      delete $scope.uploadResult

      return
]