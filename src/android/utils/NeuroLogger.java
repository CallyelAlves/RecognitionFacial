package com.cordova.neurotechnology.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NeuroLogger {

    private static final String DEFAULT_TAG = "Neurotechnology";
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private static final SimpleDateFormat LOG_LINE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static Context appContext;
    private static File logFile;
    private static Uri logUri;

    private NeuroLogger() {
        // Classe utilitária
    }

    public static void initialize(Context context) {
        if (context == null) {
            Log.w(DEFAULT_TAG, "Inicialização do NeuroLogger abortada: contexto nulo");
            return;
        }

        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            logFile = null;
            logUri = null;

            String timestamp = FILE_NAME_FORMAT.format(new Date());
            String fileName = "log.Ponto_neurotechnology_" + timestamp + ".txt";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createFileInDownloadsWithMediaStore(fileName);
            } else {
                createFileInPublicDownloads(fileName);
            }

            if (logUri == null && logFile == null) {
                createFileInAppDirectory(fileName);
            }
        }
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
        writeLine("DEBUG", tag, message, null);
    }

    public static void d(String tag, String message, Throwable throwable) {
        Log.d(tag, message, throwable);
        writeLine("DEBUG", tag, message, throwable);
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        writeLine("INFO", tag, message, null);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);
        writeLine("WARN", tag, message, null);
    }

    public static void w(String tag, String message, Throwable throwable) {
        Log.w(tag, message, throwable);
        writeLine("WARN", tag, message, throwable);
    }

    public static void e(String tag, String message) {
        Log.e(tag, message);
        writeLine("ERROR", tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
        writeLine("ERROR", tag, message, throwable);
    }

    private static void createFileInDownloadsWithMediaStore(String fileName) {
        if (appContext == null) {
            return;
        }
        try {
            ContentResolver resolver = appContext.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                logUri = uri;
                writeLine("INFO", DEFAULT_TAG, "Arquivo de log criado em Downloads: " + fileName, null);
            } else {
                Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar arquivo em Downloads via MediaStore");
            }
        } catch (Exception e) {
            Log.e(DEFAULT_TAG, "NeuroLogger falhou ao criar arquivo em Downloads (MediaStore)", e);
            logUri = null;
        }
    }

    private static void createFileInPublicDownloads(String fileName) {
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) {
            Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu resolver a pasta Downloads pública");
            return;
        }

        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar o diretório: " + directory.getAbsolutePath());
            return;
        }

        File file = new File(directory, fileName);
        try {
            if (!file.exists() && !file.createNewFile()) {
                Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar o arquivo de log: " + file.getAbsolutePath());
                return;
            }
            logFile = file;
            writeLine("INFO", DEFAULT_TAG, "Arquivo de log criado em: " + file.getAbsolutePath(), null);
        } catch (IOException e) {
            Log.e(DEFAULT_TAG, "NeuroLogger falhou ao inicializar o arquivo de log", e);
            logFile = null;
        }
    }

    private static void createFileInAppDirectory(String fileName) {
        if (appContext == null) {
            Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu resolver um diretório de fallback");
            return;
        }

        File directory = appContext.getExternalFilesDir(null);
        if (directory == null) {
            directory = appContext.getFilesDir();
        }

        if (directory == null) {
            Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu resolver um diretório para arquivos de log");
            return;
        }

        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar o diretório: " + directory.getAbsolutePath());
            return;
        }

        File file = new File(directory, fileName);
        try {
            if (!file.exists() && !file.createNewFile()) {
                Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar o arquivo de log: " + file.getAbsolutePath());
                return;
            }
            logFile = file;
            writeLine("INFO", DEFAULT_TAG, "Arquivo de log criado em: " + file.getAbsolutePath(), null);
        } catch (IOException e) {
            Log.e(DEFAULT_TAG, "NeuroLogger falhou ao inicializar o arquivo de log", e);
            logFile = null;
        }
    }

    private static void writeLine(String level, String tag, String message, Throwable throwable) {
        String safeTag = tag == null ? DEFAULT_TAG : tag;
        String safeMessage = message == null ? "" : message;
        String timestamp = LOG_LINE_FORMAT.format(new Date());
        StringBuilder builder = new StringBuilder()
                .append(timestamp)
                .append(" ")
                .append(level)
                .append("/")
                .append(safeTag)
                .append(": ")
                .append(safeMessage)
                .append('\n');

        if (throwable != null) {
            builder.append(Log.getStackTraceString(throwable)).append('\n');
        }

        String line = builder.toString();

        synchronized (LOCK) {
            if (logUri != null && appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appendToDownloadsFile(line);
                return;
            }

            if (logFile == null) {
                return;
            }

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.append(line);
            } catch (IOException e) {
                Log.e(DEFAULT_TAG, "NeuroLogger falhou ao gravar linha de log", e);
            }
        }
    }

    private static void appendToDownloadsFile(String line) {
        if (appContext == null || logUri == null) {
            return;
        }
        try (OutputStream output = appContext.getContentResolver().openOutputStream(logUri, "wa");
             OutputStreamWriter writer = output != null
                     ? new OutputStreamWriter(output, StandardCharsets.UTF_8)
                     : null) {
            if (writer == null) {
                Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu abrir o arquivo de log para escrita");
                return;
            }
            writer.write(line);
            writer.flush();
        } catch (IOException e) {
            Log.e(DEFAULT_TAG, "NeuroLogger falhou ao gravar linha de log em Downloads", e);
        }
    }
}

