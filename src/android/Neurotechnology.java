package com.cordova.neurotechnology;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.content.Context;
import android.graphics.Matrix;
import android.util.DisplayMetrics;

import com.cordova.neurotechnology.NeurotechnologyService;
import com.cordova.neurotechnology.utils.Callback;
import com.cordova.neurotechnology.utils.AutoFitTextureView;
import com.cordova.neurotechnology.utils.FaceOverlayView;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.util.concurrent.CompletionHandler;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.view.OrientationEventListener;
import android.hardware.SensorManager;
import android.view.Surface;

import br.com.nasajon.pontomobile.R;

public class Neurotechnology extends CordovaPlugin implements TextureView.SurfaceTextureListener {
    private static final String TAG = "Neurotechnology";

    private CallbackContext callbackContext;
    private Context context;
    private Activity activity;
    private AutoFitTextureView textureView;
    private FaceOverlayView faceOverlayView;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private TextView statusTextView;
    private ImageButton btnBack;
    private OrientationEventListener orientationEventListener;
    private int lastKnownRotation = -1;
    private int tempoMinimoEstabilidadeMs;
    private float limiteMovimentoPermitido;
    private float proporcaoMinimaRosto;
    private CallbackContext eventCallbackContext;

    private NeurotechnologyService neurotechnologyService = new NeurotechnologyService();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.context = cordova.getActivity().getApplicationContext();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;

        try {
            switch (action) {
                case "initializeLicense":
                    initializeNeurotechnologyService("initializeLicense");
                    return true;
                case "initializeClient":
                    initializeNeurotechnologyService("initializeClient");
                    return true;
                case "enrollFromBase64":
                    return enrollFromBase64(args);
                case "identifyFace":
                    return identifyFace(args);
                case "startCamera":
                    startCamera(args);
                    return true;
                case "cleanDB":
                    cleanDB();
                    return true;
                case "closeCamera":
                    closeCameraView();
                    return true;
                case "subscribeToEvents":
                    this.eventCallbackContext = callbackContext;
                    PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
                    result.setKeepCallback(true);
                    eventCallbackContext.sendPluginResult(result);
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao executar ação: " + action, e);
            callbackContext.error("Erro ao executar ação: " + e.getMessage());
            return false;
        }
    }

    private void initializeNeurotechnologyService(String method) {
        try {
            if ("initializeLicense".equals(method)) {
                NeurotechnologyService.initializeLicense(this.context);
            } else if ("initializeClient".equals(method)) {
                NeurotechnologyService.initializeClient(this.context);
            } else {
                callbackContext.error("Invalid method: " + method);
                return;
            }

            callbackContext.success(method);
        } catch (Exception e) {
            callbackContext.error("Error in " + method + ": " + e.getMessage());
        }
    }

    private void cleanDB() {
        try {
            NeurotechnologyService.cleanDB();
        } catch (Exception e) {
            callbackContext.error("Error in enrollFromBase64: " + e.getMessage());
        }
    }


    private boolean enrollFromBase64(JSONArray args) {
        try {
            JSONObject userData = args.getJSONObject(0);
            String base64Image = args.getString(1);
            boolean success = NeurotechnologyService.enrollFromBase64(userData, base64Image);
            if (success) {
                callbackContext.success("Enrollment successful");
            } else {
                callbackContext.error("Enrollment failed");
            }
            return true;
        } catch (Exception e) {
            callbackContext.error("Error in enrollFromBase64: " + e.getMessage());
            return false;
        }
    }

    private boolean identifyFace(JSONArray args) {
        try {
            String base64Image = args.getString(0);
            String[] result = NeurotechnologyService.identifyFace(base64Image);
            callbackContext.success(String.join(", ", result));
            return true;
        } catch (Exception e) {
            callbackContext.error("Error in identifyFace: " + e.getMessage());
            return false;
        }
    }

