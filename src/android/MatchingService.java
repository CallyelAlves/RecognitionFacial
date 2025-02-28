package com.app.facesample.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

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
import com.neurotec.biometrics.NLivenessMode;
import com.neurotec.images.NPixelFormat;
import com.neurotec.util.concurrent.CompletionHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.apache.cordova.CallbackContext;

import com.app.facesample.util.MatchingServiceResluts;
import com.app.facesample.util.AuthenticationError;
import com.app.facesample.licensing.LicensingManager;
import com.app.facesample.licensing.LicensingState;
import br.com.nasajon.pontomobile.R;
import com.app.recognition.matchingservice.utils.AutoFitTextureView;

import android.os.Handler;
import android.os.HandlerThread;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraCaptureSession;
import android.util.Size;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.View;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import java.util.Collections;
import com.app.facesample.helpers.FaceFrame;
import android.view.TextureView;
import android.app.Activity;
import android.view.ViewGroup;
import android.webkit.WebView;

public class MatchingService implements LicensingManager.LicensingStateCallback {

    private static final String LOG_TAG = MatchingService.class.getSimpleName();
    private static NBiometricClient engine;
    private static final int ENROLLMENT_ACCURACY = 90;

    private final Object captureLock = new Object();
    private List<FaceFrame> mImageQueue = new ArrayList<>();
    private NBiometricClient biometricClient;
    private CompletionHandler<NBiometricTask, NBiometricOperation> completionHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private int frameCount = 0;
    private volatile boolean isProcessingFrames = true;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

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
                String path = context.getFilesDir().getAbsolutePath()
                        + System.getProperty("file.separator") + "BiometricsV50.db";
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
                engine.initialize();

