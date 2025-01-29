package com.app.facesample.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.params.Face;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
import android.util.Base64;

import com.app.facesample.util.MatchingServiceResluts;
import com.app.facesample.util.AuthenticationError;
import com.neurotec.biometrics.NBiographicDataSchema;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NMatchingResult;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageFormat;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NCore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.apache.cordova.CallbackContext;

public class MatchingService {
    private static final String LOG_TAG = MatchingService.class.getSimpleName();
    private static NBiometricClient engine;
    private static final int ENROLLMENT_ACCURACY = 90;
    private static final int SKIP_BLUR_FRAMES = 10;

    public static void initializeMatchingClient(CallbackContext callbackContext) {
        if (engine == null) {
            try {
                // callbackContext.success("initializeMatchingClient 1");
                engine = new NBiometricClient();
                callbackContext.success("initializeMatchingClient 2");

                engine.setDatabaseConnectionToSQLite(NCore.getContext().getFilesDir().getAbsolutePath() + System.getProperty("file.separator") + "BiometricsV50.db");
                NBiographicDataSchema nBiographicDataSchema = NBiographicDataSchema.parse("(Thumbnail blob)");
                engine.setCustomDataSchema(nBiographicDataSchema);
                callbackContext.success("Matching Client Initialized");
                engine.setUseDeviceManager(true);
                engine.setMatchingWithDetails(true);
                engine.setFacesCreateThumbnailImage(true);
                engine.setFacesThumbnailImageWidth(90);
                engine.setProperty("Faces.IcaoUnnaturalSkinToneThreshold", 10);
                engine.setProperty("Faces.IcaoSkinReflectionThreshold", 10);
                engine.setFacesTemplateSize(NTemplateSize.MEDIUM);
                engine.initialize();

            } catch (Exception ex) {
                callbackContext.error("Error in initializeMatchingClient");
                Log.e(LOG_TAG, "Failed initialization", ex);
            }
        }
        //engine.clear();
    }

    public static void reset(){
        engine.clear();
        //map.clear();
    }

