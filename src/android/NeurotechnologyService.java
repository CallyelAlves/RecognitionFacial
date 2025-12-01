package com.cordova.neurotechnology;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
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
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.graphics.Point;
import android.media.ImageReader;
import android.os.Build;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.content.res.Configuration;
import android.view.WindowMetrics;
import android.util.DisplayMetrics;
import android.media.Image;
import android.util.Range;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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
import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageFormat;
import com.neurotec.images.NPixelFormat;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NCore;
import com.neurotec.licensing.NLicense;
import com.neurotec.licensing.NLicenseManager;
import com.neurotec.licensing.gui.LicensingPreferencesFragment;
import com.neurotec.util.concurrent.CompletionHandler;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import org.json.JSONArray;

import com.cordova.neurotechnology.utils.Callback;
import com.cordova.neurotechnology.utils.AuthenticationError;
import com.cordova.neurotechnology.utils.AutoFitTextureView;
import com.cordova.neurotechnology.utils.FaceOverlayView;
import com.cordova.neurotechnology.utils.NeurotechnologyServiceResluts;
import com.cordova.neurotechnology.utils.AppSettings;
import com.cordova.neurotechnology.utils.CameraResolution;
import com.cordova.neurotechnology.helpers.FaceFrame;
import com.cordova.neurotechnology.licensing.LicensingManager;
import com.cordova.neurotechnology.licensing.LicensingState;

import com.neurotec.biometrics.NBiometricCaptureOption;

import com.google.android.gms.common.util.concurrent.HandlerExecutor;

import br.com.nasajon.pontocompartilhado.R;

public class NeurotechnologyService implements LicensingManager.LicensingStateCallback {

    private static final String LOG_TAG = NeurotechnologyService.class.getSimpleName();
    private long faceDetectedStartTime = 0;
    private static final long FACE_STABLE_DURATION_MS = 20000;
    private static final int MIN_FACE_WIDTH = 350;
    private static NBiometricClient engine;
    private static final int ENROLLMENT_ACCURACY = 90;
    private static String lastEnrollFailureReason = null;
    private static AuthenticationError lastEnrollFailureCode = null;
    private static final AtomicBoolean needsEngineRecreation = new AtomicBoolean(false);
    private static volatile Context engineContext;

    private final Object captureLock = new Object();
    private List<FaceFrame> mImageQueue = new ArrayList<>();
    private NBiometricClient biometricClient;
    private CompletionHandler<NBiometricTask, NBiometricOperation> completionHandler;
    private int frameCount = 0;
    private volatile boolean isProcessingFrames = true;
    private Context appContext;
    private Activity currentActivity;
    private Bitmap ultimaImagemCompleta = null;
    private static volatile int lastCapturedOrientationDegrees = 0;
    private static String faceClientPath = null;
    private static String faceMatcherPath = null;
    private static final List<String> REQUIRED_FACE_COMPONENTS = Collections.unmodifiableList(Arrays.asList(
            "Biometrics.FaceExtraction",
            "Biometrics.FaceMatching"
    ));
    private static final String LOCAL_LICENSE_SERVER = "/local";
    private static final int LOCAL_LICENSE_TIMEOUT = 5000;

    private int mSensorOrientation;
    private int mLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
    private Size mPreviewSize;
    private int cameraAngle = 0;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private String mCameraId;
    private Handler mBackgroundCameraHandler;
    private AutoFitTextureView mTextureView;
    private int imageFormat;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private boolean isLandscape = false;
    private List<CameraResolution> availableResolutions = new ArrayList<>();
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private long lastFrameProcessed = 0;
    private State mState = State.CAPTURING;
    private enum State {
        CAPTURING,
        EXTRACTION
    }
    private static final int BYTES_PER_RGB_PIXEL = 3;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;
    private HandlerThread mBackgroundCameraThread;
    private static final String LICENSES_DIR = "Neurotechnology/Licenses";
    private static List<String> licenseStringToken = new ArrayList<>();
    private static List<String> deactivationIDToken = new ArrayList<>();
    private static Rect lastCapturedFaceRect = null;
    private final AtomicBoolean hasSentCaptureResult = new AtomicBoolean(false);

