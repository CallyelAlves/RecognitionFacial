package com.cordova.neurotechnology.utils;

/**
 * *  Created by rajeevan on 10/21/2016.
 **/

public class CameraResolution {
    private int width;
    private int height;
    private String resolutionText;
    private String aspectRatio;

    private int greatestCommonFactor(int width, int height) {
        return (height == 0) ? width : greatestCommonFactor(height, width % height);
    }

    public CameraResolution(int width, int height) {
        this.width = width;
        this.height = height;
        this.resolutionText = Math.max(width, height) + " x " + Math.min(width, height);
        int gcd = greatestCommonFactor(width, height);
        aspectRatio = gcd == 0 ? "" : Math.max(width, height) / gcd + ":" + Math.min(width, height) / gcd;

    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CameraResolution) {
            CameraResolution obj = (CameraResolution) o;
            return width * height == obj.getHeight() * obj.getWidth();
        } else {
            return false;
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getResolutionText() {
        return resolutionText;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public int getResolution() {
        return width * height;
    }
}
