package com.adventureoracle.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executor;

/** Experimental, supervised OCR automation. Never taps unknown/premium dialogs. */
public class TapService extends AccessibilityService {
    public static TapService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor callbackExecutor = command -> handler.post(command);
    private final com.google.mlkit.vision.text.TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private boolean running=false, busy=false;
    private int count=0, stagnant=0, stationIndex=0;
    private String foregroundPackage="", allowedPackage="com.hwqgrhhjfd.idlefastfood", previousText="";
    private long lastAction=0;
    private final Runnable cycle = new Runnable() { @Override public void run() { capture(); } };
    private SharedPreferences prefs(){return getSharedPreferences("bot",MODE_PRIVATE);}
    private void log(String kind,String s){BotLog.write(this,kind,s);}
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;log("INFO","Vision service connected");}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null)return;
        if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED){
            CharSequence p=event.getPackageName(); if(p!=null)foregroundPackage=p.toString();
        }
    }
    @Override public void onInterrupt(){stop("Service interrupted");}
    @Override public void onDestroy(){stop("Service destroyed");recognizer.close();instance=null;super.onDestroy();}
    public void start(){stop("Restarting");allowedPackage=prefs().getString("package","com.hwqgrhhjfd.idlefastfood");count=0;stagnant=0;stationIndex=0;previousText="";running=true;log("START","Vision beta target="+allowedPackage);schedule(1300);}
    public void stop(String reason){boolean was=running;running=false;busy=false;handler.removeCallbacks(cycle);if(was)log("STOP",reason+"; taps="+count);}
    public boolean isRunning(){return running;}
    public int getCount(){return count;}
    private void schedule(long delay){handler.removeCallbacks(cycle);if(running)handler.postDelayed(cycle,delay);}
    private void capture(){
        if(!running||busy)return;
        if(count>=prefs().getInt("limit",5)){stop("Tap limit reached");return;}
        if(!allowedPackage.equals(foregroundPackage)){schedule(1100);return;}
        if(Build.VERSION.SDK_INT<30){stop("Screenshot requires Android 11+");return;}
        busy=true;
        try{
            takeScreenshot(Display.DEFAULT_DISPLAY,callbackExecutor,new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result){
                    Bitmap bitmap=null;
                    try{
                        android.hardware.HardwareBuffer buffer=result.getHardwareBuffer();
                        Bitmap hw=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
                        if(hw!=null)bitmap=hw.copy(Bitmap.Config.ARGB_8888,false);
                        buffer.close();
                        if(hw!=null)hw.recycle();
                        if(bitmap==null){busy=false;log("ERROR","Cannot decode screenshot");schedule(1800);return;}
                        final Bitmap finalBitmap=bitmap;
                        recognizer.process(InputImage.fromBitmap(finalBitmap,0))
                            .addOnSuccessListener(callbackExecutor, text->{try{decide(text,finalBitmap.getWidth(),finalBitmap.getHeight());}finally{finalBitmap.recycle();busy=false;schedule(prefs().getInt("interval",1500));}})
                            .addOnFailureListener(callbackExecutor, e->{finalBitmap.recycle();busy=false;log("ERROR","OCR: "+e.getMessage());schedule(2200);});
                    }catch(Exception e){if(bitmap!=null)bitmap.recycle();busy=false;log("ERROR","Screenshot decode: "+e);schedule(2200);}
                }
                @Override public void onFailure(int code){busy=false;log("ERROR","Screenshot failure code="+code);schedule(2200);}
            });
        }catch(Exception e){busy=false;log("ERROR","Screenshot: "+e);schedule(2000);}
    }
    private static class Item {String s;Rect r;Item(String s,Rect r){this.s=s;this.r=r;}}
    private void decide(Text result,int w,int h){
        if(!running || !allowedPackage.equals(foregroundPackage))return;
        ArrayList<Item> items=new ArrayList<>();StringBuilder all=new StringBuilder();
        for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
            if(line.getBoundingBox()!=null){String s=line.getText().toLowerCase(Locale.ROOT).trim();items.add(new Item(s,line.getBoundingBox()));all.append(s).append(' ');}
        }
        String words=all.toString();
        if(words.equals(previousText))stagnant++;else stagnant=0;
        previousText=words;
        if(words.contains("purchase")||words.contains("real money")||words.contains("watch ad")||words.contains("free trial")||words.contains("confirm purchase")){log("SAFE","Unknown or premium dialog: no action");return;}
        // Renovation is permitted only when a blue enabled button and the text of the next restaurant are present.
        if(words.contains("renovate") && words.contains("before") && words.contains("after")){
            Item button=find(items,"renovate",h/2);
            if(button!=null){
                // Pixel sampling to avoid disabled grey buttons is deliberately conservative.
                log("WAIT","Renovate screen recognized; manual confirmation required in beta");
            }
            return;
        }
        // Unlock popup: text near the center; button is immediately beneath the label.
        Item unlock=find(items,"unlock",h/3);
        if(unlock!=null){
            int x=Math.min(w-1,Math.max(1,unlock.r.centerX()));
            int y=Math.min(h-1,unlock.r.bottom+Math.round(h*0.045f));
            log("VISION","Unlock detected at "+x+","+y);tap(x,y);return;
        }
        // Station panel with Level and MAX: no upgrades possible here; move to another station.
        if(words.contains("level") && words.contains("max")){
            log("VISION","Station at MAX; selecting next station");
            // Dismiss popup by tapping neutral game area, then retry station selection on next scan.
            tap(Math.round(w*0.50f),Math.round(h*0.39f));return;
        }
        // Upgrade panel: button has a visible price, but OCR cannot yet reliably distinguish every state.
        if(words.contains("level") && !words.contains("max")){
            log("VISION","Station upgrade panel detected; awaiting validated button coordinates");return;
        }
        if(stagnant==0)log("VISION","No supported station popup; OCR sample: "+words.substring(0,Math.min(words.length(),130)));
    }
    private Item find(ArrayList<Item> items,String text,int minY){for(Item i:items)if(i.r.top>minY && i.s.equals(text))return i;return null;}
    private void tap(int x,int y){
        if(!running || !allowedPackage.equals(foregroundPackage))return;
        Path path=new Path();path.moveTo(x,y);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,70);
        boolean ok=dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){count++;log("TAP","Completed "+count+" at "+x+","+y);}
            @Override public void onCancelled(GestureDescription g){log("ERROR","Gesture cancelled");}
        },handler);
        if(!ok)log("ERROR","Gesture rejected");
    }
}
