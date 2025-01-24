var recognitionFacial = {
    enrollFromBase64: function(personId, image, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "enrollFromBase64", [personId, image]);
    },
    initialize: function(successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "initialize", [e]);
    }
};

module.exports = recognitionFacial;
