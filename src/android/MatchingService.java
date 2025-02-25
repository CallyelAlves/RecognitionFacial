package com.app.facesample.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.params.Face;
import android.os.Environment;
import android.os.AsyncTask;
import android.util.Log;
import android.util.SparseArray;
import android.util.Base64;

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
import com.neurotec.licensing.NLicenseManager;
import com.neurotec.licensing.gui.LicensingPreferencesFragment;

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
import com.app.facesample.util.MatchingServiceResluts;
import com.app.facesample.util.AuthenticationError;
import com.app.facesample.licensing.LicensingManager;
import com.app.facesample.licensing.LicensingState;

import com.app.recognition.matchingservice.utils.AutoFitTextureView;
import android.os.Handler;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCaptureSession;
import android.util.Size;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.media.ImageReader;
import java.util.Arrays;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.TotalCaptureResult;
import com.neurotec.biometrics.NLivenessMode;
import androidx.annotation.NonNull;
import java.util.Collections;
import com.app.facesample.helpers.FaceFrame;
import com.neurotec.util.concurrent.CompletionHandler;
import android.view.TextureView;
import java.util.Queue;
import java.util.LinkedList;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler.Callback;
import com.neurotec.images.NPixelFormat;

public class MatchingService implements LicensingManager.LicensingStateCallback {
    private static final String LOG_TAG = MatchingService.class.getSimpleName();
    private static NBiometricClient engine;
    private static final int ENROLLMENT_ACCURACY = 90;
    private static final int SKIP_BLUR_FRAMES = 10;

//    private static final Context contextLocal;

    private final Object captureLock = new Object();
    private List<FaceFrame> mImageQueue = new ArrayList<>();
    private NBiometricClient biometricClient;
    private CompletionHandler<NBiometricTask, NBiometricOperation> completionHandler;
    private CameraDevice cameraDevice;
    private int frameCount = 0;

