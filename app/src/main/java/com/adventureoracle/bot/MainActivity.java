package com.adventureoracle.bot;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout layout;
    private EditText pkg, x, y, interval, limit;
    private CheckBox autoUpgrade;
    private TextView status, diagnostics;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        BotLog.initialize(this);
        prefs = getSharedPreferences("bot", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(28,28,28,28);
        scroll.addView(layout); setContentView(scroll);
        title("Adventure Oracle Bot — Autonomous Alpha 0.6");
        note("TCL 10 Pro testing. OCR + visual button detection: stations, upgrades, unlock, renovate and city transitions. Experimental: supervise and use STOP if it misidentifies a screen.");
        pkg = field("Eatventure package name", prefs.getString("package", "com.hwqgrhhjfd.idlefastfood"));
        x = field("Fallback tap X (% of screen width, 0–100)", String.valueOf(prefs.getInt("x",50)));
        y = field("Fallback tap Y (% of screen height, 0–100)", String.valueOf(prefs.getInt("y",75)));
        interval = field("Interval between taps (milliseconds, 500–10000)", String.valueOf(prefs.getInt("interval",1500)));
        limit = field("Maximum taps per session (1–500)", String.valueOf(prefs.getInt("limit",5)));
        autoUpgrade = new CheckBox(this); autoUpgrade.setText("Enable fallback station tapping at X/Y (experimental)"); autoUpgrade.setChecked(prefs.getBoolean("autoUpgrade",false)); layout.addView(autoUpgrade);
        button("1. Enable Accessibility Service", () -> { BotLog.write(this,"INFO","Opening Accessibility settings"); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); });
        button("2. Save settings", this::save);
        button("3. ONE TEST TAP — switch to Eatventure", () -> {
            if (!save()) return;
            if (TapService.instance == null) { showStatus("Enable Accessibility first"); return; }
            TapService.instance.startCalibration();
            showStatus("Switch to Eatventure. One tap will be sent to your chosen X/Y. Verify visible effect.");
        });
        button("4. START AUTONOMOUS BOT — switch to Eatventure", () -> {
            if (!save()) return;
            if (TapService.instance == null) { showStatus("ERROR: Accessibility Service is not connected. Open Android Accessibility settings, enable Adventure Oracle Bot, then return."); BotLog.write(this,"ERROR","Start rejected: service not connected"); return; }
            TapService.instance.start();
            showStatus("Autonomous alpha started. Use Home then open Eatventure; supervise it.");
        });
        button("STOP BOT", () -> { if (TapService.instance != null) TapService.instance.stop("Stopped from app"); showStatus("Stopped by user."); });
        status = new TextView(this); status.setTextSize(16); status.setPadding(0,16,0,16); layout.addView(status);
        title("Diagnostics / error reporting");
        note("Every start, stop, tap, permission problem, package mismatch and gesture failure is recorded. The log is stored privately inside the app; use Share report to save it to Files or send it for debugging.");
        button("Refresh diagnostics", this::refreshDiagnostics);
        button("Share full debug report", this::shareLog);
        button("Copy report text", () -> { android.content.ClipboardManager cb = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); cb.setPrimaryClip(android.content.ClipData.newPlainText("Adventure Oracle debug", BotLog.read(this))); Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show(); });
        diagnostics = new TextView(this); diagnostics.setTextSize(12); diagnostics.setTextIsSelectable(true); diagnostics.setPadding(0,16,0,16); layout.addView(diagnostics);
        note("Alpha 0.6 uses OCR + visual color checks. Station upgrades, ordinary coin upgrades, unlock, renovate and welcome are attempted when confidently identified. No ads, gems or real-money purchases. Fallback X/Y is optional.");
        refreshDiagnostics();
    }
    private void title(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(22); t.setPadding(0,8,0,16); layout.addView(t); }
    private void note(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(14); t.setPadding(0,10,0,16); layout.addView(t); }
    private EditText field(String label, String value) { note(label); EditText e = new EditText(this); e.setSingleLine(true); e.setText(value); layout.addView(e); return e; }
    private void button(String name, Runnable action) { Button b = new Button(this); b.setText(name); b.setOnClickListener(v -> action.run()); layout.addView(b); }
    private void showStatus(String msg) { if (status != null) status.setText(msg); Toast.makeText(this,msg,Toast.LENGTH_LONG).show(); }
    private void refreshDiagnostics() {
        if (diagnostics == null) return;
        String service = TapService.instance == null ? "NOT CONNECTED" : (TapService.instance.isRunning() ? "RUNNING" : "CONNECTED / STOPPED");
        diagnostics.setText("Accessibility: " + service + "\nSuccessful taps: " + (TapService.instance == null ? "0" : TapService.instance.getCount()) + "\n\nRecent events:\n" + BotLog.read(this));
    }
    private void shareLog() {
        try {
            BotLog.write(this,"INFO","User exported debug report");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", BotLog.file(this));
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain"); send.putExtra(Intent.EXTRA_STREAM,uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,"Save or share Adventure Oracle debug log"));
        } catch (Exception e) { BotLog.write(this,"ERROR","Report sharing failed: " + e); showStatus("Could not share file. Use Copy report text instead."); }
    }
    private boolean save() {
        try {
            int xx = Integer.parseInt(x.getText().toString().trim()), yy = Integer.parseInt(y.getText().toString().trim());
            int ms = Integer.parseInt(interval.getText().toString().trim()), max = Integer.parseInt(limit.getText().toString().trim());
            String packageName = pkg.getText().toString().trim();
            if (xx < 0 || xx > 100 || yy < 0 || yy > 100 || ms < 500 || ms > 10000 || max < 1 || max > 500 || packageName.isEmpty()) throw new IllegalArgumentException("Invalid setting range");
            prefs.edit().putInt("x",xx).putInt("y",yy).putInt("interval",ms).putInt("limit",max).putString("package",packageName).putBoolean("autoUpgrade",autoUpgrade.isChecked()).apply();
            BotLog.write(this,"INFO","Settings saved: package="+packageName+", x="+xx+"%, y="+yy+"%, interval="+ms+"ms, limit="+max);
            Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show(); return true;
        } catch (Exception e) { BotLog.write(this,"ERROR","Invalid settings: " + e.getMessage()); showStatus("ERROR: Check numeric settings and allowed ranges."); return false; }
    }
    @Override protected void onResume() { super.onResume(); if (TapService.instance != null && TapService.instance.isRunning()) TapService.instance.stop("Returned to bot app"); refreshDiagnostics(); }
}
