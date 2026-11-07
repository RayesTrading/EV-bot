package com.adventureoracle.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class TapService extends AccessibilityService {
    public static TapService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private String foregroundPackage = "";
    private int count = 0;
    private String allowedPackage = "com.hwqgrhhjfd.idlefastfood";
    private String lastWaitingReason = "";
    private final Runnable cycle = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                if (count >= prefs().getInt("limit",5)) { stop("Tap limit reached: " + count); return; }
                AccessibilityNodeInfo root = getRootInActiveWindow();
                CharSequence rootPkg = root == null ? null : root.getPackageName();
                if (root != null) root.recycle();
                // Fail closed: foreground event AND active root must agree when both are available.
                if (!allowedPackage.equals(foregroundPackage) || (rootPkg != null && !allowedPackage.contentEquals(rootPkg))) {
                    String reason = "Waiting for Eatventure: foreground=" + foregroundPackage + ", root=" + rootPkg + ", expected=" + allowedPackage;
                    if (!reason.equals(lastWaitingReason)) { BotLog.write(TapService.this,"WAIT",reason); lastWaitingReason = reason; }
                    handler.postDelayed(this, 800); return;
                }
                lastWaitingReason = "";
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                float x = metrics.widthPixels * prefs().getInt("x",50) / 100f;
                float y = metrics.heightPixels * prefs().getInt("y",75) / 100f;
                Path path = new Path(); path.moveTo(x,y);
                GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path,0,60);
                boolean accepted = dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) {
                        count++; BotLog.write(TapService.this,"TAP","Completed tap " + count + " at " + (int)x + "," + (int)y);
                        if (running) handler.postDelayed(cycle,prefs().getInt("interval",1500));
                    }
                    @Override public void onCancelled(GestureDescription g) { stop("ERROR: Android cancelled tap gesture"); }
                },handler);
                if (!accepted) stop("ERROR: Android rejected gesture dispatch");
            } catch (Exception e) { stop("ERROR: Tap loop failed: " + e); }
        }
    };
    private SharedPreferences prefs() { return getSharedPreferences("bot",MODE_PRIVATE); }
    @Override protected void onServiceConnected() { super.onServiceConnected(); instance=this; BotLog.write(this,"INFO","Accessibility Service connected"); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            CharSequence p=event.getPackageName(); String next=p==null?"":p.toString();
            if (!next.equals(foregroundPackage)) { foregroundPackage=next; BotLog.write(this,"INFO","Foreground package: " + foregroundPackage); }
            if (running) { handler.removeCallbacks(cycle); handler.postDelayed(cycle,700); }
        }
    }
    @Override public void onInterrupt() { stop("Accessibility Service interrupted"); }
    @Override public void onDestroy() { stop("Accessibility Service destroyed"); instance=null; super.onDestroy(); }
    public void start() {
        stop("Restarting session");
        allowedPackage=prefs().getString("package","com.hwqgrhhjfd.idlefastfood").trim();
        if (allowedPackage.isEmpty()) { BotLog.write(this,"ERROR","No target package configured"); return; }
        count=0; lastWaitingReason=""; running=true;
        BotLog.write(this,"START","Target="+allowedPackage+", interval="+prefs().getInt("interval",1500)+"ms, tapLimit="+prefs().getInt("limit",5));
        handler.postDelayed(cycle,1500);
    }
    public void stop(String reason) { boolean wasRunning=running; running=false; handler.removeCallbacks(cycle); if (wasRunning) BotLog.write(this,"STOP",reason+"; completed taps="+count); }
    public boolean isRunning() { return running; }
    public int getCount() { return count; }
}
