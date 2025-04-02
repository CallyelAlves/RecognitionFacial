package com.cordova.neurotechnology.utils;

import org.json.JSONObject;
import com.neurotec.images.NImage;

public class NeurotechnologyServiceResluts {
    private AuthenticationError authenticationError;
    private String personId;
    private NImage enrolledImage;
    private JSONObject userData;

    public NeurotechnologyServiceResluts(){
        this.enrolledImage = null;
    }
    public AuthenticationError getAuthenticationError() {
        return authenticationError;
    }

    public void setAuthenticationError(AuthenticationError authenticationError) {
        this.authenticationError = authenticationError;
    }

    public String getPersonId() {
        return personId;
    }

    public NImage geEnroledImage() {return enrolledImage;}

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public void setEnroledImage(NImage image){ this.enrolledImage = image;}

    public JSONObject getUserData() {
        return userData;
    }

    public void setUserData(JSONObject userData) {
        this.userData = userData;
    }
}
