var neurotechnology = {
    initializeLicense: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeLicense", []);
    },
    initializeClient: function(successCallback, errorCallback) {
        console.log("Calling initialize");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "initializeClient", []);
    },
    enrollFromBase64: function(user, image, successCallback, errorCallback) {
        console.log("Calling enrollFromBase64 with user:", user);
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "enrollFromBase64", [user, image]);
    },
    identifyFace: function(image, successCallback, errorCallback) {
        console.log("Calling identifyBase64 with image.");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "identifyFace", [image]);
    },
    startCamera: function(successCallback, errorCallback) {
        console.log("Calling startCamera with image.");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "startCamera", []);
    },
    cleanDB: function(successCallback, errorCallback) {
        console.log("Calling cleanDB");
        cordova.exec(successCallback, errorCallback, "Neurotechnology", "cleanDB", []);
    }
};

module.exports = neurotechnology;
