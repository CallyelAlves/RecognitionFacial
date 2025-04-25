package com.cordova.neurotechnology;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.Collections;

import com.neurotec.biometrics.NBiographicDataSchema;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NLivenessMode;
import com.neurotec.biometrics.NMatchingResult;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageFormat;
import com.neurotec.images.NPixelFormat;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NCore;
import com.neurotec.licensing.NLicenseManager;
import com.neurotec.licensing.gui.LicensingPreferencesFragment;
import com.neurotec.util.concurrent.CompletionHandler;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;

import com.cordova.neurotechnology.utils.Callback;
import com.cordova.neurotechnology.utils.AuthenticationError;
import com.cordova.neurotechnology.utils.AutoFitTextureView;
import com.cordova.neurotechnology.utils.FaceOverlayView;
import com.cordova.neurotechnology.utils.NeurotechnologyServiceResluts;
import com.cordova.neurotechnology.helpers.FaceFrame;
import com.cordova.neurotechnology.licensing.LicensingManager;
import com.cordova.neurotechnology.licensing.LicensingState;

import br.com.nasajon.pontomobile.R;

public class NeurotechnologyService implements LicensingManager.LicensingStateCallback {

    private static final String LOG_TAG = NeurotechnologyService.class.getSimpleName();
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

    public static void initializeLicense(Context context) {
        NLicenseManager.setTrialMode(LicensingPreferencesFragment.isUseTrial(context));
        NCore.setContext(context);
        new InitializationTask(context).execute();
    }

    public static void initializeClient(Context context) {
        if (engine == null) {
            engine = new NBiometricClient();
            String path = context.getFilesDir().getAbsolutePath()
                    + System.getProperty("file.separator") + "BiometricsV50.db";
            engine.setDatabaseConnectionToSQLite(path);
            NBiographicDataSchema nBiographicDataSchema = NBiographicDataSchema.parse("(Thumbnail blob, UserData string)");
            engine.setCustomDataSchema(nBiographicDataSchema);
            engine.setUseDeviceManager(true);
            engine.setMatchingWithDetails(true);
            engine.setFacesCreateThumbnailImage(true);
            engine.setFacesThumbnailImageWidth(90);
            engine.setProperty("Faces.IcaoUnnaturalSkinToneThreshold", 10);
            engine.setProperty("Faces.IcaoSkinReflectionThreshold", 10);
            engine.setFacesTemplateSize(NTemplateSize.MEDIUM);
            engine.initialize();
        }
    }

    public static void cleanDB(){
        engine.clear();
    }

    public static AuthenticationError enrollTemplate(NSubject subject, JSONObject userData, NImage image) {
        try {
            String userPassword = userData.getString("nome");
            String uniqueID = userPassword + "_" + UUID.randomUUID().toString();
            subject.setId(uniqueID);

            NImageFormat format = image.getInfo().getFormat();
            if (format == null || !format.isCanWrite()) {
                format = NImageFormat.getPNG();
            }
            subject.getProperties().add("UserData", userData.toString());
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

    public static List<NeurotechnologyServiceResluts> identify(NSubject subject) {
        List<NeurotechnologyServiceResluts> resultsList = new ArrayList<>();

        if (subject == null) {
            NeurotechnologyServiceResluts result = new NeurotechnologyServiceResluts();
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
                        NeurotechnologyServiceResluts res = new NeurotechnologyServiceResluts();
                        res.setAuthenticationError(AuthenticationError.OK);
                        String[] names = id.split("_");
                        res.setPersonId(names.length > 0 ? names[0] : id);

                        NSubject sub = new NSubject();
                        sub.setId(id);
                        NBiometricStatus status = engine.get(sub);

                        if (status == NBiometricStatus.OK) {
                            if (sub.getProperties().containsKey("UserData")) {
                                String userDataJson = (String) sub.getProperties().get("UserData");
                                res.setUserData(new JSONObject(userDataJson));
                            }

                            if (sub.getProperties().containsKey("Thumbnail")) {
                                NBuffer nBuffer = (NBuffer) sub.getProperties().get("Thumbnail");
                                NImage img = NImage.fromMemory(nBuffer);
                                res.setEnroledImage(img);
                            }

                            resultsList.add(res);
                        }
                    }
                }
            } else {
                NeurotechnologyServiceResluts res = new NeurotechnologyServiceResluts();
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
            NeurotechnologyServiceResluts res = new NeurotechnologyServiceResluts();
            res.setAuthenticationError(AuthenticationError.IDENTIFICATION_ERROR);
            resultsList.add(res);
        }
        return resultsList;
    }

