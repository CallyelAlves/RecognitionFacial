var neurotechnology = {
    initializeLicense: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeLicense", []);
    },
    initializeClient: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeClient", []);
    },
    carregarLicenca: function(licenca, successCallback, errorCallback) {
        console.log("Calling carregarLicenca", licenca);
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "carregarLicenca", [licenca]);
    },
    desativarLicenca: function(successCallback, errorCallback) {
        console.log("Calling desativarLicenca");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "desativarLicenca", []);
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
