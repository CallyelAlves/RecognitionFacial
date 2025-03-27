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

import com.cordova.neurotechnology.neurotechnologyservice;
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
                case "initialize":
                    initializeMatchingService("initializeLicense");
                    return true;
                case "initializeMatchingClient":
                    initializeMatchingService("initializeMatchingClient");
                    return true;
                case "enrollFromBase64":
                    return enrollFromBase64(args);
                case "identifyBase64":
                    return identifyBase64(args);
                case "startCamera":
                    startCamera();
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

    private void initializeMatchingService(String method) {
        if ("initializeLicense".equals(method)) {
            MatchingService.initializeLicense(callbackContext, this.context);
        } else if ("initializeMatchingClient".equals(method)) {
            MatchingService.initializeMatchingClient(callbackContext, this.context);
        }
    }

    private boolean enrollFromBase64(JSONArray args) {
        try {
            String personId = args.getString(0);
            String base64Image = args.getString(1);
            boolean success = MatchingService.enrollFromBase64(personId, base64Image, callbackContext);
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

    private boolean identifyBase64(JSONArray args) {
        try {
            String base64Image = args.getString(0);
            String[] result = MatchingService.IdentifyFace(base64Image, callbackContext);
            callbackContext.success(String.join(", ", result));
            return true;
        } catch (Exception e) {
            callbackContext.error("Error in identifyBase64: " + e.getMessage());
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

        MatchingService matchingService = new MatchingService();
        matchingService.setCompletionHandler(new CompletionHandler<NBiometricTask, NBiometricOperation>() {
            @Override
            public void completed(NBiometricTask task, NBiometricOperation operation) {
                Log.d(TAG, "Processamento biométrico concluído.");
            }

            @Override
            public void failed(Throwable throwable, NBiometricOperation operation) {
                Log.e(TAG, "Erro no processamento biométrico", throwable);
            }
        });

        matchingService.startFrameProcessing(textureView, faceOverlayView);
        matchingService.openCamera(context, textureView, backgroundHandler);
        matchingService.processCameraFrames(callbackContext, context, activity, textureView, faceOverlayView);
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
