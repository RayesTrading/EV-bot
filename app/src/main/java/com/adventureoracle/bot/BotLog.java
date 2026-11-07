package com.adventureoracle.bot;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class BotLog {
    private static final String TAG = "AdventureOracleBot";
    private static final String FILE = "adventure_oracle_debug.txt";
    private BotLog() {}
    public static synchronized void write(Context c, String level, String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + " [" + level + "] " + message + "\n";
        Log.i(TAG, line.trim());
        try {
            File file = new File(c.getFilesDir(), FILE);
            if (file.length() > 512000) {
                File previous = new File(c.getFilesDir(), "adventure_oracle_previous.txt");
                if (previous.exists()) previous.delete();
                file.renameTo(previous);
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) { out.write(line.getBytes("UTF-8")); }
        } catch (Exception e) { Log.e(TAG, "Could not write diagnostic log", e); }
    }
    public static File file(Context c) { return new File(c.getFilesDir(), FILE); }
    public static String read(Context c) {
        try {
            File f = file(c); if (!f.exists()) return "No events yet. Start a test session.";
            byte[] bytes = new byte[(int)Math.min(f.length(), 16000)];
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) { r.seek(Math.max(0, f.length() - bytes.length)); r.readFully(bytes); }
            return new String(bytes, "UTF-8");
        } catch (Exception e) { return "Cannot read log: " + e.getMessage(); }
    }
    public static void initialize(Context c) {
        write(c, "INFO", "App started; Android=" + Build.VERSION.RELEASE + "; device=" + Build.MANUFACTURER + " " + Build.MODEL + "; SDK=" + Build.VERSION.SDK_INT);
    }
}