                callbackContext.success("initializeMatchingClient");
            } catch (Exception ex) {
                callbackContext.error("initializeMatchingClient");
                Log.e(LOG_TAG, "Failed initialization", ex);
            }
        }
    }

    public static void reset() {
        engine.clear();
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

            return taskEnroll.getStatus() == NBiometricStatus.OK
                    ? AuthenticationError.OK
                    : AuthenticationError.ENROLLMENT_ERROR;
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Failed to enroll", ex);
            return AuthenticationError.EXTRACTION_ERROR;
        }
    }

    public static List<MatchingServiceResluts> identify(NSubject subject) {
        List<MatchingServiceResluts> resultsList = new ArrayList<>();

        if (subject == null) {
            MatchingServiceResluts result = new MatchingServiceResluts();
            result.setAuthenticationError(AuthenticationError.EXTRACTION_ERROR);
            resultsList.add(result);
            return resultsList;
        }

        try {
            NBiometricTask taskIdentify = engine.createTask(EnumSet.of(NBiometricOperation.IDENTIFY), subject);
            engine.performTask(taskIdentify);

            if (taskIdentify.getStatus() == NBiometricStatus.OK) {
                NSubject.MatchingResultCollection details = subject.getMatchingResults();
                for (NMatchingResult matchingResult : details) {
                    String id = matchingResult.getId();
                    if (matchingResult.getScore() > ENROLLMENT_ACCURACY) {
                        MatchingServiceResluts res = new MatchingServiceResluts();
                        res.setAuthenticationError(AuthenticationError.OK);
                        String[] names = id.split("_");
                        res.setPersonId(names.length > 0 ? names[0] : id);

                        NSubject sub = new NSubject();
                        sub.setId(matchingResult.getId());
                        NBiometricStatus status = engine.get(sub);

                        if (status == NBiometricStatus.OK && sub.getProperties().containsKey("Thumbnail")) {
                            NBuffer nBuffer = (NBuffer) sub.getProperties().get("Thumbnail");
                            NImage img = NImage.fromMemory(nBuffer);
                            res.setEnroledImage(img);
                            resultsList.add(res);
                        }
                    }
                }
            } else {
                MatchingServiceResluts res = new MatchingServiceResluts();
                Log.e(LOG_TAG, "Identification failed: " + taskIdentify.getStatus());
                if (taskIdentify.getStatus() == NBiometricStatus.MATCH_NOT_FOUND) {
                    Log.e(LOG_TAG, "Matcher status:" + taskIdentify.getStatus());
                    res.setAuthenticationError(AuthenticationError.NO_MATCHING);
                    resultsList.add(res);
                } else {
                    res.setAuthenticationError(AuthenticationError.IDENTIFICATION_ERROR);
                }
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception on matching", ex);
            resultsList.clear();
            MatchingServiceResluts res = new MatchingServiceResluts();
            res.setAuthenticationError(AuthenticationError.IDENTIFICATION_ERROR);
            resultsList.add(res);
        }
        return resultsList;
    }

    public static boolean enrollFromBase64(String personId, String base64Image, CallbackContext callbackContext) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            NImage image = NImage.fromMemory(new NBuffer(decodedBytes));

            if (image == null) {
                Log.e(LOG_TAG, "Invalid image provided.");
                return false;
            }
            callbackContext.success("Function enrollFromBase64 2");
            NSubject extractSubject = new NSubject();
            NFace face = engine.detectFaces(image);
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
        String downloadsPath = getDownloadsPath();
        File imagesDir = new File(downloadsPath, "Imagens");
        File[] files = imagesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String imagePath = file.getAbsolutePath();
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                    byte[] template = getBytesFromBitmap(bitmap);

                    String personId = file.getName();
                    int lastDotIndex = personId.lastIndexOf('.');
                    if (lastDotIndex > 0) {
                        personId = personId.substring(0, lastDotIndex);
                    }

                    NImage image = NImage.fromMemory(new NBuffer(template));
                    NSubject extractSubject = new NSubject();
                    if (image != null) {
                        NFace face = engine.detectFaces(image);
                        if (face.getObjects().size() > 0) {
                            extractSubject.getFaces().add(face);
                            enrollTemplate(extractSubject, personId, image);
                        }
                    }
                }
            }
        }
    }

    public static String getDownloadsPath() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return (downloadsDir != null && downloadsDir.exists())
                ? downloadsDir.getAbsolutePath() + System.getProperty("file.separator")
                : null;
    }

    public static byte[] getBytesFromBitmap(Bitmap bitmap) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public static String[] IdentifyFace(String base64Image, CallbackContext callbackContext) {
        String[] identifiedUsers = null;
        byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
        NImage image = NImage.fromMemory(new NBuffer(decodedBytes));
        NSubject extractSubject = new NSubject();
        if (image != null) {
            NFace face = engine.detectFaces(image);
            if (face.getObjects().size() > 0) {
                extractSubject.getFaces().add(face);
                List<MatchingServiceResluts> results = MatchingService.identify(extractSubject);
                if (!results.isEmpty() && results.size() == 1
                        && results.get(0).getAuthenticationError() != AuthenticationError.OK) {
                    // Tratamento de erro, se necessário
                }
                identifiedUsers = prepareIdentifiedUsers(results);
            }
        }
        return identifiedUsers;
    }

    private static String[] prepareIdentifiedUsers(List<MatchingServiceResluts> results) {
        ArrayList<String> list = new ArrayList<>();
        for (MatchingServiceResluts res : results) {
            if (res.getAuthenticationError() != null && res.getAuthenticationError() == AuthenticationError.OK) {
                list.add(res.getPersonId());
            }
        }
        return list.toArray(new String[0]);
    }

    @Override
    public void onLicensingStateChanged(LicensingState state) {
        switch (state) {
            case OBTAINING:
                Log.i(LOG_TAG, "Obtaining licenses");
                break;
            case OBTAINED:
                Log.i(LOG_TAG, "Licenses were obtained");
                break;
            case NOT_OBTAINED:
                Log.i(LOG_TAG, "Licenses were not obtained");
                break;
        }
    }

    final static class InitializationTask extends AsyncTask<Object, Integer, Boolean> {
        private static final int OBTAINING_LICENSE = 2;
        private static final int PREPARE_DATA_FILES = 3;
        private static final int INITIALIZING_BIOMETRIC_CLIENT = 4;

        private boolean isLicenseObtained = false;
        private Context activityContext;

        public InitializationTask(Context context) {
            activityContext = context;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            Log.e(LOG_TAG, "InitializationTask : doInBackground");
            publishProgress(OBTAINING_LICENSE);
            try {
                isLicenseObtained = LicensingManager.getInstance().obtainComponents(activityContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, "InitializationTask : doInBackground Error: " + e.getMessage(), e);
            }
            Log.d(LOG_TAG, isLicenseObtained ? "Licenses obtained" : "Cannot obtain licenses!");
            Log.i(LOG_TAG, isLicenseObtained ? "Licenses were obtained 1" : "Licenses were not obtained 1");
            return true;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            switch (values[0]) {
                case PREPARE_DATA_FILES:
                    Log.i(LOG_TAG, "Preparing data files...");
                    break;
                case OBTAINING_LICENSE:
                    Log.i(LOG_TAG, "Obtaining licenses...");
                    break;
                case INITIALIZING_BIOMETRIC_CLIENT:
                    Log.i(LOG_TAG, "Initializing biometric client");
                    break;
            }
        }
    }

    public void onBackPressed() {
        Log.e(LOG_TAG, "before onBackPressed : License status - "
                + LicensingManager.getInstance().isLicensesObtained());
        LicensingManager.getInstance().release();
        Log.e(LOG_TAG, "after onBackPressed : License status - "
                + LicensingManager.getInstance().isLicensesObtained());
    }

    public void openCamera(Context context, AutoFitTextureView textureView, Handler backgroundHandler) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture != null && outputSizes != null && outputSizes.length > 0) {
                texture.setDefaultBufferSize(outputSizes[0].getWidth(), outputSizes[0].getHeight());
            }
            Surface surface = new Surface(texture);

            engine.setFacesDetectLiveness(true);
            engine.setFacesLivenessMode(NLivenessMode.PASSIVE);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
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
                                captureSession = session;
                                try {
                                    session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                                } catch (CameraAccessException e) {
                                    Log.e(LOG_TAG, "Error in setRepeatingRequest", e);
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Log.e(LOG_TAG, "CameraCaptureSession configuration failed");
                            }
                        }, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(LOG_TAG, "Error in creating preview request", e);
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Log.d("Camera", "onDisconnected chamado");
                    closeCamera();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    closeCamera();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, "Error in openCamera", e);
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
                Log.d("Camera", "onSurfaceTextureDestroyed chamado");
                closeCamera();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                frameCount++;
                if (frameCount % 30 != 0) {
                    return;
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

    public void processCameraFrames(CallbackContext callback, Context context, Activity activity, AutoFitTextureView textureView, WebView webView) {
        new Thread(() -> {
            while (isProcessingFrames) {
                if (engine == null) {
                    Log.e(LOG_TAG, "Erro: engine não foi inicializado!");
                    return;
                }

                synchronized (captureLock) {
                    while (mImageQueue.isEmpty()) {
                        try {
                            captureLock.wait();
                        } catch (InterruptedException e) {
                            Log.e(LOG_TAG, "Waiting interrupted", e);
                        }
                    }
                }

                if (!isProcessingFrames) {
                    Log.d("Camera", "Processamento de frames interrompido.");
                    return;
                }
                NImage image = null;

                if (!mImageQueue.isEmpty()) {
                    FaceFrame data = mImageQueue.remove(0);
                    if (data == null || data.getBuffer1() == null || data.getBuffer1().length == 0) {
                        Log.e(LOG_TAG, "Erro: Buffer de imagem inválido.");
                        continue;
                    }

                    NSubject subject = new NSubject();
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
                        NFace nFace = engine.detectFaces(image);
                        subject.getFaces().add(nFace);

                        NBiometricTask task = engine.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
                        engine.performTask(task);

                        if (task.getStatus() == NBiometricStatus.OK && !subject.getFaces().isEmpty()) {
                            NFace detectedFace = subject.getFaces().get(0);
                            if (detectedFace.getObjects().size() > 0) {
                                String base64Image = convertNImageToBase64(image);
                                isProcessingFrames = false;

                                callback.success(base64Image);
                                closeCamera();
                                activity.runOnUiThread(() -> {
                                    try {
                                        if (textureView != null) {
                                            textureView.setVisibility(View.GONE);
                                        }
                                        stopBackgroundThread();
                                        if (textureView != null) {
                                            textureView.setSurfaceTextureListener(null);
                                            SurfaceTexture surface = textureView.getSurfaceTexture();
                                            if (surface != null) {
                                                surface.release();
                                            }
                                            ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
                                            rootView.removeView(textureView);
                                        }
                                    } catch (Exception e) {
                                        Log.e("CameraError", "Erro ao fechar câmera", e);
                                    }
                                });
                                return;
                            } else {
                                Log.e(LOG_TAG, "Nenhum objeto facial detectado.");
                            }
                        } else {
                            Log.e(LOG_TAG, "Erro no reconhecimento facial. Status: " + task.getStatus());
                        }
                    }
                }
            }
        }).start();
    }

    private void stopBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundHandler.getLooper().quitSafely();
            backgroundHandler = null;
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
            } catch (InterruptedException e) {
                Log.e("MatchingService", "Erro ao parar a thread de fundo", e);
            }
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

    public void closeCamera() {
        try {
            Log.d("Camera", "Fechando câmera...");
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
                captureSession.close();
                captureSession = null;
                Log.d("Camera", "Capture session fechada");
            }
        } catch (CameraAccessException e) {
            Log.e("Camera", "Erro ao fechar a capture session", e);
        }

        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
                Log.d("Camera", "Camera device fechada");
            }
        } catch (Exception e) {
            Log.e("Camera", "Erro ao fechar o camera device", e);
        }

        try {
            if (backgroundHandler != null) {
                backgroundHandler.getLooper().quitSafely();
                backgroundHandler = null;
            }
        } catch (Exception e) {
            Log.e("Camera", "Erro ao parar o background handler", e);
        }
    }

    private byte[] convertBitmapToJPEG(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }

    private String convertNImageToBase64(NImage image) {
        try {
            NBuffer buffer = image.save(NImageFormat.getJPEG());
            byte[] imageBytes = buffer.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Erro ao converter imagem para base64", e);
            return null;
        }
    }
}