    public static void initializeLicense(CallbackContext callbackContext, Context context) {
        NLicenseManager.setTrialMode(LicensingPreferencesFragment.isUseTrial(context));
        NCore.setContext(context);

        Log.e(LOG_TAG, "InitializationTask : Before");
        new InitializationTask(context).execute();
        Log.e(LOG_TAG, "InitializationTask : After");
        callbackContext.success("initializeLicense");
    }
    public static void initializeMatchingClient(CallbackContext callbackContext, Context context) {
        if (engine == null) {
            try {

                engine = new NBiometricClient();

//                engine.setDatabaseConnectionToSQLite(NCore.getContext().getFilesDir().getAbsolutePath() + System.getProperty("file.separator") + "BiometricsV50.db");
//                this.context = context;
                String path = context.getFilesDir().getAbsolutePath() + System.getProperty("file.separator") + "BiometricsV50.db";
                engine.setDatabaseConnectionToSQLite(path);
                NBiographicDataSchema nBiographicDataSchema = NBiographicDataSchema.parse("(Thumbnail blob)");
                engine.setCustomDataSchema(nBiographicDataSchema);
                engine.setUseDeviceManager(true);
                engine.setMatchingWithDetails(true);
                engine.setFacesCreateThumbnailImage(true);
                engine.setFacesThumbnailImageWidth(90);
                engine.setProperty("Faces.IcaoUnnaturalSkinToneThreshold", 10);
                engine.setProperty("Faces.IcaoSkinReflectionThreshold", 10);
                engine.setFacesTemplateSize(NTemplateSize.MEDIUM);
                // engine.setFacesDetectLiveness(true);
                // engine.setFacesLivenessMode(NLivenessMode.PASSIVE);
                engine.initialize();

                callbackContext.success("initializeMatchingClient");

            } catch (Exception ex) {
                callbackContext.error("initializeMatchingClient");
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
//            callbackContext.success("Function enrollFromBase64 1");
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

    public static String[] IdentifyFace(String base64Image, CallbackContext callbackContext){
        String[] mapPerson = null;
        NImage image = null;
        byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
        image = NImage.fromMemory(new NBuffer(decodedBytes));

        NFace face = null;
        NSubject extractSubject = new NSubject();
        if (image != null ) {

            face = engine.detectFaces(image);
            if (face.getObjects().size() > 0) {

                extractSubject.getFaces().add(face);

                List<MatchingServiceResluts> results = MatchingService.identify(extractSubject);

                if(results.size() > 0) {
                    if (results.size() == 1)
                        if (results.get(0).getAuthenticationError() != AuthenticationError.OK) {
                            if (results.get(0).getAuthenticationError() == AuthenticationError.NO_MATCHING) {
//                                Toast.makeText(FaceCaptureActivity.this, R.string.no_match_found, Toast.LENGTH_SHORT).show();
                            } else {
//                                Toast.makeText(FaceCaptureActivity.this, R.string.unidentified_error, Toast.LENGTH_SHORT).show();
                            }
                        }
                }

                mapPerson = prepareIdentifiedUsers(results);
            }
        }
        return mapPerson;

    }

    private static String[] prepareIdentifiedUsers(List<MatchingServiceResluts> results){
        ArrayList<String> lista = new ArrayList<String>();
        if(results.size() > 0){
            for(MatchingServiceResluts matchingServiceResluts : results){
                if(matchingServiceResluts.getAuthenticationError()!= null){
                    if(matchingServiceResluts.getAuthenticationError() == AuthenticationError.OK){
                        lista.add(matchingServiceResluts.getPersonId());
//                        imageList.add(matchingServiceResluts.geEnroledImage());
                    }
                }
            }
        }
        return lista.toArray(new String[0]);
    }

    @Override
    public void onLicensingStateChanged(LicensingState state) {
        switch (state) {
            case OBTAINING:
//                showProgress(R.string.msg_obtaining_licenses);
                Log.i(LOG_TAG, "Obtaining licenses");
                break;
            case OBTAINED:
//                hideProgress();
//                showToast(R.string.msg_licenses_obtained);
                Log.i(LOG_TAG, "Licenses were obtained");
                break;
            case NOT_OBTAINED:
//                hideProgress();
//                showToast(R.string.msg_licenses_not_obtained);
                Log.i(LOG_TAG, "Licenses were not obtained");
                break;
        }
    }

    final static class InitializationTask extends AsyncTask<Object, Integer, Boolean> {

        private static final int OBTAINING_LICENSE = 2;
        private static final int PREPARE_DATA_FILES = 3;
        private static final int Initializing_BIOMETRIC_CLIENT = 4;

        private boolean isLicenseObtained= false;
        private Context activityConext;

        public InitializationTask(Context context){
            activityConext = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            Log.e(LOG_TAG, "InitializationTask : doInBackground");
            publishProgress(OBTAINING_LICENSE);
            try {
                isLicenseObtained  = LicensingManager.getInstance().obtainComponents(this.activityConext);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(LOG_TAG, "InitializationTask : doInBackground Error: " + e.getMessage());
//                showError(e.getMessage(), false);
//                showToast(e.getMessage());

            }
            Log.d(LOG_TAG, isLicenseObtained ? "Licenses obtained" : "Cannot obtain licenses!");

            if (isLicenseObtained){
                Log.i(LOG_TAG, "Licenses were obtained 1");
            }else{
                Log.i(LOG_TAG, "Licenses were not obtained 1");
            }

//            publishProgress(Initializing_BIOMETRIC_CLIENT);
//            MatchingService.initializeMatchingClient();

            return true;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            switch (values[0]) {
                case PREPARE_DATA_FILES:
//                    showProgress(R.string.prepare_data_files);
                    Log.i(LOG_TAG, "Preparing data files...");
                    break;
                case OBTAINING_LICENSE:
//                    showProgress(R.string.msg_obtaining_licenses);
                    Log.i(LOG_TAG, "Obtaining licenses...");
                    break;
                case Initializing_BIOMETRIC_CLIENT:
//                    showProgress(R.string.initializing_biometric_client);
                    Log.i(LOG_TAG, "Initializing biometric client");
                    break;
            }
        }

//        @Override
//        protected void onPostExecute(Boolean result) {
//            super.onPostExecute(result);
//            hideProgress();
//            if(isLicenseObtained){
//                btnEnroll.setEnabled(true);
//                btnEnrollBase.setEnabled(true);
//                btnIdentify.setEnabled(true);
//                btnCleanDB.setEnabled(true);
//            }else{
//                btnEnroll.setEnabled(false);
//                btnEnrollBase.setEnabled(false);
//                btnIdentify.setEnabled(false);
//                btnCleanDB.setEnabled(false);
//            }
//
//        }
    }

    //    @Override
    public void onBackPressed() {
        Log.e(LOG_TAG, "before onBackPressed : License status -  " +  LicensingManager.getInstance().isLicensesObtained());
        LicensingManager.getInstance().release();
        Log.e(LOG_TAG, "after onBackPressed : License status -  " +  LicensingManager.getInstance().isLicensesObtained());
//        finish();
    }

    public void openCamera(Context context, AutoFitTextureView textureView, Handler backgroundHandler) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(outputSizes[0].getWidth(), outputSizes[0].getHeight());
            Surface surface = new Surface(texture);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        CaptureRequest.Builder previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewBuilder.addTarget(surface);

                        cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                try {
                                    session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                        }, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    cameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    cameraDevice.close();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startFrameProcessing(AutoFitTextureView textureView) {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {}

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                frameCount++;
                if (frameCount % 30 != 0) {
                    return; // Processa 1 frame a cada 30
                }

                Bitmap bitmap = textureView.getBitmap(textureView.getWidth(), textureView.getHeight());
                if (bitmap == null) {
                    Log.e("Camera", "Erro: Bitmap nulo.");
                    return;
                }

                try {
                    Bitmap rgbBitmap = bitmap.copy(Bitmap.Config.RGB_565, false);
                    if (rgbBitmap == null) {
                        Log.e("Camera", "Erro ao copiar Bitmap.");
                        return;
                    }

                    bitmap.recycle();
                    byte[] yuvData = convertBitmapToJPEG(rgbBitmap);
                    if (yuvData == null || yuvData.length == 0) {
                        Log.e("Camera", "Erro: Buffer YUV vazio.");
                        return;
                    }

                    int width = rgbBitmap.getWidth();
                    int height = rgbBitmap.getHeight();
                    FaceFrame faceFrame = new FaceFrame(yuvData, null, null, width, height, width, 0, 0, 0, 0, 0, 0);

                    synchronized (captureLock) {
                        mImageQueue.add(faceFrame);
                        captureLock.notify();
                    }
                } catch (Exception e) {
                    Log.e("Camera", "Erro ao converter Bitmap", e);
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
            }
        });
    }

    public void processCameraFrames(CallbackContext callback) {
        new Thread(() -> {
            while (true) {
                if (engine == null) {
                    Log.e(LOG_TAG, "Erro: engine não foi inicializado!");
                    return;
                }

                FaceFrame frame;
                synchronized (captureLock) {
                    while (mImageQueue.isEmpty()) {
                        try {
                            captureLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                engine.setFacesDetectLiveness(true);
                engine.setFacesLivenessMode(NLivenessMode.PASSIVE);
                NImage image = null;

                if (!mImageQueue.isEmpty()) {
                    FaceFrame data = mImageQueue.remove(0);
                    if (data == null) {
                        Log.e(LOG_TAG, "Erro: FaceFrame nulo.");
                        continue;
                    }

                    if (data.getBuffer1() == null || data.getBuffer1().length == 0) {
                        Log.e(LOG_TAG, "Erro: Buffer de imagem vazio.");
                        continue;
                    }

                    NSubject subject = new NSubject();
                    NFace nFace = new NFace();
                    try {
                        if (data.getBuffer2() != null && data.getBuffer3() != null) {
                            image = NImage.create(NPixelFormat.RGB_8U, data.getWidth(), data.getHeight(), data.getStride());
                            image.copyFromYCbCrData(new NBuffer(data.getBuffer1()), data.getRowStride1(), data.getPixelStride1(),
                                    new NBuffer(data.getBuffer2()), data.getRowStride2(), data.getPixelStride2(),
                                    new NBuffer(data.getBuffer3()), data.getRowStride3(), data.getPixelStride3());
                        } else {
                            image = NImage.fromMemory(new NBuffer(data.getBuffer1()), NImageFormat.getJPEG());
                        }
                    } catch (IllegalArgumentException ex) {
                        Log.e(LOG_TAG, "Formato de imagem inválido. Ignorando o frame!", ex);
                    }

                    if (image != null) {
                        nFace = engine.detectFaces(image);
                        subject.getFaces().add(nFace);

                        NBiometricTask task = engine.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
                        engine.performTask(task);
                        Log.d(LOG_TAG, "task.getStatus(): " + task.getStatus());
                        Log.d(LOG_TAG, String.valueOf(subject.getFaces().isEmpty()));
                        if (task.getStatus() == NBiometricStatus.OK) {
                            if (!subject.getFaces().isEmpty()) {
                                NFace detectedFace = subject.getFaces().get(0);
                                if (detectedFace.getObjects().size() > 0) {
                                    callback.success("Liveness detectado e template criado com sucesso.");
                                } else {
                                    Log.e(LOG_TAG, "Nenhum objeto facial detectado.");
                                    callback.error("Falha: Nenhum objeto facial detectado.");
                                }
                            } else {
                                Log.e(LOG_TAG, "Nenhuma face detectada no frame.");
                                callback.error("Falha: Nenhuma face detectada.");
                            }
                        } else {
                            Log.e(LOG_TAG, "Erro no reconhecimento facial. Status: " + task.getStatus());
                            callback.error("Falha na detecção de face ou liveness. Status: " + task.getStatus());
                        }
                    }
                }
            }
        }).start();
    }


    public static boolean checkLiveness(NBiometricClient biometricClient, NSubject subject) {
        try {
            biometricClient.setFacesDetectLiveness(true);
            biometricClient.setFacesLivenessMode(NLivenessMode.PASSIVE);

            NBiometricTask task = biometricClient.createTask(EnumSet.of(NBiometricOperation.ASSESS_QUALITY), subject);
            biometricClient.performTask(task);

            return task.getStatus() == NBiometricStatus.OK && subject.getFaces().get(0).getObjects().size() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void setBiometricClient(NBiometricClient biometricClient) {
        this.biometricClient = biometricClient;
    }

    public void setCompletionHandler(CompletionHandler<NBiometricTask, NBiometricOperation> completionHandler) {
        this.completionHandler = completionHandler;
    }

    public NBiometricClient getBiometricClient() {
        return biometricClient;
    }

    public interface Callback {
        void onSuccess(String message);

        void onFailure(String error);
    }

    private byte[] convertBitmapToJPEG(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

}