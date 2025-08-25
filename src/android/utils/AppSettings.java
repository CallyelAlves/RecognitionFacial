package com.cordova.neurotechnology.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import androidx.preference.PreferenceManager;

import br.com.nasajon.pontomobile.R;

public class AppSettings {
    public static int TYPE_ACTIVIY_ENROLL = 10;
    public static int TYPE_ACTIVIY_IDENTIFY = 11;

    public static String getCurrentCamera(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int id  = sharedPreferences.getInt("0", -1);
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] idList = manager.getCameraIdList();
            //Select Front camera
            if (id < 0) {
                for (int i = 0; i < idList.length; i++) {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(idList[i]);
                    int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        id = i;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putInt("0", id);
                        editor.apply();
                        break;
                    }
                }

                if (id < 0)
                    id = 0;
            }

            if (idList.length > id)
                return idList[id];

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void switchCamera(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int id = sharedPreferences.getInt("0", 0);

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            int numberOfCameras = manager.getCameraIdList().length;
            int newId = numberOfCameras > 0 ? (++id) % numberOfCameras : id;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt("0", newId );
            editor.commit();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    public static  int getImageFormat(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(String.valueOf(ImageFormat.JPEG));
    }


    public static boolean isMaskDetectionEnabled(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean("false", false);
    }

    public static int getFaceQualityTreshold(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getInt("40", 40);
    }

    public static int[] getCamResolution2(Context context) {
        int[] resolution = new int[2];
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int camera_id = sharedPreferences.getInt("0", 0);
        int image_format_id =  getImageFormat(context);
        //imageformate 35 = YUV_420_888
        //imageformat 256 = JPEG
        String key = "res_" + camera_id + "_" + image_format_id;
        resolution[0] = sharedPreferences.getInt(key + "_width", 0);
        resolution[1] = sharedPreferences.getInt(key + "_height", 0);

        return resolution;
    }

    public static void setCameraResolution2(Context context, int width, int height) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        int camera_id = sharedPreferences.getInt("0", 0);
        int image_format_id =  getImageFormat(context);
        //imageformate 35 = YUV_420_888
        //imageformat 256 = JPEG
        String key = "res_" + camera_id + "_" + image_format_id;
        editor.putInt(key + "_width", width);
        editor.putInt(key + "_height", height);
        editor.commit();
    }
}
