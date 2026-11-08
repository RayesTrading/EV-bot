package com.adventureoracle.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executor;

/** v0.5: visible tap calibration, OCR-targeted unlock, guarded user-calibrated upgrades. */
public class TapService extends AccessibilityService {
    public static TapService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor = command -> handler.post(command);
    private final com.google.mlkit.vision.text.TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private boolean running=false, busy=false, calibration=false, awaitingGesture=false;
    private int count=0, scans=0, identical=0, width=0, height=0;
    private String foreground="", target="com.hwqgrhhjfd.idlefastfood", lastWords="", lastAction="";
    private long lastTapAt=0;
    private final Runnable cycle = this::capture;
    private SharedPreferences prefs(){return getSharedPreferences("bot",MODE_PRIVATE);}
    private void log(String type,String msg){BotLog.write(this,type,msg);}
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;log("INFO","v0.5 service connected");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e!=null && (e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)){
            CharSequence p=e.getPackageName();if(p!=null)foreground=p.toString();
        }
    }
    @Override public void onInterrupt(){stop("Accessibility interrupted");}
    @Override public void onDestroy(){stop("Service destroyed");recognizer.close();instance=null;super.onDestroy();}
    public void start(){startMode(false);}
    public void startCalibration(){startMode(true);}
    private void startMode(boolean test){stop("Restart");target=prefs().getString("package","com.hwqgrhhjfd.idlefastfood");calibration=test;running=true;count=0;scans=0;identical=0;lastWords="";lastAction="";log("START",test?"ONE TEST TAP: open game now":"v0.5 OCR + calibrated upgrade target="+target);schedule(1500);}
    public void stop(String why){boolean was=running;running=false;busy=false;awaitingGesture=false;handler.removeCallbacks(cycle);if(was)log("STOP",why+"; taps="+count+"; scans="+scans);}
    public boolean isRunning(){return running;}
    public int getCount(){return count;}
    private void schedule(long delay){handler.removeCallbacks(cycle);if(running)handler.postDelayed(cycle,Math.max(500,delay));}
    private void capture(){
        if(!running || busy || awaitingGesture)return;
        if(count>=prefs().getInt("limit",5)){stop("Tap limit reached");return;}
        if(!target.equals(foreground)){if(++scans%8==0)log("WAIT","Foreground="+foreground+"; waiting for "+target);schedule(1200);return;}
        if(Build.VERSION.SDK_INT<30){stop("Screenshot requires Android 11");return;}
        busy=true;
        try{takeScreenshot(Display.DEFAULT_DISPLAY,executor,new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult result){
                Bitmap b=null;
                try{
                    HardwareBuffer buffer=result.getHardwareBuffer();
                    Bitmap hw=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                    if(hw!=null)b=hw.copy(Bitmap.Config.ARGB_8888,false);
                    buffer.close();
                    if(hw!=null)hw.recycle();
                    if(b==null){busy=false;log("ERROR","Screenshot bitmap null");schedule(1800);return;}
                    final Bitmap image=b;width=image.getWidth();height=image.getHeight();
                    if(calibration){image.recycle();busy=false;int x=width*prefs().getInt("x",50)/100;int y=height*prefs().getInt("y",75)/100;log("TEST","Sending one tap at "+x+","+y+" on "+width+"x"+height);tap(x,y,"TEST",true);return;}
                    recognizer.process(InputImage.fromBitmap(image,0))
                        .addOnSuccessListener(executor,t->{try{decide(t,width,height);}catch(Exception ex){log("ERROR","Decision: "+ex);}finally{image.recycle();busy=false;if(!awaitingGesture)schedule(prefs().getInt("interval",1500));}})
                        .addOnFailureListener(executor,e->{image.recycle();busy=false;log("ERROR","OCR: "+e);schedule(2000);});
                }catch(Exception ex){if(b!=null&&!b.isRecycled())b.recycle();busy=false;log("ERROR","Decode: "+ex);schedule(2000);}
            }
            @Override public void onFailure(int code){busy=false;log("ERROR","Screenshot failure "+code);schedule(2000);}
        });}catch(Exception e){busy=false;log("ERROR","Screenshot exception "+e);schedule(2000);}
    }
    private static class Line {final String s;final Rect r;Line(String s,Rect r){this.s=s;this.r=r;}}
    private void decide(Text result,int w,int h){
        if(!running||!target.equals(foreground))return;
        scans++;ArrayList<Line> lines=new ArrayList<>();StringBuilder sb=new StringBuilder();
        for(Text.TextBlock b:result.getTextBlocks())for(Text.Line l:b.getLines())if(l.getBoundingBox()!=null){String s=l.getText().toLowerCase(Locale.ROOT).trim();lines.add(new Line(s,l.getBoundingBox()));sb.append(s).append(' ');}
        String words=sb.toString();identical=words.equals(lastWords)?identical+1:0;lastWords=words;
        if(scans%6==1)log("SCAN","#"+scans+" "+w+"x"+h+" text="+words.substring(0,Math.min(words.length(),150)));
        if(words.contains("purchase")||words.contains("real money")||words.contains("free trial")||words.contains("watch ad")||words.contains("remove ads")||words.contains("shop")&&words.contains("gem")){log("SAFE","Potential purchase/ad screen: no taps");return;}
        if(words.contains("offline earnings")||words.contains("while you were gone")){log("WAIT","Offline earnings popup: dismiss manually");return;}
        if(words.contains("renovate")){log("WAIT","Renovate detected; manual confirmation required");return;}
        if(words.contains("level")&&words.contains("max")){log("WAIT","MAX station; select another station manually in v0.5");return;}
        if(words.contains("level")&&!words.contains("max")){
            if(!prefs().getBoolean("autoUpgrade",false)){log("WAIT","Upgrade panel recognized; enable calibrated auto-upgrade after testing");return;}
            int x=w*prefs().getInt("x",50)/100,y=h*prefs().getInt("y",75)/100;
            if(identical>=5){log("WAIT","No screen change after repeated upgrades; stopping to prevent blind tapping");stop("Stagnant upgrade screen");return;}
            log("VISION","Upgrade panel; calibrated tap "+x+","+y);tap(x,y,"UPGRADE",false);return;
        }
        // The v0.4 bug tapped BELOW the OCR label. v0.5 taps the centre of the text box.
        Line unlock=null;
        for(Line l:lines)if(l.s.equals("unlock") && l.r.top>h/3 && l.r.width()>20){unlock=l;break;}
        if(unlock!=null){int x=unlock.r.centerX(),y=unlock.r.centerY();
            if(identical>=3){log("WAIT","Unlock unchanged after 3 scans; stopping instead of blind tapping");stop("Unlock unchanged");return;}
            log("VISION","Unlock label CENTER="+x+","+y+" bounds="+unlock.r);tap(x,y,"UNLOCK",false);return;}
        if(scans%5==0)log("WAIT","No actionable OCR target; waiting");
    }
    private void tap(int x,int y,String reason,boolean test){
        if(!running||!target.equals(foreground)||awaitingGesture)return;
        if(System.currentTimeMillis()-lastTapAt<900){schedule(1200);return;}
        if(x<0||y<0||x>=width||y>=height){log("ERROR","Invalid tap coordinates "+x+","+y);return;}
        awaitingGesture=true;lastTapAt=System.currentTimeMillis();lastAction=reason;
        Path p=new Path();p.moveTo(x,y);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(p,0,120);
        boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){awaitingGesture=false;count++;log("TAP",reason+" completed #"+count+" at "+x+","+y+" (Android accepted; verify game reacted)");if(test)stop("One-tap test completed");else schedule(prefs().getInt("interval",1500));}
            @Override public void onCancelled(GestureDescription g){awaitingGesture=false;log("ERROR","Gesture cancelled at "+x+","+y);if(test)stop("Test cancelled");else schedule(1600);}
        },handler);
        if(!accepted){awaitingGesture=false;log("ERROR","Gesture rejected by Android");if(test)stop("Test rejected");else schedule(1800);}
    }
}