    public static AuthenticationError enrollTemplate(NSubject subject, String personId, NImage image) {
        try {
            String uniqueID = personId + "_" + UUID.randomUUID().toString();
            subject.setId(uniqueID);

            NImageFormat format = image.getInfo().getFormat();
            if (format == null || !format.isCanWrite()) {
                format = NImageFormat.getPNG();
            }

            subject.getProperties().add("Thumbnail", image.save(format));

            NBiometricTask taskEnroll = engine.createTask(EnumSet.of(NBiometricOperation.ENROLL), subject);
            engine.performTask(taskEnroll);

            if(taskEnroll.getStatus() == NBiometricStatus.OK){

                return AuthenticationError.OK;
            }else{
                return AuthenticationError.ENROLLMENT_ERROR;
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Failed to enroll", ex);
            return AuthenticationError.EXTRACTION_ERROR;
        }
    }

    public static List<MatchingServiceResluts> identify(NSubject subject) {
        List<MatchingServiceResluts> matchingServiceReslutsList = new ArrayList<MatchingServiceResluts>();

        MatchingServiceResluts matchingServiceResluts;

        if (subject == null) {
            matchingServiceResluts = new MatchingServiceResluts();
            matchingServiceResluts.setAuthenticationError(AuthenticationError.EXTRACTION_ERROR);
            matchingServiceReslutsList.add(matchingServiceResluts);
            return matchingServiceReslutsList;
        }

        try {

            NBiometricTask taskIdentify = engine.createTask(EnumSet.of(NBiometricOperation.IDENTIFY), subject);
            engine.performTask(taskIdentify);

            if (taskIdentify.getStatus() == NBiometricStatus.OK) {
                NSubject.MatchingResultCollection details = subject.getMatchingResults();
                for (NMatchingResult matchingResult : details) {

                    String id = matchingResult.getId();
                    if (matchingResult.getScore() > ENROLLMENT_ACCURACY) {
                        matchingServiceResluts = new MatchingServiceResluts();
                        matchingServiceResluts.setAuthenticationError(AuthenticationError.OK);
                        String[] names = id.split("_");
                        if(names.length >0){
                            matchingServiceResluts.setPersonId(names[0]);
                        }else{
                            matchingServiceResluts.setPersonId(id);
                        }


                        NSubject sub = new NSubject();
                        sub.setId(matchingResult.getId());
                        NBiometricStatus nBiometricStatus = engine.get(sub);

                        if(nBiometricStatus == NBiometricStatus.OK){
                            if(sub.getProperties().containsKey("Thumbnail")){
                                NBuffer nBuffer = (NBuffer) sub.getProperties().get("Thumbnail");
                                NImage image = NImage.fromMemory(nBuffer);
                                matchingServiceResluts.setEnroledImage(image);
                                matchingServiceReslutsList.add(matchingServiceResluts);
                            }
                        }
                    }
                }

            } else {
                matchingServiceResluts = new MatchingServiceResluts();
                Log.e(LOG_TAG, "Identification failed: " + taskIdentify.getStatus());
                if (taskIdentify.getStatus() == NBiometricStatus.MATCH_NOT_FOUND) {
                    Log.e(LOG_TAG, "Matcher status:" + taskIdentify.getStatus());

                    matchingServiceResluts.setAuthenticationError(AuthenticationError.NO_MATCHING);
                    matchingServiceReslutsList.add(matchingServiceResluts);
                }else{
                    matchingServiceResluts.setAuthenticationError(AuthenticationError.IDENTIFICATION_ERROR);
                }

            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception on matching", ex);
            matchingServiceReslutsList.clear();

            matchingServiceResluts = new MatchingServiceResluts();
            matchingServiceResluts.setAuthenticationError(AuthenticationError.IDENTIFICATION_ERROR);
            matchingServiceReslutsList.add(matchingServiceResluts);
        }
        return matchingServiceReslutsList;
    }

    public static boolean enrollFromBase64(String personId, String base64Image, CallbackContext callbackContext) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            
            // Converte o array de bytes em um objeto NImage
            NImage image = NImage.fromMemory(new NBuffer(decodedBytes));
            
            // Verifica se a imagem é válida
            if (image == null) {
                Log.e(LOG_TAG, "Invalid image provided.");
                return false;
            }
            callbackContext.success("Function enrollFromBase64 1");
            // Cria um sujeito (NSubject) para processar a imagem
            NSubject extractSubject = new NSubject();
            NFace face = engine.detectFaces(image);
            callbackContext.success("Function enrollFromBase64 2");
            if (face != null && face.getObjects().size() > 0) {
                extractSubject.getFaces().add(face);
                AuthenticationError result = enrollTemplate(extractSubject, personId, image);
                callbackContext.success("Function enrollFromBase64 3");
                if (result == AuthenticationError.OK) {
                    Log.i(LOG_TAG, "Enrollment successful for personId: " + personId);
                    return true;
                } else {
                    Log.e(LOG_TAG, "Enrollment failed with error: " + result);
                    return false;
                }
            } else {
                Log.e(LOG_TAG, "No faces detected in the provided image.");
                return false;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error enrolling from Base64: ", e);
            return false;
        }
    }


    public static void populateTemplates() {
        //List<String> imagePaths, MatchingService matchingService
        String downloadsPath = getDownloadsPath(); // 'this' refere-se à sua Activity ou contexto atual

        File imagesDir = new File(downloadsPath, "Imagens");
        File[] files = imagesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String imagePath = file.getAbsolutePath();
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                    byte[] template = getBytesFromBitmap(bitmap);

                    String personId = file.getName();
                    // Remove a extensão do arquivo, se houver
                    int lastDotIndex = personId.lastIndexOf('.');
                    if (lastDotIndex > 0) {
                        personId = personId.substring(0, lastDotIndex);
                    }

                    NImage image = null;
                    image = NImage.fromMemory(new NBuffer(template));

                    NFace face = null;
                    NSubject extractSubject = new NSubject();
                    if (image != null ) {

                        face = engine.detectFaces(image);
                        if (face.getObjects().size() > 0) {

                            extractSubject.getFaces().add(face);
                            enrollTemplate(extractSubject, personId, image); // Cadastra no banco
                        }
                    }
                }
            }
        }

    }

    public static String getDownloadsPath() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null && downloadsDir.exists()) {
            return downloadsDir.getAbsolutePath() + System.getProperty("file.separator");
        } else {
            return null;
        }
    }

    public static byte[] getBytesFromBitmap(Bitmap bitmap) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            // Lidar com a exceção
            return null;
        }
    }

}