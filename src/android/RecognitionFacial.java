package com.app.recognition.matchingservice;

import com.app.facesample.service.MatchingService;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.app.Activity;
import android.util.Log;
import com.app.recognition.matchingservice.utils.AutoFitTextureView;
import android.os.Handler;
import android.os.HandlerThread;
import com.exemplo.apppontoteste.R;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.LayoutInflater;
import android.view.View;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.util.concurrent.CompletionHandler;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NBiometricOperation;
import android.os.Handler.Callback;

public class RecognitionFacial extends CordovaPlugin implements TextureView.SurfaceTextureListener {
    private CallbackContext callbackContext;
    private Context context; // Armazena o contexto
    private AutoFitTextureView textureView;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.context = cordova.getActivity().getApplicationContext(); // Obtém o contexto
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("initialize")) {
            MatchingService.initializeLicense(callbackContext, this.context);
//            callbackContext.success("Matching Client Initialized");
            return true;
        }

        if (action.equals("initializeMatchingClient")) {
            MatchingService.initializeMatchingClient(callbackContext, this.context);
//            callbackContext.success("Matching Client Initialized");
            return true;
        }

        if (action.equals("enrollFromBase64")) {
            try {
                String personId = args.getString(0); // Primeiro argumento
                String base64Image = args.getString(1); // Segundo argumento

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
                String base64Image = args.getString(0); // Segundo argumento

                String[] success = MatchingService.IdentifyFace(base64Image, callbackContext);
                callbackContext.success(String.join(", ", success));

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
        Activity activity = cordova.getActivity();
        Context context = activity.getApplicationContext();
        activity.runOnUiThread(() -> {
            try {
                Log.d("CameraDebug", "Activity: " + activity.getClass().getName());

                LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View view = inflater.inflate(R.layout.activity_main, null);

                textureView = view.findViewById(R.id.texture_view);

                if (textureView == null) {
                    Log.e("CameraError", "textureView está NULL após inflar manualmente");
                    callbackContext.error("textureView está NULL após inflar manualmente");
                    return;
                }

                activity.setContentView(view);

                startBackgroundThread();
                textureView.setSurfaceTextureListener(RecognitionFacial.this);

                // MatchingService matchingService = new MatchingService();
                // matchingService.startFrameProcessing(textureView);
                // matchingService.openCamera(context, textureView, backgroundHandler);

                callbackContext.success("Câmera iniciada com sucesso.");
            } catch (Exception e) {
                Log.e("CameraError", "Erro ao iniciar câmera", e);
                callbackContext.error("Erro ao iniciar câmera: " + e.getMessage());
            }
        });
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // Lógica para abrir a câmera
        Log.d("RecognitionFacial", "onSurfaceTextureAvailable chamado");
        MatchingService matchingService = new MatchingService();
        matchingService.setCompletionHandler(new CompletionHandler<NBiometricTask, NBiometricOperation>() {
            @Override
            public void completed(NBiometricTask task, NBiometricOperation operation) {
                if (task.getStatus() == NBiometricStatus.OK) {
                    Log.d("MatchingService", "Template criado com sucesso.");
                    boolean liveness = MatchingService.checkLiveness(matchingService.getBiometricClient(), task.getSubjects().get(0));
                    if (liveness) {
                        Log.d("MatchingService", "Prova de vida aprovada!");
                    } else {
                        Log.d("MatchingService", "Prova de vida reprovada!");
                    }
                } else {
                    Log.e("MatchingService", "Falha na criação do template: " + task.getStatus());
                }
            }

            @Override
            public void failed(Throwable throwable, NBiometricOperation operation) {
                Log.e("MatchingService", "Erro no processamento biométrico", throwable);
            }
        });


        matchingService.startFrameProcessing(textureView);
        matchingService.openCamera(cordova.getActivity().getApplicationContext(), textureView, backgroundHandler);

        // Inicia o processamento dos frames para prova de vida e template
        matchingService.processCameraFrames(this.callbackContext);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Lógica para lidar com mudanças de tamanho
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true; // Indica que o recurso foi liberado
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Processamento do frame atualizado
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
