package com.app.facesample.util;

import com.neurotec.images.NImage;

public class MatchingServiceResluts {
    private AuthenticationError authenticationError;
    private String personId;
    private NImage enrolledImage;

    public MatchingServiceResluts(){
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
}
