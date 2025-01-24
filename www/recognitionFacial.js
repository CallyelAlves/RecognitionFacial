var RecognitionFacial = {
    enrollFromBase64: function(personId, image, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "enrollFromBase64", [personId, image]);
    }
};

module.exports = megaMatcherPlugin;