    private void startCamera(JSONArray args) {
        try {
            tempoMinimoEstabilidadeMs = args.getInt(0);
            limiteMovimentoPermitido = (float) args.getDouble(1);
            proporcaoMinimaRosto = (float) args.getDouble(2);
        } catch (JSONException e) {
            Log.e(TAG, "Erro ao ler argumentos JSON", e);
            // callbackContext.error("Erro ao ler argumentos JSON: " + e.getMessage());
            return;
        }
        this.activity = cordova.getActivity();
        final ViewGroup container = activity.findViewById(android.R.id.content);

        activity.runOnUiThread(() -> {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            try {
                Log.d(TAG, "Iniciando câmera na Activity: " + activity.getClass().getName());

                LayoutInflater inflater = LayoutInflater.from(activity);
                View view = inflater.inflate(R.layout.activity_main, container, false);
                container.addView(view);

                textureView = view.findViewById(R.id.texture_view);
                faceOverlayView = view.findViewById(R.id.face_overlay);

                if (textureView == null) {
                    Log.e(TAG, "textureView está NULL após inflar layout");
                    callbackContext.error("textureView está NULL após inflar layout");
                    return;
                }

                view.post(() -> configureFaceOverlay(view));

                // configureFaceOverlay(view);
                // Detectar mudança de rotação
                orientationEventListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
                    @Override
                    public void onOrientationChanged(int orientation) {
                        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                        if (rotation != lastKnownRotation) {
                            lastKnownRotation = rotation;
                            Log.d(TAG, "Nova rotação detectada: " + rotation);

                            activity.runOnUiThread(() -> {
                                View view = activity.findViewById(android.R.id.content);
                                configureFaceOverlay(view); // Recalcula a oval com base na nova rotação
                                CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                                try{
                                    String cameraId = manager.getCameraIdList()[1];
                                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                                    int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                                    int displayRotation = getJpegOrientationSensor(sensorOrientation, rotation);
                                    Matrix matrix = new Matrix();

                                    int viewWidth = textureView.getWidth();
                                    int viewHeight = textureView.getHeight();

                                    if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                                        // Ajusta para modo paisagem
                                        float centerX = viewWidth / 2f;
                                        float centerY = viewHeight / 2f;
                                        matrix.postRotate(displayRotation, centerX, centerY);
                                    } else if (rotation == Surface.ROTATION_180) {
                                        matrix.postRotate(180, viewWidth / 2f, viewHeight / 2f);
                                    }

                                    textureView.setTransform(matrix);
                                } catch (Exception e) {
                                    Log.e("Camera erro:", "Camera exception", e);
                                }
                            });
                        }
                    }
                };

                if (orientationEventListener.canDetectOrientation()) {
                    orientationEventListener.enable();
                }
                startBackgroundThread();

                textureView.setVisibility(View.VISIBLE);
                textureView.setSurfaceTextureListener(this);

                btnBack = view.findViewById(R.id.btn_back);
                statusTextView = view.findViewById(R.id.status_text_view);

                btnBack.setOnClickListener(v -> {

                    neurotechnologyService.closeCameraView(activity, textureView, faceOverlayView);
                    neurotechnologyService.stopBackgroundThread();

                    ViewGroup widget = (ViewGroup) activity.findViewById(android.R.id.content);
                    widget.removeView(view);

                    if (eventCallbackContext != null) {
                        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "botao_voltar_clicado");
                        pluginResult.setKeepCallback(true);
                        eventCallbackContext.sendPluginResult(pluginResult);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar câmera", e);
                callbackContext.error("Erro ao iniciar câmera: " + e.getMessage());
            }
        });
    }

    private int getJpegOrientationSensor(int sensorOrientation, int deviceRotation) {
        int[] ORIENTATIONS = {0, 90, 180, 270};
        return (ORIENTATIONS[deviceRotation] + sensorOrientation + 270) % 360;
    }

    private void configureFaceOverlay(View view) {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        boolean isLandscape = screenWidth > screenHeight;

        float ovalWidth = (isLandscape ? screenHeight : screenWidth) * 0.7f;
        float ovalHeight = ovalWidth * 1.8f;

        float left = (screenWidth - ovalWidth) / 2f;
        float top = (screenHeight - ovalHeight) / 2f;

        RectF ovalRect = new RectF(left, top, left + ovalWidth, top + ovalHeight);
        faceOverlayView.setOvalRect(ovalRect);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable chamado");

        neurotechnologyService.setCompletionHandler(new CompletionHandler<NBiometricTask, NBiometricOperation>() {
            @Override
            public void completed(NBiometricTask task, NBiometricOperation operation) {
                Log.d(TAG, "Processamento concluído.");
            }

            @Override
            public void failed(Throwable throwable, NBiometricOperation operation) {
                Log.e(TAG, "Erro no processamento", throwable);
            }
        });

        neurotechnologyService.startFrameProcessing(textureView, faceOverlayView);
        neurotechnologyService.openCamera(context, textureView, backgroundHandler);
        neurotechnologyService.processCameraFrames(activity, textureView, faceOverlayView, statusTextView, tempoMinimoEstabilidadeMs, limiteMovimentoPermitido, proporcaoMinimaRosto, new Callback() {
            @Override
            public void onSuccess(String result) {
                activity.runOnUiThread(() -> {
                    btnBack.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    callbackContext.success(result);
                    closeCameraView();
                });
            }
            @Override
            public void onFailure(String errorMessage) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, errorMessage);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
            }
            @Override
            public void sendPluginResult(PluginResult result) {
                callbackContext.sendPluginResult(result);
            }
        });
        configureTransform(textureView);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        configureTransform(textureView);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureUpdated chamado");
    }

    private void configureTransform(TextureView textureView) {
        if (textureView == null) return;

        int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, textureView.getWidth(), textureView.getHeight());
        RectF bufferRect = new RectF(0, 0, textureView.getHeight(), textureView.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) textureView.getHeight() / textureView.getWidth(),
                    (float) textureView.getWidth() / textureView.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180, centerX, centerY);
        }

        textureView.setTransform(matrix);
    }

    public void closeCameraView() {
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        neurotechnologyService.closeCameraView(activity, textureView, faceOverlayView);
    }
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBackgroundThread();
    }
}
