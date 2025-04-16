package com.cordova.neurotechnology;

import android.app.Activity;
import android.content.Context;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

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
import org.json.JSONArray;
import org.json.JSONObject;

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
                    startCamera();
                    return true;
                case "cleanDB":
                    cleanDB();
                    return true;
                case "closeCamera":
                    closeCameraView();
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

    private void startCamera() {
        this.activity = cordova.getActivity();
        final ViewGroup container = activity.findViewById(android.R.id.content);

        activity.runOnUiThread(() -> {
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

                configureFaceOverlay(view);
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
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro ao iniciar câmera", e);
                callbackContext.error("Erro ao iniciar câmera: " + e.getMessage());
            }
        });
    }

    private void configureFaceOverlay(View view) {
        int screenWidth = view.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = view.getResources().getDisplayMetrics().heightPixels;

        float ovalWidth = screenWidth * 0.7f;
        float ovalHeight = ovalWidth * 1.8f;
        float left = (screenWidth - ovalWidth) / 2;
        float top = (screenHeight - ovalHeight) / 2;

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
        neurotechnologyService.processCameraFrames(activity, textureView, faceOverlayView, statusTextView, new Callback() {
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
                callbackContext.error(errorMessage);
            }
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureUpdated chamado");
    }

    public void closeCameraView() {
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
