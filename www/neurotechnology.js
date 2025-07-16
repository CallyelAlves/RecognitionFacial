var neurotechnology = {
    initializeLicense: function(licenca, successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeLicense", [licenca]);
    },
    initializeClient: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeClient", []);
    },
    isLicensesObtained: function(successCallback, errorCallback) {
        console.log("Calling isLicensesObtained");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "isLicensesObtained", []);
    },
    release: function(successCallback, errorCallback) {
        console.log("Calling release");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "release", []);
    },
    enrollFromBase64: function(user, image, successCallback, errorCallback) {
        console.log("Calling enrollFromBase64 with user:", user);
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "enrollFromBase64", [user, image]);
    },
    identifyFace: function(image, successCallback, errorCallback) {
        console.log("Calling identifyBase64 with image.");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "identifyFace", [image]);
    },
    startCamera: function(tempoMinimoEstabilidadeMs, limiteMovimentoPermitido, proporcaoMinimaRosto, successCallback, errorCallback) {
        console.log("Calling startCamera with image.");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "startCamera", [tempoMinimoEstabilidadeMs, limiteMovimentoPermitido, proporcaoMinimaRosto]);
    },
    cleanDB: function(successCallback, errorCallback) {
        console.log("Calling cleanDB");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "cleanDB", []);
    },
    closeCamera: function(successCallback, errorCallback) {
        console.log("Calling close camera.");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "closeCamera", []);
    },
    subscribeToEvents: function(eventCallback, errorCallback) {
        console.log("Subscribing to plugin events");
        cordova.exec(eventCallback, errorCallback, "Neurotechnology", "subscribeToEvents", []);
    }
};

module.exports = neurotechnology;
