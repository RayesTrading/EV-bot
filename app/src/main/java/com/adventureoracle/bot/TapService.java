package com.adventureoracle.bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** v0.6 experimental on-device state machine. Actions require OCR/visual evidence; no premium actions. */
public class TapService extends AccessibilityService {
    public static TapService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor = command -> handler.post(command);
    private final com.google.mlkit.vision.text.TextRecognizer ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final Runnable cycle = this::capture;
    private boolean running=false,busy=false,awaiting=false,test=false;
    private int taps=0,scans=0,w=0,h=0,stalls=0,scrolls=0,failures=0;
    private String foreground="",target="com.hwqgrhhjfd.idlefastfood",previous="",lastAction="",phase="WAITING";
    private long lastGesture=0,lastScreenLog=0;
    private int lastX=-1,lastY=-1;
    private SharedPreferences prefs(){return getSharedPreferences("bot",MODE_PRIVATE);}
    private void log(String tag,String msg){BotLog.write(this,tag,msg);}
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;log("INFO","v0.6 vision state machine connected");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e!=null&&(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)){CharSequence p=e.getPackageName();if(p!=null)foreground=p.toString();}}
    @Override public void onInterrupt(){stop("Interrupted");}
    @Override public void onDestroy(){stop("Destroyed");ocr.close();instance=null;super.onDestroy();}
    public boolean isRunning(){return running;}
    public int getCount(){return taps;}
    public void start(){begin(false);}
    public void startCalibration(){begin(true);}
    private void begin(boolean one){stop("Restart");target=prefs().getString("package","com.hwqgrhhjfd.idlefastfood");running=true;test=one;taps=0;scans=0;stalls=0;scrolls=0;failures=0;previous="";lastAction="";lastX=-1;lastY=-1;phase="WAITING";log("START",(one?"ONE TAP TEST":"AUTO v0.6")+" target="+target);schedule(1500);}
    public void stop(String reason){boolean was=running;running=false;busy=false;awaiting=false;handler.removeCallbacks(cycle);if(was)log("STOP",reason+"; taps="+taps+" scans="+scans+" phase="+phase);}
    private void schedule(long ms){handler.removeCallbacks(cycle);if(running)handler.postDelayed(cycle,Math.max(650,ms));}
    private void capture(){
        if(!running||busy||awaiting)return;
        if(taps>=prefs().getInt("limit",5)){stop("Action limit reached");return;}
        if(!target.equals(foreground)){if(++scans%7==0)log("WAIT","Foreground="+foreground+"; open Eatventure from home screen");schedule(1100);return;}
        if(Build.VERSION.SDK_INT<30){stop("Android 11 required");return;}
        busy=true;
        try{takeScreenshot(Display.DEFAULT_DISPLAY,executor,new TakeScreenshotCallback(){
            @Override public void onSuccess(ScreenshotResult r){
                Bitmap image=null;
                try{
                    HardwareBuffer buffer=r.getHardwareBuffer();Bitmap hw=Bitmap.wrapHardwareBuffer(buffer,r.getColorSpace());
                    if(hw!=null)image=hw.copy(Bitmap.Config.ARGB_8888,false);
                    buffer.close();if(hw!=null)hw.recycle();
                    if(image==null){recover("Null screenshot");return;}
                    final Bitmap b=image;w=b.getWidth();h=b.getHeight();
                    if(test){b.recycle();busy=false;int x=w*prefs().getInt("x",50)/100,y=h*prefs().getInt("y",75)/100;act(x,y,"TEST",true);return;}
                    ocr.process(InputImage.fromBitmap(b,0))
                        .addOnSuccessListener(executor,t->{try{decide(t,b);}catch(Exception ex){log("ERROR","Decision: "+ex);}finally{b.recycle();busy=false;if(!awaiting)schedule(prefs().getInt("interval",1500));}})
                        .addOnFailureListener(executor,e->{b.recycle();recover("OCR "+e.getMessage());});
                }catch(Exception ex){if(image!=null&&!image.isRecycled())image.recycle();recover("Screenshot decode "+ex);}
            }
            @Override public void onFailure(int code){recover("Screenshot error "+code);}
        });}catch(Exception e){recover("Screenshot exception "+e);}
    }
    private void recover(String error){busy=false;failures++;log("ERROR",error);if(failures>=8)stop("Repeated screenshot failures");else schedule(2000);}
    private static class Line {String text;Rect box;Line(String t,Rect r){text=t;box=r;}}
    private static class Screen {ArrayList<Line> lines=new ArrayList<>();String all="";}
    private Screen parse(Text t){Screen s=new Screen();StringBuilder b=new StringBuilder();for(Text.TextBlock block:t.getTextBlocks())for(Text.Line line:block.getLines())if(line.getBoundingBox()!=null){String v=line.getText().toLowerCase(Locale.ROOT).trim();s.lines.add(new Line(v,line.getBoundingBox()));b.append(v).append(' ');}s.all=b.toString();return s;}
    private boolean has(Screen s,String... keys){for(String k:keys)if(s.all.contains(k))return true;return false;}
    private Line find(Screen s,String key){for(Line l:s.lines)if(l.text.contains(key))return l;return null;}
    private Line findBelow(Screen s,String key,int minY){for(Line l:s.lines)if(l.box.centerY()>minY&&l.text.contains(key))return l;return null;}
    private boolean premium(Screen s){return has(s,"real money","free trial","remove ads","watch ad","watch video","purchase failed","subscribe","in-app purchase","buy gems","offer ends","special offer");}
    private boolean blue(Bitmap b,int x,int y){int n=0,good=0;for(int dx=-12;dx<=12;dx+=6)for(int dy=-10;dy<=10;dy+=5){int xx=x+dx,yy=y+dy;if(xx<0||xx>=b.getWidth()||yy<0||yy>=b.getHeight())continue;int c=b.getPixel(xx,yy);int red=Color.red(c),green=Color.green(c),bl=Color.blue(c);n++;if(bl>135&&green>90&&bl>red*1.3&&green>red*1.25)good++;}return n>0&&good>=n/3;}
    private boolean gray(Bitmap b,int x,int y){if(x<0||y<0||x>=b.getWidth()||y>=b.getHeight())return true;int c=b.getPixel(x,y);return Math.abs(Color.red(c)-Color.green(c))<18&&Math.abs(Color.green(c)-Color.blue(c))<18;}
    private static double amount(String str){Matcher m=Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\\s*([a-z]{0,3})",Pattern.CASE_INSENSITIVE).matcher(str.replace(",",""));if(!m.find())return -1;double v;try{v=Double.parseDouble(m.group(1));}catch(Exception e){return -1;}String suffix=m.group(2).toLowerCase(Locale.ROOT);if(suffix.isEmpty())return v;if(suffix.equals("k"))return v*1e3;if(suffix.equals("m"))return v*1e6;if(suffix.equals("b"))return v*1e9;if(suffix.equals("t"))return v*1e12;if(suffix.length()==2&&suffix.charAt(0)>='a'&&suffix.charAt(0)<='z'&&suffix.charAt(1)>='a'&&suffix.charAt(1)<='z')return v*Math.pow(1000,5+(suffix.charAt(0)-'a')*26+(suffix.charAt(1)-'a'));return -1;}
    private void decide(Text raw,Bitmap bitmap){
        if(!running||!target.equals(foreground))return;
        scans++;failures=0;Screen s=parse(raw);String words=s.all;
        boolean unchanged=words.equals(previous);stalls=unchanged?stalls+1:0;previous=words;
        if(scans%5==1)log("SCAN","#"+scans+" "+w+"x"+h+" phase="+phase+" text="+words.substring(0,Math.min(180,words.length())));
        if(premium(s)){phase="PREMIUM_BLOCKED";if(scans%5==1)log("SAFE","Ad/premium screen; no action");return;}
        if(has(s,"loading","please wait")&&!has(s,"level","upgrades")){phase="LOADING";return;}
        // Offline earnings: only an ordinary Collect button, never x2/ad.
        if(has(s,"offline earnings","while you were gone")){
            phase="OFFLINE";Line collect=find(s,"collect");if(collect!=null&&!collect.text.contains("x2")&&collect.box.centerY()>h/2){act(collect.box.centerX(),collect.box.centerY(),"OFFLINE_COLLECT",false);}else logOccasionally("Offline popup: no safe Collect target");return;
        }
        // Upgrades list: cost labels are positioned inside blue coin buttons at the right.
        if(has(s,"upgrades")&&!has(s,"upgrade your restaurant")){
            phase="MAIN_UPGRADES";
            ArrayList<Line> prices=new ArrayList<>();for(Line l:s.lines)if(l.box.centerX()>w*0.62&&l.box.centerY()>h*0.33&&l.box.centerY()<h*0.83&&amount(l.text)>0&&blue(bitmap,l.box.centerX(),l.box.centerY()))prices.add(l);
            Collections.sort(prices,Comparator.comparingInt(a->a.box.top));
            if(!prices.isEmpty()){
                Line choice=prices.get(0);int x=choice.box.centerX(),y=choice.box.centerY();
                if(stalls>=4){log("WAIT","Upgrade list unchanged; scroll or exit");if(scrolls<3){scrolls++;swipe(w*3/4,h*3/4,w*3/4,h/2,"UPGRADES_SCROLL");}else close(s);return;}
                log("VISION","Coin upgrade candidate "+choice.text+" at "+x+","+y);act(x,y,"COIN_UPGRADE",false);return;
            }
            if(scrolls<3){scrolls++;swipe(w*3/4,h*3/4,w*3/4,h/2,"UPGRADES_SCROLL");}else close(s);return;
        }
        // Renovation: require both Before and After and an enabled blue button near the bottom.
        if(has(s,"renovate")&&has(s,"before")&&has(s,"after")){
            phase="RENOVATE";Line price=null;for(Line l:s.lines)if(l.box.centerY()>h*.64&&l.box.centerY()<h*.83&&amount(l.text)>0&&blue(bitmap,l.box.centerX(),l.box.centerY()))price=l;
            if(price!=null){log("VISION","Enabled renovate coin button "+price.text);act(price.box.centerX(),price.box.centerY(),"RENOVATE",false);}else logOccasionally("Renovate button disabled/unrecognized; waiting");return;
        }
        // Welcome after a city/restaurant transition: recognize ordinary Continue/Play/Start buttons.
        if(has(s,"welcome","new restaurant","congratulations","next city")){
            phase="WELCOME";Line go=null;for(String label:new String[]{"continue","let's go","lets go","start","play","ok"}){go=findBelow(s,label,h/3);if(go!=null)break;}
            if(go!=null&&blue(bitmap,go.box.centerX(),go.box.centerY())){act(go.box.centerX(),go.box.centerY(),"WELCOME_CONTINUE",false);}else logOccasionally("Welcome screen; waiting for a safe blue continue button");return;
        }
        // Station level panel: read LEVEL/MAX and find blue cost button in lower half.
        if(has(s,"level")&&!has(s,"upgrades")){
            phase="STATION";
            if(has(s,"max")){logOccasionally("Station MAX: close and select another");close(s);return;}
            ArrayList<Line> candidates=new ArrayList<>();for(Line l:s.lines)if(l.box.centerY()>h*.44&&l.box.centerY()<h*.9&&amount(l.text)>0&&blue(bitmap,l.box.centerX(),l.box.centerY()))candidates.add(l);
            if(!candidates.isEmpty()){
                Collections.sort(candidates,(a,b)->Integer.compare(b.box.centerY(),a.box.centerY()));Line buy=candidates.get(0);
                if(stalls>=5){log("WAIT","Station unchanged after taps; moving on");close(s);return;}
                act(buy.box.centerX(),buy.box.centerY(),"STATION_UPGRADE",false);return;
            }
            if(prefs().getBoolean("autoUpgrade",false)&&stalls<3){act(w*prefs().getInt("x",50)/100,h*prefs().getInt("y",75)/100,"FALLBACK_STATION",false);return;}
            logOccasionally("Station panel but no validated coin button");return;
        }
        // Unlock station: text inside an active button, not a guessed offset.
        Line unlock=findBelow(s,"unlock",h/3);
        if(unlock!=null){phase="UNLOCK";if(stalls>=4){logOccasionally("Unlock unchanged; scanning instead of repeating");return;}act(unlock.box.centerX(),unlock.box.centerY(),"UNLOCK",false);return;}
        // Unclassified restaurant: explore by gentle scroll rather than tapping unknown areas.
        phase="EXPLORE";
        if(stalls>4&&stalls%6==0&&scrolls<6){scrolls++;swipe(w/2,h*3/4,w/2,h/3,"RESTAURANT_SCROLL");return;}
        logOccasionally("No confident action; awaiting recognizable screen");
    }
    private void logOccasionally(String msg){if(scans%5==0)log("WAIT",msg);}
    private void close(Screen s){Line x=find(s,"x");if(x!=null&&x.box.centerX()>w*.65&&x.box.centerY()<h*.5){act(x.box.centerX(),x.box.centerY(),"CLOSE",false);}else logOccasionally("No reliable close button");}
    private void act(int x,int y,String reason,boolean one){gesture(x,y,x,y,120,reason,one);}
    private void swipe(int x,int y,int toX,int toY,String reason){gesture(x,y,toX,toY,430,reason,false);}
    private void gesture(int x,int y,int ex,int ey,int duration,String reason,boolean one){
        if(!running||awaiting||!target.equals(foreground))return;
        if(System.currentTimeMillis()-lastGesture<850){schedule(1000);return;}
        if(x<0||x>=w||y<0||y>=h||ex<0||ex>=w||ey<0||ey>=h)return;
        awaiting=true;lastGesture=System.currentTimeMillis();lastAction=reason;lastX=x;lastY=y;
        Path path=new Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,duration);
        boolean ok=dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){awaiting=false;taps++;log("ACTION",reason+" #"+taps+" ("+x+","+y+") -> ("+ex+","+ey+") Android completed; game result not yet verified");if(one)stop("One-tap test done");else schedule(reason.equals("RENOVATE")?5000:prefs().getInt("interval",1500));}
            @Override public void onCancelled(GestureDescription g){awaiting=false;log("ERROR","Gesture cancelled "+reason);if(one)stop("Test cancelled");else schedule(2000);}
        },handler);
        if(!ok){awaiting=false;log("ERROR","Gesture rejected "+reason);if(one)stop("Test rejected");else schedule(2000);}
    }
}