    public static boolean enrollFromBase64(JSONObject userData, String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            NImage image = NImage.fromMemory(new NBuffer(decodedBytes));

            if (image == null) {
                Log.e(LOG_TAG, "Invalid image provided.");
                return false;
            }

            NSubject extractSubject = new NSubject();
            NFace face = engine.detectFaces(image);
            if (face != null && face.getObjects().size() > 0) {
                extractSubject.getFaces().add(face);
                AuthenticationError result = enrollTemplate(extractSubject, userData, image);

                if (result == AuthenticationError.OK) {
                    Log.i(LOG_TAG, "Enrollment successful");
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

    public static String[] identifyFace(String base64Image) {
        String[] identifiedUsers = null;
        byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
        NImage image = NImage.fromMemory(new NBuffer(decodedBytes));
        
        if (image != null) {
            NFace face = engine.detectFaces(image);
            if (face.getObjects().size() > 0) {
                NSubject extractSubject = new NSubject();
                extractSubject.getFaces().add(face);
                List<NeurotechnologyServiceResluts> results = NeurotechnologyService.identify(extractSubject);

                if (!results.isEmpty() && results.size() == 1
                        && results.get(0).getAuthenticationError() != AuthenticationError.OK) {  }
                identifiedUsers = prepareIdentifiedUsers(results);
            }
        }
        return identifiedUsers;
    }

    private static String[] prepareIdentifiedUsers(List<NeurotechnologyServiceResluts> results) {
        ArrayList<String> list = new ArrayList<>();
        for (NeurotechnologyServiceResluts res : results) {
            if (res.getAuthenticationError() != null && res.getAuthenticationError() == AuthenticationError.OK) {
                list.add(res.getUserData().toString());
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

    public void startFrameProcessing(AutoFitTextureView textureView, FaceOverlayView faceOverlayView) {
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

                int captureWidth = textureView.getWidth();
                int captureHeight = textureView.getHeight();
                Bitmap fullBitmap = textureView.getBitmap(captureWidth, captureHeight);

                if (fullBitmap == null) {
                    Log.e("Camera", "Erro: Bitmap nulo.");
                    return;
                }

                try {
                    RectF ovalBounds = faceOverlayView.getOvalRect();
                    if (ovalBounds == null) {
                        Log.e("Camera", "Erro: Oval não definida.");
                        return;
                    }

                    Bitmap croppedBitmap = cropToOval(fullBitmap, ovalBounds);
                    fullBitmap.recycle();

                    byte[] jpegData = convertBitmapToHighQualityJPEG(croppedBitmap);
                    croppedBitmap.recycle();

                    if (jpegData == null || jpegData.length == 0) {
                        Log.e("Camera", "Erro: Buffer JPEG vazio.");
                        return;
                    }

                    int width = (int) ovalBounds.width();
                    int height = (int) ovalBounds.height();
                    FaceFrame faceFrame = new FaceFrame(jpegData, null, null, width, height, width, 0, 0, 0, 0, 0, 0);
                    synchronized (captureLock) {
                        mImageQueue.add(faceFrame);
                        captureLock.notify();
                    }
                } catch (Exception e) {
                    Log.e("Camera", "Erro ao processar frame", e);
                }
            }
        });
    }

    private Bitmap cropToOval(Bitmap original, RectF ovalBounds) {
        Bitmap croppedBitmap = Bitmap.createBitmap(
                (int) ovalBounds.width(),
                (int) ovalBounds.height(),
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(croppedBitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        Path path = new Path();
        path.addOval(new RectF(0, 0, ovalBounds.width(), ovalBounds.height()), Path.Direction.CCW);

        canvas.drawColor(Color.WHITE);

        canvas.save();
        canvas.clipPath(path);
        canvas.drawBitmap(original, -ovalBounds.left, -ovalBounds.top, paint);
        canvas.restore();

        return croppedBitmap;
    }

    private byte[] convertBitmapToHighQualityJPEG(Bitmap bitmap) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            Log.e("Camera", "Erro ao converter bitmap para JPEG", e);
            return null;
        }
    }

    public void processCameraFrames(Activity activity, AutoFitTextureView textureView, FaceOverlayView faceOverlayView, TextView statusTextView, Callback callback) {
        mImageQueue.clear();
        isProcessingFrames = true;

        long[] faceDetectedStartTime = {0};
        int stabilityTimeMs = 600;

        float[] lastCenterX = {-1f};
        float[] lastCenterY = {-1f};
        float stabilityThreshold = 0.03f;

        new Thread(() -> {
            while (isProcessingFrames) {
                if (engine == null) {
                    Log.e(LOG_TAG, "Erro: engine não foi inicializado!");
                    callback.onFailure("Erro: engine não inicializado!");
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

                if (!mImageQueue.isEmpty()) {
                    FaceFrame data = mImageQueue.remove(0);
                    if (data == null || data.getBuffer1() == null || data.getBuffer1().length == 0) {
                        Log.e(LOG_TAG, "Erro: Buffer de imagem inválido.");
                        continue;
                    }

                    NSubject subject = new NSubject();
                    NImage image = null;

                    try {
                        if (data.getBuffer2() != null && data.getBuffer3() != null) {
                            image = NImage.create(NPixelFormat.RGB_8U, data.getWidth(), data.getHeight(), data.getStride());
                            image.copyFromYCbCrData(
                                    new NBuffer(data.getBuffer1()), data.getRowStride1(), data.getPixelStride1(),
                                    new NBuffer(data.getBuffer2()), data.getRowStride2(), data.getPixelStride2(),
                                    new NBuffer(data.getBuffer3()), data.getRowStride3(), data.getPixelStride3()
                            );
                        } else {
                            image = NImage.fromMemory(new NBuffer(data.getBuffer1()), NImageFormat.getJPEG());
                        }
                    } catch (IllegalArgumentException ex) {
                        Log.e(LOG_TAG, "Formato de imagem inválido. Ignorando o frame!", ex);
                        continue;
                    }

                    if (image != null) {
                        NFace nFace = engine.detectFaces(image);
                        subject.getFaces().add(nFace);

                        NBiometricTask task = engine.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);
                        engine.performTask(task);

                        if (task.getStatus() == NBiometricStatus.OK && !subject.getFaces().isEmpty()) {
                            NFace detectedFace = subject.getFaces().get(0);
                            if (!detectedFace.getObjects().isEmpty()) {
                                int faceWidth = detectedFace.getObjects().get(0).getBoundingRect().width();
                                int imageWidth = image.getWidth();

                                float faceRatio = (float) faceWidth / imageWidth;
                                Log.d("FaceDetection", "Face width: " + faceWidth + ", Image width: " + imageWidth + ", Ratio: " + faceRatio);

                                float centerX = (detectedFace.getObjects().get(0).getBoundingRect().left + detectedFace.getObjects().get(0).getBoundingRect().right) / 2f / imageWidth;
                                float centerY = (detectedFace.getObjects().get(0).getBoundingRect().top + detectedFace.getObjects().get(0).getBoundingRect().bottom) / 2f / image.getHeight();

                                // Só capturar se o rosto ocupar pelo menos 55% da largura da imagem
                                if (faceRatio < 0.55f) {
                                    Log.d("FaceDetection", "Rosto muito distante da câmera. Ignorando frame.");
                                    continue;
                                }

                                // Verifica estabilidade do centro
                                if (lastCenterX[0] >= 0 && lastCenterY[0] >= 0) {
                                    float deltaX = Math.abs(centerX - lastCenterX[0]);
                                    float deltaY = Math.abs(centerY - lastCenterY[0]);

                                    if (deltaX > stabilityThreshold || deltaY > stabilityThreshold) {
                                        faceDetectedStartTime[0] = 0;
                                        activity.runOnUiThread(() -> statusTextView.setText("Mantenha o rosto estável"));
                                        lastCenterX[0] = centerX;
                                        lastCenterY[0] = centerY;
                                        continue;
                                    }
                                }
                                lastCenterX[0] = centerX;
                                lastCenterY[0] = centerY;

                                if (faceDetectedStartTime[0] == 0) {
                                    faceDetectedStartTime[0] = System.currentTimeMillis();
                                    activity.runOnUiThread(() -> statusTextView.setText("Aguardando estabilidade..."));
                                }

                                long duration = System.currentTimeMillis() - faceDetectedStartTime[0];
                                if (duration >= stabilityTimeMs) {
                                    activity.runOnUiThread(() -> statusTextView.setText("Capturando imagem..."));

                                    String base64Image = convertNImageToBase64(image);
                                    isProcessingFrames = false;
                                    callback.onSuccess(base64Image);
                                    return;
                                }
                            } else {
                                Log.e(LOG_TAG, "Nenhum objeto facial detectado.");
                                faceDetectedStartTime[0] = 0;
                            }
                        } else {
                            String errorMessage = "Erro no reconhecimento facial. Status: " + task.getStatus();
                            PluginResult result = new PluginResult(PluginResult.Status.ERROR, errorMessage);
                            Log.e(LOG_TAG, errorMessage);
                            faceDetectedStartTime[0] = 0;
                            activity.runOnUiThread(() -> statusTextView.setText("Erro ao detectar rosto"));
                            result.setKeepCallback(true);
                            callback.sendPluginResult(result);
                        }
                    }
                }
            }
        }).start();
    }

    public void stopBackgroundThread() {
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
                Log.e("NeurotechnologyService", "Erro ao parar a thread de fundo", e);
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

    public void closeCameraView(Activity activity, AutoFitTextureView textureView, FaceOverlayView faceOverlayView) {
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
                    rootView.removeView(faceOverlayView);
                }
                faceOverlayView.setVisibility(View.GONE);
            } catch (Exception e) {
                Log.e("CameraError", "Erro ao fechar câmera", e);
            }
        });
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
