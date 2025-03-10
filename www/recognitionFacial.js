var recognitionFacial = {
    enrollFromBase64: function(personId, image, successCallback, errorCallback) {
        console.log("Calling enrollFromBase64 with personId:", personId);
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "enrollFromBase64", [personId, image]);
    },
    initialize: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "initialize", []);
    },
    initializeLicense: function(successCallback, errorCallback) {
        console.log("Calling initializeLicense");
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "initializeLicense", []);
    },
};

module.exports = recognitionFacial;
