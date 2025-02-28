package com.app.recognition.matchingservice;

import com.app.facesample.service.MatchingService;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import android.content.Context;
import android.app.Activity;
import android.util.Log;
import com.app.recognition.matchingservice.utils.AutoFitTextureView;
import android.os.Handler;
import android.os.HandlerThread;
import br.com.nasajon.pontomobile.R;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.LayoutInflater;
import android.view.View;
import com.neurotec.util.concurrent.CompletionHandler;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NBiometricOperation;
import android.view.ViewGroup;

public class RecognitionFacial extends CordovaPlugin implements TextureView.SurfaceTextureListener {
    private CallbackContext callbackContext;
    private Context context;
    private AutoFitTextureView textureView;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Activity activity;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.context = cordova.getActivity().getApplicationContext();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("initialize")) {
            MatchingService.initializeLicense(callbackContext, this.context);
            return true;
        }

        if (action.equals("initializeMatchingClient")) {
            MatchingService.initializeMatchingClient(callbackContext, this.context);
            return true;
        }

        if (action.equals("enrollFromBase64")) {
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

        if (action.equals("identifyBase64")) {
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

        if (action.equals("startCamera")) {
            this.callbackContext = callbackContext;
            startCamera(callbackContext);
            return true;
        }

        return false;
    }

    private void startCamera(CallbackContext callbackContext) {
        this.activity = cordova.getActivity();
        final ViewGroup container = (ViewGroup) activity.findViewById(android.R.id.content);
        this.activity.runOnUiThread(() -> {
            try {
                Log.d("CameraDebug", "Activity: " + activity.getClass().getName());
                LayoutInflater inflater = LayoutInflater.from(activity);
                View view = inflater.inflate(R.layout.activity_main, container, false);
                container.addView(view);
                textureView = view.findViewById(R.id.texture_view);
                if (textureView == null) {
                    Log.e("CameraError", "textureView está NULL após inflar manualmente");
                    callbackContext.error("textureView está NULL após inflar manualmente");
                    return;
                }
                textureView.setVisibility(View.VISIBLE);
                startBackgroundThread();
                textureView.setSurfaceTextureListener(RecognitionFacial.this);
            } catch (Exception e) {
                Log.e("CameraError", "Erro ao iniciar câmera", e);
                callbackContext.error("Erro ao iniciar câmera: " + e.getMessage());
            }
        });
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d("RecognitionFacial", "onSurfaceTextureAvailable chamado");
        MatchingService matchingService = new MatchingService();
        matchingService.setCompletionHandler(new CompletionHandler<NBiometricTask, NBiometricOperation>() {
            @Override
            public void completed(NBiometricTask task, NBiometricOperation operation) {
            }
            @Override
            public void failed(Throwable throwable, NBiometricOperation operation) {
                Log.e("MatchingService", "Erro no processamento biométrico", throwable);
            }
        });
        matchingService.startFrameProcessing(textureView);
        matchingService.openCamera(cordova.getActivity().getApplicationContext(), textureView, backgroundHandler);
        // Caso não utilize uma WebView específica, pode passar null
        matchingService.processCameraFrames(this.callbackContext, this.context, this.activity, textureView, null);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Lógica para lidar com mudanças de tamanho
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.d("RecognitionFacial", "onSurfaceTextureUpdated chamado");
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
        }
    }
}
