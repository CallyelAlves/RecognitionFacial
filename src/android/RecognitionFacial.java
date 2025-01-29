package com.app.recognition.matchingservice;

import com.app.facesample.service.MatchingService;

// import com.neurotec.images.NImage;
// import com.neurotec.images.NImageFormat;
// import com.neurotec.biometrics.NSubject;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

public class RecognitionFacial extends CordovaPlugin {
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("initialize")) {
            MatchingService.initializeMatchingClient(callbackContext);
            callbackContext.success("Matching Client Initialized");
            return true;
        }

        if (action.equals("enrollFromBase64")) {
            try {
                callbackContext.success("enrollFromBase64 0");
                String personId = args.getString(0); // Primeiro argumento
                String base64Image = args.getString(1); // Segundo argumento
                
                boolean success = MatchingService.enrollFromBase64(personId, base64Image, callbackContext);
                callbackContext.success("enrollFromBase64 1");
                callbackContext.success(String.valueOf(success));
                if (success) {
                    callbackContext.success("Enrollment successful");
                } else {
                    callbackContext.error("Enrollment failed");
                }
                return true;
            } catch (Exception e) {
                callbackContext.error("Error in enrollFromBase64: " + e.getMessage());
                return false;
            }
        }

        // if (action.equals("enrollTemplate")) {
        //     try {
        //         String personId = args.getString(0);
        //         String imagePath = args.getString(1);
                
        //         // Carregue a imagem e crie o objeto NSubject
        //         NImage image = NImage.fromFile(imagePath);
        //         NSubject subject = new NSubject();
                
        //         AuthenticationError result = MatchingService.enrollTemplate(subject, personId, image);
        //         if (result == AuthenticationError.OK) {
        //             callbackContext.success("Enrollment successful");
        //         } else {
        //             callbackContext.error("Enrollment failed: " + result.toString());
        //         }
        //         return true;
        //     } catch (Exception e) {
        //         callbackContext.error("Error enrolling template: " + e.getMessage());
        //         return false;
        //     }
        // }

        // if (action.equals("identify")) {
        //     try {
        //         String imagePath = args.getString(0);
        //         NImage image = NImage.fromFile(imagePath);
        //         NSubject subject = new NSubject();
        //         subject.setTemplate(image);

        //         List<MatchingServiceResluts> results = MatchingService.identify(subject);
        //         JSONArray jsonResults = new JSONArray();
        //         for (MatchingServiceResluts result : results) {
        //             JSONObject obj = new JSONObject();
        //             obj.put("personId", result.getPersonId());
        //             // Adicione outros campos necessários
        //             jsonResults.put(obj);
        //         }
        //         callbackContext.success(jsonResults);
        //         return true;
        //     } catch (Exception e) {
        //         callbackContext.error("Error identifying: " + e.getMessage());
        //         return false;
        //     }
        // }

        return false;
    }
}
