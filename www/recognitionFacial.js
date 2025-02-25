var recognitionFacial = {
    initialize: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "initialize", []);
    },
    initializeMatchingClient: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "initializeMatchingClient", []);
    },
    enrollFromBase64: function(personId, image, successCallback, errorCallback) {
        console.log("Calling enrollFromBase64 with personId:", personId);
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "enrollFromBase64", [personId, image]);
    },
    identifyBase64: function(image, successCallback, errorCallback) {
        console.log("Calling identifyBase64 with image.");
        cordova.exec(successCallback, errorCallback, "RecognitionFacial", "identifyBase64", [image]);
    },
    startCamera: function(successCallback, errorCallback) {
        console.log("Calling startCamera with image.");
        cordova.exec(successCallback, errorCallback, 'RecognitionFacial', 'startCamera', []);
    }
};

module.exports = recognitionFacial;
