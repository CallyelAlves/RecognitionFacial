package com.cordova.neurotechnology.utils;

public interface Callback {
    void onSuccess(String message);
    void onFailure(String error);
}