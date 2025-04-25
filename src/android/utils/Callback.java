package com.cordova.neurotechnology.utils;
import org.apache.cordova.PluginResult;

public interface Callback {
    void onSuccess(String message);
    void onFailure(String error);
    void sendPluginResult(PluginResult result);
}