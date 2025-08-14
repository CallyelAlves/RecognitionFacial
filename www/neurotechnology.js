var neurotechnology = {
    initializeLicense: function(licenca, successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeLicense", [licenca]);
    },
    initializeClient: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeClient", []);
    },
    initializeLicenseTrialMode: function(successCallback, errorCallback) {
        console.log("Calling initializeLicenseTrialMode");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeLicenseTrialMode", []);
    },
    isLicensesObtained: function(successCallback, errorCallback) {
        console.log("Calling isLicensesObtained");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "isLicensesObtained", []);
    },
    deactivateLicenses: function(licenca, successCallback, errorCallback) {
        console.log("Calling deactivateLicenses");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "deactivateLicenses", [licenca]);
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
    getListIds: function(successCallback, errorCallback) {
        console.log("Calling getListIds");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "getListIds", []);
    },
    deleteId: function(id, successCallback, errorCallback) {
        console.log("Calling deleteId");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "deleteId", [id]);
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