    public static void initializeLicense(Context context, JSONArray args, CallbackContext callbackContext) {
        try {
            NCore.setContext(context);
            Log.d(LOG_TAG, "ARGS JSON: " + args.toString());

            JSONArray pathArray = args.getJSONArray(0);

            if (pathArray.length() == 0) {
                callbackContext.error("Nenhum caminho de licença fornecido.");
                return;
            }

            // Lista de componentes
            List<String> components = REQUIRED_FACE_COMPONENTS;

            List<String> licFilePaths = new ArrayList<>();

            for (int i = 0; i < pathArray.length(); i++) {
                // Remove o pré-fixo file do nome do arquivo
                String path = pathArray.getString(i).replaceFirst("file://", "");
                File file = new File(path);

                if (!file.exists()) {
                    Log.e(LOG_TAG, "Arquivo não encontrado: " + path);
                    continue;
                }

                // Verifica o tipo de arquivo
                if (path.toLowerCase().endsWith(".sn")) {
                    String licFileName = (i == 0) ? "FaceExtraction.lic" : "FaceMatcher.lic";
                    String licFilePath = context.getFilesDir() + "/" + licFileName;
                    licFilePaths.add(licFilePath);

                    // Se não existir o arquivo .lic ativa a licença online e cria o arquivo .lic
                    if (!Files.exists(Paths.get(licFilePath))) {
                        String serialContent = new String(Files.readAllBytes(file.toPath())).trim();

                        String id = NLicense.generateID(null, serialContent);
                        Log.d(LOG_TAG, "Activation ID gerado: " + id);

                        // Quando ativado retorna um token da ativação que é usado posteriomente para desativação, esse token é armazenado em um arquivo .lic
                        String licenseString = NLicense.activateOnline(id);
                        Log.d(LOG_TAG, "Licença ativada online: " + licenseString);

                        licenseStringToken.add(licenseString);
                        Files.write(Paths.get(licFilePath), licenseString.getBytes("UTF-8"));
                        Log.d(LOG_TAG, "Licença salva em: " + licFilePath);
                    }

                    // Aqui adiciona a licença no aparelho
                    byte[] licBytes = Files.readAllBytes(Paths.get(licFilePath));
                    NBuffer buffer = new NBuffer(licBytes);
                    NLicense.add(buffer);
                    Log.d(LOG_TAG, "Licença adicionada com sucesso: " + licFilePath);

                } else if (path.toLowerCase().endsWith(".lic")) {
                    byte[] licBytes = Files.readAllBytes(file.toPath());
                    NBuffer buffer = new NBuffer(licBytes);
                    NLicense.add(buffer);
                    Log.d(LOG_TAG, "Licença adicionada com sucesso: " + path);

                } else {
                    Log.w(LOG_TAG, "Tipo de arquivo não reconhecido (esperado .sn ou .lic): " + path);
                }
            }

            // Obtém os componentes
            (new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... voids) {
                    boolean result = true;
                    for (String component : components) {
                        try {
                            // Carrega os componente da ativação
                            boolean ok = NLicense.obtainComponents("/local", 5000, component);
                            Log.d(LOG_TAG, component + ": " + (ok ? "ativado" : "falhou"));
                            result &= ok;
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Erro ao obter licença para " + component + ": " + e.getMessage());
                            result = false;
                        }
                    }
                    return result;
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    if (success) {
                        callbackContext.success("Licenças ativadas com sucesso." + licenseStringToken);
                    } else {
                        callbackContext.error("Falha ao ativar uma ou mais licenças.");
                    }
                }
            }).execute();

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Erro ao carregar licenças: " + ex.getMessage(), ex);
            callbackContext.error("Erro ao carregar licença: " + ex.getMessage());
        }
    }

    public static void deactivateLicenses(Context context, JSONArray args, CallbackContext callbackContext) {
        try {
            JSONArray pathArray = args.getJSONArray(0);

            if (pathArray.length() == 0) {
                callbackContext.error("Nenhum caminho de licença fornecido para desativação.");
                return;
            }

            boolean allDeactivated = true;

            for (int i = 0; i < pathArray.length(); i++) {
                String snPath = pathArray.getString(i).replaceFirst("file://", "");
                File snFile = new File(snPath);
                if (!snFile.exists()) {
                    Log.e(LOG_TAG, "Arquivo .sn não encontrado: " + snPath);
                    allDeactivated = false;
                    continue;
                }

                String serialContent = new String(Files.readAllBytes(snFile.toPath())).trim();
                String licFileName = (i == 0) ? "FaceExtraction.lic" : "FaceMatcher.lic";
                String licFilePath = context.getFilesDir() + "/" + licFileName;

                File licFile = new File(licFilePath);
                if (!licFile.exists()) {
                    Log.e(LOG_TAG, "Licença offline não encontrada para serial: " + serialContent);
                    allDeactivated = false;
                    continue;
                }

                // Lê o conteúdo da licença
                String licenseString = new String(Files.readAllBytes(licFile.toPath()), "UTF-8");

                // Gera o Deactivation ID
                String deactivationID = generateDeactivationID(licenseString);
                Log.d(LOG_TAG, "Deactivation ID gerado: " + deactivationID);
                deactivationIDToken.add(deactivationID);

                // Chama desativação online
                NLicense.deactivateOnlineWithID(licenseString, deactivationID);
                Log.d(LOG_TAG, "Licença desativada online para serial: " + serialContent);

                // Remove o arquivo local
                boolean deleted = licFile.delete();
                Log.d(LOG_TAG, "Arquivo .lic deletado: " + deleted);
            }

            if (allDeactivated) {
                callbackContext.success(String.valueOf(deactivationIDToken));
            } else {
                callbackContext.error("Algumas licenças não puderam ser desativadas.");
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Erro ao desativar licenças: " + e.getMessage(), e);
            callbackContext.error("Erro ao desativar licenças: " + e.getMessage());
        }
    }

    public static String generateDeactivationID(String licenseString) throws IOException {
        String deactivationID = NLicense.generateDeactivationIDForLicense(licenseString);
        Log.d(LOG_TAG, "Deactivation ID gerado: " + deactivationID);
        return deactivationID;
    }

    public static void initializeLicenseTrialMode(Context context, CallbackContext callbackContext) {
        boolean useTrial = LicensingPreferencesFragment.isUseTrial(context);
        NLicenseManager.setTrialMode(useTrial);
        Log.i(LOG_TAG, "Initializing trial mode (enabled=" + useTrial + ") with components: " + LicensingManager.components());
        NCore.setContext(context);
        new InitializationTask(context, callbackContext).execute();
    }

    public static void initializeClient(Context context) {
        if (context == null) {
            Log.w(LOG_TAG, "Não foi possível inicializar o engine: contexto nulo.");
            return;
        }
        engineContext = context.getApplicationContext();
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

    final static class InitializationTask extends AsyncTask<Object, Integer, Boolean> {
        private static final int OBTAINING_LICENSE = 2;
        private static final int PREPARE_DATA_FILES = 3;
        private static final int INITIALIZING_BIOMETRIC_CLIENT = 4;

        private boolean isLicenseObtained = false;
        private Context activityContext;
        private CallbackContext callContext;

        public InitializationTask(Context context, CallbackContext callbackContext) {
            activityContext = context;
            callContext = callbackContext;
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
        protected void onPostExecute(Boolean success) {
            if (success) {
                callContext.success("Licenças ativadas com sucesso.");
            } else {
                callContext.error("Falha ao ativar uma ou mais licenças.");
            }
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

    public static void cleanDB(){
        engine.clear();
        needsEngineRecreation.set(true);
    }

    public static AuthenticationError enrollTemplate(NSubject subject, JSONObject userData, NImage image) {
        try {
            String trabalhador = userData.getString("trabalhador");
            subject.setId(trabalhador);

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

    private static void recordEnrollFailure(String message, AuthenticationError errorCode, JSONObject userData, String base64Image) {
        lastEnrollFailureReason = message;
        lastEnrollFailureCode = errorCode;

        String userDataString = userData != null ? userData.toString() : "{}";
        Log.e(LOG_TAG, "Enrollment failure: " + message + ", errorCode=" + (errorCode != null ? errorCode.name() : "UNKNOWN"));
        Log.e(LOG_TAG, "Enrollment failure userData: " + userDataString);
        if (base64Image != null) {
            int previewLength = Math.min(120, base64Image.length());
            String preview = base64Image.substring(0, previewLength);
            Log.d(LOG_TAG, "Enrollment failure photo base64 length=" + base64Image.length() + ", preview=" + preview);
        }
    }

    public static String getLastEnrollFailureReason() {
        return lastEnrollFailureReason;
    }

    public static String getLastEnrollFailureCode() {
        return lastEnrollFailureCode != null ? lastEnrollFailureCode.name() : null;
    }

    public static boolean enrollFromBase64(JSONObject userData, String base64Image) {
        lastEnrollFailureReason = null;
        lastEnrollFailureCode = null;

        try {
            recreateEngineIfNeeded();
            if (userData == null) {
                recordEnrollFailure("User data is null", AuthenticationError.ENROLLMENT_ERROR, null, base64Image);
                return false;
            }

            if (base64Image == null || base64Image.trim().isEmpty()) {
                recordEnrollFailure("Empty Base64 image received", AuthenticationError.EXTRACTION_ERROR, userData, base64Image);
                return false;
            }

            Log.d(LOG_TAG, "Starting enrollFromBase64 for trabalhador=" + userData.optString("trabalhador", "") + ", codigo=" + userData.optString("codigo", ""));
            Log.d(LOG_TAG, "Image base64 length=" + base64Image.length());

            if (!ensureFaceLicenses()) {
                recordEnrollFailure("Unable to obtain face biometric licenses", AuthenticationError.ENROLLMENT_ERROR, userData, base64Image);
                return false;
            }

            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            if (decodedBytes == null || decodedBytes.length == 0) {
                recordEnrollFailure("Decoded Base64 image is empty", AuthenticationError.EXTRACTION_ERROR, userData, base64Image);
                return false;
            }

            NImage image = NImage.fromMemory(new NBuffer(decodedBytes));

            if (image == null) {
                recordEnrollFailure("Invalid image provided", AuthenticationError.EXTRACTION_ERROR, userData, base64Image);
                return false;
            }

            NSubject extractSubject = new NSubject();
            NFace face = new NFace();
            face.setImage(image);
            extractSubject.getFaces().add(face);
            NBiometricStatus status = engine.createTemplate(extractSubject);

            if (status == NBiometricStatus.NONE) {
                Log.w(LOG_TAG, "Face licenses missing when creating template. Attempting to re-obtain.");
                if (ensureFaceLicenses()) {
                    status = engine.createTemplate(extractSubject);
                }
            }

            if (status == NBiometricStatus.OK) {
                AuthenticationError result = enrollTemplate(extractSubject, userData, image);

                if (result == AuthenticationError.OK) {
                    Log.i(LOG_TAG, "Enrollment successful for trabalhador=" + userData.optString("trabalhador", "") + ", codigo=" + userData.optString("codigo", ""));
                    return true;
                } else {
                    recordEnrollFailure("Enrollment failed with error: " + result.name(), result, userData, base64Image);
                    return false;
                }
            }

            if (status == NBiometricStatus.OBJECT_NOT_FOUND) {
                recordEnrollFailure("No faces detected in the provided image", AuthenticationError.EXTRACTION_ERROR, userData, base64Image);
            } else {
                recordEnrollFailure("Failed to create face template (status=" + status.name() + ")", AuthenticationError.EXTRACTION_ERROR, userData, base64Image);
            }
            return false;
        } catch (Exception e) {
            recordEnrollFailure("Error enrolling from Base64: " + e.getMessage(), AuthenticationError.ENROLLMENT_ERROR, userData, base64Image);
            Log.e(LOG_TAG, "Error enrolling from Base64: ", e);
            return false;
        }
    }

    private static boolean ensureFaceLicenses() {
        boolean allObtained = true;
        for (String component : REQUIRED_FACE_COMPONENTS) {
            if (!isComponentActivated(component)) {
                boolean obtained = obtainLicenseComponent(component);
                allObtained &= obtained;
            }
        }
        return allObtained;
    }

    private static boolean isComponentActivated(String component) {
        try {
            return NLicense.isComponentActivated(component);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to check license component: " + component, e);
            return false;
        }
    }

    private static boolean obtainLicenseComponent(String component) {
        try {
            boolean obtained = NLicense.obtainComponents(LOCAL_LICENSE_SERVER, LOCAL_LICENSE_TIMEOUT, component);
            Log.d(LOG_TAG, "Obtaining license component '" + component + "' from " + LOCAL_LICENSE_SERVER + ": " + obtained);
            return obtained;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error obtaining license component: " + component, e);
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

    public static void recreateEngine(Context context) {
        try {
            if (context == null) {
                Log.w(LOG_TAG, "Não foi possível recriar o engine: contexto nulo.");
                return;
            }
            engineContext = context.getApplicationContext();
            if (engine != null) {
                engine.cancel();
                engine.dispose();
            }

            engine = new NBiometricClient();
            String path = context.getFilesDir().getAbsolutePath()
                    + System.getProperty("file.separator") + "BiometricsV50.db";
            engine.setDatabaseConnectionToSQLite(path);
            NBiographicDataSchema schema = NBiographicDataSchema.parse("(Thumbnail blob, UserData string)");
            engine.setCustomDataSchema(schema);
            engine.setUseDeviceManager(true);
            engine.setMatchingWithDetails(true);
            engine.setFacesCreateThumbnailImage(true);
            engine.setFacesThumbnailImageWidth(90);
            engine.setProperty("Faces.IcaoUnnaturalSkinToneThreshold", 10);
            engine.setProperty("Faces.IcaoSkinReflectionThreshold", 10);
            engine.setFacesTemplateSize(NTemplateSize.MEDIUM);
            engine.initialize();

            Log.i(LOG_TAG, "Engine recriado após captura ou identificação.");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Erro ao recriar engine", e);
        }
    }

    private static void recreateEngineIfNeeded() {
        if (!needsEngineRecreation.compareAndSet(true, false)) {
            return;
        }

        Context context = engineContext;
        if (context != null) {
            recreateEngine(context);
        } else {
            Log.w(LOG_TAG, "Engine marcado para recriação, mas o contexto está indisponível.");
            needsEngineRecreation.set(true);
        }
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

    public static boolean isLicensesObtained() {
        return LicensingManager.isLicensesObtained();
    }

    public static void release() {
        LicensingManager.release();
    }

    private void createCameraPreviewSession() {
        try {
            imageFormat = AppSettings.getImageFormat(this.appContext);
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);

            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), imageFormat, 2);
            mImageReader.setOnImageAvailableListener(reader -> {

                try (Image img = reader.acquireLatestImage()) {

                    long currentTime = System.currentTimeMillis();

                    if (currentTime - lastFrameProcessed < 100) {
                        return;
                    }

                    lastFrameProcessed = currentTime;

                    if (mState == State.CAPTURING) {
                        if (img != null) {

                            if (img.getPlanes().length == 3) {
                                //For YUV_420_888
                                ByteBuffer buf1 = img.getPlanes()[0].getBuffer();
                                byte[] buffer1 = new byte[buf1.capacity()];
                                buf1.get(buffer1);
                                ByteBuffer buf2 = img.getPlanes()[1].getBuffer();
                                byte[] buffer2 = new byte[buf2.capacity()];
                                buf2.get(buffer2);
                                ByteBuffer buf3 = img.getPlanes()[2].getBuffer();
                                byte[] buffer3 = new byte[buf3.capacity()];
                                buf3.get(buffer3);

                                synchronized (captureLock) {
                                    if (mImageQueue.size() > 1) {
                                        mImageQueue.remove(0);
                                    }
                                    FaceFrame faceFrame = new FaceFrame(buffer1, buffer2, buffer3, img.getWidth(), img.getHeight(), img.getWidth() * BYTES_PER_RGB_PIXEL, img.getPlanes()[0].getRowStride(), img.getPlanes()[0].getPixelStride(), img.getPlanes()[1].getRowStride(), img.getPlanes()[1].getPixelStride(), img.getPlanes()[2].getRowStride(), img.getPlanes()[2].getPixelStride());
                                    mImageQueue.add(faceFrame);
                                    captureLock.notify();
                                }

                            } else {
                                //For JPEG
                                ByteBuffer buf1 = img.getPlanes()[0].getBuffer();
                                byte[] buffer1 = new byte[buf1.capacity()];
                                buf1.get(buffer1);

                                synchronized (captureLock) {
                                    if (mImageQueue.size() > 1) {
                                        mImageQueue.remove(0);
                                    }

                                    FaceFrame faceFrame = new FaceFrame(buffer1);
                                    mImageQueue.add(faceFrame);
                                    captureLock.notify();
                                }
                            }
                        } else {
                            Log.e(LOG_TAG, "Empty image");
                        }
                    }
                }
            }, mBackgroundCameraHandler);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            if (mCameraDevice != null) {
                createCameraCaptureSession(surface);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraCaptureSession(Surface surface) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                List<OutputConfiguration> outputConfigurations = new ArrayList<>();
                outputConfigurations.add(new OutputConfiguration(surface));
                outputConfigurations.add(new OutputConfiguration(mImageReader.getSurface()));

                try {
                    mCameraDevice.createCaptureSession(new SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            outputConfigurations,
                            new HandlerExecutor(mBackgroundCameraHandler.getLooper()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                    configureCaptureSession(cameraCaptureSession);
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                }
                            }));
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                    configureCaptureSession(cameraCaptureSession);
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                }
                            }, mBackgroundCameraHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startBackgroundCameraThread() {
        mBackgroundCameraThread = new HandlerThread("CameraBackground");
        mBackgroundCameraThread.start();
        mBackgroundCameraHandler = new Handler(mBackgroundCameraThread.getLooper());
    }

    private void configureCaptureSession(@NonNull CameraCaptureSession cameraCaptureSession) {
        if (mCameraDevice == null) return;

        mCaptureSession = cameraCaptureSession;
        try {
            Size preferredSize = new Size(640, 480);
            mPreviewRequestBuilder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, preferredSize);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(15, 30));

            mPreviewRequest = mPreviewRequestBuilder.build();
            mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void configureTransform(Activity activity,AutoFitTextureView textureView, int viewWidth, int viewHeight) {
        mTextureView = textureView;
        int rotation = getDisplayRotation(activity);
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpCameraOutputs(Activity activity, Context context, int width, int height, boolean usarTraseira) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Descobre qual câmera usar (frontal ou traseira)
            String selectedCameraId = null;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (usarTraseira && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    selectedCameraId = cameraId;
                    Log.e(LOG_TAG, "Selecionada câmera TRASEIRA, ID: " + selectedCameraId);
                    break;
                } else if (!usarTraseira && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedCameraId = cameraId;
                    Log.e(LOG_TAG, "Selecionada câmera FRONTAL, ID: " + selectedCameraId);
                    break;
                }
            }

            // Fallback: se não encontrou, pega da AppSettings
            if (selectedCameraId == null) {
                selectedCameraId = AppSettings.getCurrentCamera(context);
                Log.e(LOG_TAG, "Não encontrou câmera desejada, usando da AppSettings: " + selectedCameraId);
            }

            mCameraId = selectedCameraId;
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);

            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null) {
                mLensFacing = facing;
            } else {
                mLensFacing = CameraCharacteristics.LENS_FACING_FRONT;
            }

            Log.e(LOG_TAG, "setUpCameraOutputs cameraID :" + facing);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }

            // Verifica formatos disponíveis
            int format = -1;
            for (int size : map.getOutputFormats()) {
                if (size == imageFormat)
                    format = imageFormat;
            }

            Size[] allSizes;
            if (format == -1) {
                allSizes = map.getOutputSizes(SurfaceTexture.class);
            } else {
                allSizes = map.getOutputSizes(imageFormat);
            }

            Size largest = Collections.max(Arrays.asList(allSizes), new CompareSizesByArea());

            int displayRotation = getDisplayRotation(activity);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            boolean swappedDimensions = false;
            switch (displayRotation) {
                case Surface.ROTATION_0:
                case Surface.ROTATION_180:
                    if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                        swappedDimensions = true;
                    }
                    break;
                case Surface.ROTATION_90:
                case Surface.ROTATION_270:
                    if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                        swappedDimensions = true;
                    }
                    break;
            }

            Point displaySize = getDisplaySize(activity);
            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = height;
                rotatedPreviewHeight = width;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            int[] resolutions = AppSettings.getCamResolution2(activity);
            if (resolutions[0] == 0 || resolutions[1] == 0) {
                mPreviewSize = chooseOptimalSize(map, format, width, height);
                AppSettings.setCameraResolution2(activity, mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mPreviewSize = chooseOptimalSize(map, format, width, height);
            }

            initializeAvailableResolutions(allSizes);
            int orientation = activity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                isLandscape = true;
                mTextureView.setAspectRatio(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                isLandscape = false;
                mTextureView.setAspectRatio(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }

            Log.e(LOG_TAG, "setUpCameraOutputs orientation : " + orientation);
            Log.e(LOG_TAG, "setUpCameraOutputs TextureView height: " + mPreviewSize.getHeight() + " width: " + mPreviewSize.getWidth());

            cameraAngle = getEffectiveImageRotation(facing, mSensorOrientation, displayRotation);
            engine.setFacesTemplateSize(NTemplateSize.MEDIUM);
            engine.setProperty("Faces.RollAngleBase", cameraAngle);
            engine.setProperty("Faces.DetectWithPose", true);
            engine.setFacesTemplateSize(NTemplateSize.MEDIUM);

            int minIOD = 100;
            int resolution = mPreviewSize.getWidth() * mPreviewSize.getHeight();
            if (resolution <= 1920 * 1080) minIOD = 80;
            if (resolution <= 800 * 600) minIOD = 50;
            if (resolution <= 640 * 480) minIOD = 40;
            engine.setFacesMinimalInterOcularDistance(minIOD);

            String faceQualityThreshold = String.valueOf(AppSettings.getFaceQualityTreshold(activity));
            engine.setFacesQualityThreshold(Byte.parseByte(faceQualityThreshold));

        } catch (CameraAccessException e) {
            Log.e( LOG_TAG,  e.getMessage());
        } catch (NullPointerException e) {
        }
    }

    private void initializeAvailableResolutions(Size[] choices) {
        availableResolutions = new ArrayList<>();
        for (Size size : choices) {
            if (size.getWidth() <= MAX_PREVIEW_WIDTH && size.getHeight() <= MAX_PREVIEW_HEIGHT)
                availableResolutions.add(new CameraResolution(size.getWidth(), size.getHeight()));
        }
    }

    private static Point getDisplaySize(Activity activity) {
        Point displaySize = new Point();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager windowManager = activity.getSystemService(WindowManager.class);
            if (windowManager != null) {
                WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
                Rect bounds = metrics.getBounds();
                displaySize.set(bounds.width(), bounds.height());
            } else {
                throw new IllegalStateException("WindowManager is not available");
            }
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
            displaySize.set(displayMetrics.widthPixels, displayMetrics.heightPixels);
        }

        return displaySize;
    }

    public void openCamera(Activity activity, Context context, AutoFitTextureView textureView, int width, int height) {
        this.appContext = context;
        this.currentActivity = activity;
        this.mTextureView = textureView;
        startBackgroundCameraThread();
        setUpCameraOutputs(activity, context, width, height, false);
        configureTransform(activity, textureView, width, height);
        CameraManager manager = (CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            manager.openCamera(mCameraId, mStateCallback, mBackgroundCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {

            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    private static int getDisplayRotation(Activity activity) {
        int displayRotation;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            DisplayManager displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager != null) {
                Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                if (display != null) {
                    displayRotation = display.getRotation();
                } else {
                    throw new IllegalStateException("Default display is not available");
                }
            } else {
                throw new IllegalStateException("DisplayManager is not available");
            }
        } else {
            displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        }

        return displayRotation;
    }

    private int getEffectiveImageRotation(int facing, int sensorOrientation, int displayRotation) {
        int degrees = displayRotation * 90;
        int result;
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            result = (sensorOrientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (sensorOrientation - degrees + 360) % 360;
        }

        result = result > 180 ? -1 * (360 - result) : result;
        if (facing != CameraCharacteristics.LENS_FACING_FRONT) {
            result *= -1;
        }
        return result;
    }

    private Size chooseOptimalSize(StreamConfigurationMap map, int format, int textureViewWidth, int textureViewHeight) {
        Size[] choices = null;
        if(format == -1){
            choices = map.getOutputSizes(ImageReader.class);
        }else{
            choices = map.getOutputSizes(format);
        }
        double targetRatio = (double) textureViewHeight / textureViewWidth;

        Size optimalSize = choices[0];
        double minDiff = Double.MAX_VALUE;

        for (Size option : choices) {
            double ratio = (double) option.getWidth() / option.getHeight();
            double diff = Math.abs(ratio - targetRatio);
            if (diff < minDiff) {
                minDiff = diff;
                optimalSize = option;
            }
        }
        return optimalSize;
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private int getJpegOrientationSensor(int sensorOrientation, int deviceRotation) {
        int[] ORIENTATIONS = {0, 90, 180, 270};
        return (ORIENTATIONS[deviceRotation] + sensorOrientation + 270) % 360;
    }

    private static byte[] convertBitmapToHighQualityJPEG(Bitmap bitmap) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            Log.e("Camera", "Erro ao converter bitmap para JPEG", e);
            return null;
        }
    }

    private int getImageRotationDegrees(Activity activity) {
        int displayRotation = getDisplayRotation(activity);
        int degrees = displayRotation * 90;

        if (mLensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            return (mSensorOrientation + degrees) % 360;
        }

        return (mSensorOrientation - degrees + 360) % 360;
    }

    public void processCameraFrames(
            Activity activity,
            AutoFitTextureView textureView,
            FaceOverlayView faceOverlayView,
            TextView statusTextView,
            int stabilityTimeMs,
            float stabilityThreshold,
            float minimumFaceProportion,
            int livenessScore,
            Boolean isLiveness,
            Callback callback
    ) {
        mImageQueue.clear();
        isProcessingFrames = true;
        hasSentCaptureResult.set(false);
        boolean livenessEnabled = Boolean.TRUE.equals(isLiveness);
        try {
            engine.setFacesDetectLiveness(livenessEnabled);
            if (livenessEnabled) {
                engine.setFacesLivenessMode(NLivenessMode.PASSIVE);
                engine.setFacesLivenessThreshold((byte) livenessScore);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Falha ao configurar parâmetros de liveness", e);
        }

        long[] faceDetectedStartTime = {0};
        final boolean[] operationNotActivatedNotified = {false};
        // int stabilityTimeMs = 300;

        float[] lastCenterX = {-1f};
        float[] lastCenterY = {-1f};
        // float stabilityThreshold = 0.03f;

        final int rotationDegrees = getImageRotationDegrees(activity);

        new Thread(() -> {
            while (isProcessingFrames) {
                if (engine == null) {
                    Log.e(LOG_TAG, "Erro: engine não foi inicializado!");
                    callback.onFailure("Erro: engine não inicializado!");
                    return;
                }

                FaceFrame data;
                synchronized (captureLock) {
                    while (mImageQueue.isEmpty()) {
                        try {
                            captureLock.wait();
                        } catch (InterruptedException e) {
                            Log.e(LOG_TAG, "Waiting interrupted", e);
                        }
                    }
                    data = mImageQueue.remove(0);
                }

                if (!isProcessingFrames) {
                    Log.d("Camera", "Processamento de frames interrompido.");
                    return;
                }

                if (data == null || data.getBuffer1() == null || data.getBuffer1().length == 0) {
                    Log.e(LOG_TAG, "Erro: Buffer de imagem inválido.");
                    continue;
                }

                NSubject subject = new NSubject();
                NImage image = null;

                try {
                    boolean useYUV = data.getBuffer2() != null && data.getBuffer3() != null;

                    if (useYUV) {
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
                    Log.e(LOG_TAG, "Erro ao criar imagem. Ignorando frame!", ex);
                    continue;
                }

                if (image == null) continue;

                NFace nFace = new NFace();
                nFace.setCaptureOptions(EnumSet.of(NBiometricCaptureOption.STREAM));
                nFace.setImage(image);
                subject.getFaces().add(nFace);

                EnumSet<NBiometricOperation> operations =
                        EnumSet.of(NBiometricOperation.CREATE_TEMPLATE, NBiometricOperation.DETECT_SEGMENTS);
                NBiometricTask task = engine.createTask(operations, subject);
                engine.performTask(task);

                if (task.getStatus() == NBiometricStatus.OK && !subject.getFaces().isEmpty()) {
                    NFace detectedFace = subject.getFaces().get(0);
                    Byte liveness = detectedFace.getObjects().isEmpty()
                            ? 0
                            : detectedFace.getObjects().get(0).getLivenessScore();
                    int score = liveness;
                    Log.d("LivenessScore", String.valueOf(score));

                    if (!detectedFace.getObjects().isEmpty() && (!livenessEnabled || score > livenessScore)) {
                        NLAttributes faceAttributes = detectedFace.getObjects().get(0);
                        Rect boundingRect = faceAttributes.getBoundingRect();
                        int faceWidth = boundingRect.width();
                        int imageWidth = image.getWidth();

                        float faceRatio = (float) faceWidth / imageWidth;
                        Log.d("FaceDetection", "Face width: " + faceWidth +
                                ", Image width: " + imageWidth + ", Ratio: " + faceRatio);

                        float centerX = boundingRect.centerX() / (float) imageWidth;
                        float centerY = boundingRect.centerY() / (float) image.getHeight();

                        if (faceRatio < minimumFaceProportion) {
                            activity.runOnUiThread(() -> {
                                statusTextView.setText("Aproxime o rosto");
                                faceOverlayView.setBorderColor(Color.YELLOW);
                            });
                            Log.d("FaceDetection", "Rosto muito distante da câmera. Ignorando frame.");
                            continue;
                        }

                        if (lastCenterX[0] >= 0 && lastCenterY[0] >= 0) {
                            float deltaX = Math.abs(centerX - lastCenterX[0]);
                            float deltaY = Math.abs(centerY - lastCenterY[0]);

                            if (deltaX > stabilityThreshold || deltaY > stabilityThreshold) {
                                faceDetectedStartTime[0] = 0;
                                activity.runOnUiThread(() -> {
                                    statusTextView.setText("Mantenha o rosto estável");
                                    faceOverlayView.setBorderColor(Color.YELLOW);
                                });
                            }
                        }
                        lastCenterX[0] = centerX;
                        lastCenterY[0] = centerY;

                        if (faceDetectedStartTime[0] == 0) {
                            faceDetectedStartTime[0] = System.currentTimeMillis();
                            activity.runOnUiThread(() -> {
                                statusTextView.setText("Aguardando estabilidade...");
                                faceOverlayView.setBorderColor(Color.YELLOW);
                            });
                        }

                        long duration = System.currentTimeMillis() - faceDetectedStartTime[0];
                        if (duration >= stabilityTimeMs) {
                            activity.runOnUiThread(() -> {
                                statusTextView.setText("Capturando imagem...");
                                faceOverlayView.setBorderColor(Color.GREEN);
                            });

                            String base64Image = convertNImageToBase64(image);

                            if (hasSentCaptureResult.compareAndSet(false, true)) {
                                isProcessingFrames = false;
                                callback.onSuccess(base64Image);
                            } else {
                                isProcessingFrames = false;
                                Log.w(LOG_TAG, "Resultado de captura já enviado, ignorando frame duplicado.");
                            }
                            return;
                        }
                    } else {
                        Log.e(LOG_TAG, "Nenhum objeto facial detectado.");
                        activity.runOnUiThread(() -> {
                            String message = livenessEnabled
                                    ? "Rosto não detectado. LivenessScore: " + livenessScore
                                    : "Rosto não detectado";
                            statusTextView.setText(message);
                            faceOverlayView.setBorderColor(Color.RED);
                        });
                        faceDetectedStartTime[0] = 0;
                    }
                } else {
                    NBiometricStatus st = task.getStatus();
                    Log.e(LOG_TAG, "Reconhecimento não OK: " + st);
                    faceDetectedStartTime[0] = 0;
                    activity.runOnUiThread(() -> {
                        String msg;
                        switch (st) {
                            case SPOOF_DETECTED: msg = "Possível spoof detectado"; break;
                            case OCCLUSION: msg = "Rosto ocluído"; break;
                            case OBJECT_NOT_FOUND: msg = "Rosto não detectado"; break;
                            case OPERATION_NOT_ACTIVATED: msg = "Conecte-se a internet para poder marcar novamente"; break;
                            default: msg = "Ajuste seu rosto no enquadramento"; break;
                        }
                        statusTextView.setText(msg);
                        faceOverlayView.setBorderColor(Color.RED);
                    });

                    if (st == NBiometricStatus.OPERATION_NOT_ACTIVATED && !operationNotActivatedNotified[0]) {
                        operationNotActivatedNotified[0] = true;
                        callback.onEvent("operation_not_activated");
                    }
                }
            }
        }).start();
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(LOG_TAG, "Bitmap nulo ou já reciclado. Não é possível rotacionar.");
            return null;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);

        try {
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
            return rotatedBitmap;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Erro ao rotacionar bitmap", e);
            return bitmap;
        }
    }

    private Bitmap nImageToBitmap(NImage nImage) {
        byte[] imageBytes = nImage.save(NImageFormat.getJPEG()).toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private int getJpegOrientation(Context context, String cameraId) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int deviceOrientation = windowManager.getDefaultDisplay().getRotation();

            int degrees = 0;
            switch (deviceOrientation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            return (sensorOrientation - degrees + 360) % 360;
        } catch (CameraAccessException e) {
            Log.e(LOG_TAG, "Erro ao obter rotação", e);
            return 0;
        }
    }

    public void stopBackgroundThread() {
        if (mBackgroundCameraHandler != null) {
            mBackgroundCameraHandler.getLooper().quitSafely();
            mBackgroundCameraHandler = null;
        }
        if (mBackgroundCameraThread != null) {
            mBackgroundCameraThread.quitSafely();
            try {
                mBackgroundCameraThread.join();
                mBackgroundCameraThread = null;
            } catch (InterruptedException e) {
                Log.e("NeurotechnologyService", "Erro ao parar a thread de fundo", e);
            }
        }
    }

    public void closeCameraView(Activity activity, AutoFitTextureView textureView, FaceOverlayView faceOverlayView) {
        closeCamera();
        activity.runOnUiThread(() -> {
            try {
                if (textureView != null) {
                    textureView.setVisibility(View.GONE);
                }
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

    public void setBiometricClient(NBiometricClient biometricClient) {
        this.biometricClient = biometricClient;
    }

    public void setCompletionHandler(CompletionHandler<NBiometricTask, NBiometricOperation> completionHandler) {
        this.completionHandler = completionHandler;
    }

    public NBiometricClient getBiometricClient() {
        return biometricClient;
    }

    public static JSONArray getListIds() {
        JSONArray guids = new JSONArray();
        String[] ids = engine.listIds();

        for (String id : ids) {
            try {
                NSubject subject = new NSubject();
                subject.setId(id);
                engine.get(subject);

                String userDataStr = (String) subject.getProperties().get("UserData");
                if (userDataStr != null) {
                    JSONObject userData = new JSONObject(userDataStr);
                    JSONObject dado = new JSONObject();
                    dado.put("trabalhador", userData.getString("trabalhador"));
                    dado.put("hashfoto", userData.optString("hashfoto", ""));
                    guids.put(dado);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Erro ao ler UserData do ID: " + id, e);
            }
        }
        return guids;
    }

    public static boolean deleteId(String id, Context context) {
        try {
            NBiometricStatus status = engine.delete(id);
            boolean deleted = (status == NBiometricStatus.OK);

            if (deleted) {
                Log.i(LOG_TAG, "Template deletado com sucesso: " + id);
                if (context != null) {
                    engineContext = context.getApplicationContext();
                }
                needsEngineRecreation.set(true);
                Log.i(LOG_TAG, "Engine marcado para recriação após deleção.");
            }

            return deleted;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Erro ao deletar ID: " + id, e);
            return false;
        }
    }

    public void closeCamera() {
        Log.d("Camera", "Fechando câmera...");

        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            Log.d("Camera", "Capture session fechada");
        }

        try {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
                Log.d("Camera", "Camera device fechada");
            }
        } catch (Exception e) {
            Log.e("Camera", "Erro ao fechar o camera device", e);
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

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}
