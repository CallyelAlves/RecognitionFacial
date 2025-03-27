var neurotechnology = {
    initialize: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initialize", []);
    },
    initializeMatchingClient: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeMatchingClient", []);
    },
    enrollFromBase64: function(personId, image, successCallback, errorCallback) {
        console.log("Calling enrollFromBase64 with personId:", personId);
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "enrollFromBase64", [personId, image]);
    },
    identifyBase64: function(image, successCallback, errorCallback) {
        console.log("Calling identifyBase64 with image.");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "identifyBase64", [image]);
    },
    startCamera: function(successCallback, errorCallback) {
        console.log("Calling startCamera with image.");
        cordova.exec(successCallback, errorCallback, 'Neurotechnology', 'startCamera', []);
    }
};

module.exports = neurotechnology;
