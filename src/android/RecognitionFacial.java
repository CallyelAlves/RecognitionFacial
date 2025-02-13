package com.app.recognition.matchingservice;

import com.app.facesample.service.MatchingService;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;

public class RecognitionFacial extends CordovaPlugin {
    private Context context; // Armazena o contexto

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.context = cordova.getActivity().getApplicationContext(); // Obtém o contexto
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("initialize")) {
            if (!MatchingService.isLicensesObtained(this.context)) {
                MatchingService.initializeLicense(callbackContext, this.context);
            }
            return true;
        }

        if (action.equals("initializeMatchingClient")) {
            MatchingService.initializeMatchingClient(callbackContext, this.context);
            return true;
        }

        if (action.equals("enrollFromBase64")) {
            try {
                String personId = args.getString(0);
                String base64Image = args.getString(1);
                
                boolean success = MatchingService.enrollFromBase64(personId, base64Image, callbackContext);

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

        if (action.equals("identifyBase64")) {
            try {
                String base64Image = args.getString(0);

                String[] success = MatchingService.IdentifyFace(base64Image, callbackContext);

                callbackContext.success(String.join(", ", success));
                return true;
            } catch (Exception e) {
                callbackContext.error("Error in identifyBase64: " + e.getMessage());
                return false;
            }
        }

        return false;
    }
}
