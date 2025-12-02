package com.cordova.neurotechnology.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NeuroLogger {

    private static final String DEFAULT_TAG = "Neurotechnology";
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    private static final SimpleDateFormat LOG_LINE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static File logFile;

    private NeuroLogger() {
        // Utility class
    }

    public static void initialize(Context context) {
        if (context == null) {
            Log.w(DEFAULT_TAG, "Inicialização do NeuroLogger abortada: contexto nulo");
            return;
        }

        synchronized (LOCK) {
            try {
                String timestamp = FILE_NAME_FORMAT.format(new Date());
                String fileName = "log.Ponto_neurotechnology_" + timestamp + ".txt";
                File directory = context.getExternalFilesDir(null);
                if (directory == null) {
                    directory = context.getFilesDir();
                }

                if (directory == null) {
                    Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu resolver um diretório para arquivos de log");
                    return;
                }

                if (!directory.exists() && !directory.mkdirs()) {
                    Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar o diretório: " + directory.getAbsolutePath());
                    return;
                }

                logFile = new File(directory, fileName);
                if (!logFile.exists() && !logFile.createNewFile()) {
                    Log.e(DEFAULT_TAG, "NeuroLogger não conseguiu criar o arquivo de log: " + logFile.getAbsolutePath());
                    logFile = null;
                    return;
                }

                writeLine("INFO", DEFAULT_TAG, "Arquivo de log criado em: " + logFile.getAbsolutePath(), null);
            } catch (IOException e) {
                Log.e(DEFAULT_TAG, "NeuroLogger falhou ao inicializar o arquivo de log", e);
                logFile = null;
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

    private static void writeLine(String level, String tag, String message, Throwable throwable) {
        if (logFile == null) {
            return;
        }

        String safeTag = tag == null ? DEFAULT_TAG : tag;
        String safeMessage = message == null ? "" : message;

        synchronized (LOCK) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = LOG_LINE_FORMAT.format(new Date());
                writer.append(timestamp)
                        .append(" ")
                        .append(level)
                        .append("/")
                        .append(safeTag)
                        .append(": ")
                        .append(safeMessage)
                        .append('\n');

                if (throwable != null) {
                    writer.append(Log.getStackTraceString(throwable)).append('\n');
                }
            } catch (IOException e) {
                Log.e(DEFAULT_TAG, "NeuroLogger falhou ao gravar linha de log", e);
            }
        }
    }
}
