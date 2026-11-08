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

/** v0.9 experimental navigation engine. All gestures are gated by foreground game and screenshot state. */
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
    private boolean pendingVerification=false;
    private String pendingPhase="", pendingText="";
    private long pendingHash=0;
    private int pendingLevel=-1, ineffective=0;
    private String blockedPhase="";
    private long blockedHash=0;
    private int verified=0, unverified=0;
    private int currentStation=-1, lastStation=-1, stationVisits=0, maxSeen=0, upgradeRound=0, noAffordable=0;
    private boolean preferMain=false, mainMenuAttempted=false, stationMax=false;
    private long lastMenuAttempt=0;
    private int menuPurchases=0, menuScrolls=0, arrowFailures=0;
    private long lastArrowAttempt=0;
    private String lastMenuFirst="";
    private int strategy(){return prefs().getInt("strategy",1);}
    private int speed(){return prefs().getInt("speed",1);}
    private int holdDuration(){return speed()==0?450:speed()==1?950:1700;}
    private String strategyName(){return new String[]{"Sequential","First & Last","Balanced","Adaptive"}[Math.max(0,Math.min(3,strategy()))];}
    private SharedPreferences prefs(){return getSharedPreferences("bot",MODE_PRIVATE);}
    private void log(String tag,String msg){BotLog.write(this,tag,msg);}
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;log("INFO","v0.9 navigation engine connected");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e!=null&&(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)){CharSequence p=e.getPackageName();if(p!=null)foreground=p.toString();}}
    @Override public void onInterrupt(){stop("Interrupted");}
    @Override public void onDestroy(){stop("Destroyed");ocr.close();instance=null;super.onDestroy();}
    public boolean isRunning(){return running;}
    public int getCount(){return taps;}
    public void start(){begin(false);}
    public void startCalibration(){begin(true);}
    private void begin(boolean one){stop("Restart");target=prefs().getString("package","com.hwqgrhhjfd.idlefastfood");running=true;test=one;taps=0;scans=0;stalls=0;scrolls=0;failures=0;previous="";lastAction="";lastX=-1;lastY=-1;pendingVerification=false;ineffective=0;blockedPhase="";blockedHash=0;verified=0;unverified=0;phase="WAITING";currentStation=-1;lastStation=-1;stationVisits=0;maxSeen=0;upgradeRound=0;noAffordable=0;preferMain=false;mainMenuAttempted=false;stationMax=false;menuPurchases=0;menuScrolls=0;arrowFailures=0;lastArrowAttempt=0;lastMenuFirst="";log("CONFIG","strategy="+strategyName()+" speed="+speed()+" mainUpgrades="+prefs().getBoolean("mainUpgrades",true)+" autoRenovate="+prefs().getBoolean("autoRenovate",false));log("START",(one?"ONE TAP TEST":"AUTO v0.9")+" target="+target);schedule(1500);}
    public void stop(String reason){boolean was=running;running=false;busy=false;awaiting=false;handler.removeCallbacks(cycle);if(was)log("STOP",reason+"; taps="+taps+" verified="+verified+" unverified="+unverified+" scans="+scans+" phase="+phase);}
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
    private static final Pattern LEVEL_PATTERN=Pattern.compile("\\blevel\\s*([0-9]+)");
    private int stationLevel(String words){Matcher m=LEVEL_PATTERN.matcher(words);return m.find()?Integer.parseInt(m.group(1)):-1;}
    // Sample the game image outside of the status bar and navigation bar. No images are stored.
    private long fingerprint(Bitmap b){long hash=1469598103934665603L;int ww=b.getWidth(),hh=b.getHeight();for(int row=2;row<18;row++)for(int col=1;col<13;col++){
        int x=col*ww/14,y=row*hh/20;int c=b.getPixel(x,y);int rgb=((Color.red(c)/24)<<8)|((Color.green(c)/24)<<4)|(Color.blue(c)/24);
        hash^=rgb;hash*=1099511628211L;
    }return hash;}
    private void verify(Screen s,Bitmap b){
        if(!pendingVerification)return;
        pendingVerification=false;
        long now=fingerprint(b);int level=stationLevel(s.all);
        boolean levelUp=pendingLevel>=0&&level>pendingLevel;
        boolean screenChanged=now!=pendingHash;
        // A changed image alone may be animation; do not claim a purchase succeeded.
        boolean semanticChanged=levelUp||(!pendingText.equals(s.all)&&!s.all.contains("level")&&pendingPhase.equals("UNLOCK")) || (pendingPhase.equals("COIN_UPGRADE") && !pendingText.equals(s.all) && s.all.contains("upgrades"));
        if(levelUp||semanticChanged){verified++;ineffective=0;blockedPhase="";log("VERIFY",pendingPhase+" CONFIRMED; level="+pendingLevel+"->"+level+" imageChanged="+screenChanged);}
        else{unverified++;ineffective++;log("VERIFY",pendingPhase+" NOT CONFIRMED; imageChanged="+screenChanged+" level="+pendingLevel+"->"+level+" repeat="+ineffective);
            if(ineffective>=2 && !pendingPhase.equals("STATION_HOLD") && !pendingPhase.equals("MAIN_MENU_OPEN") && !pendingPhase.equals("ARROW_SELECT")){blockedPhase=pendingPhase;blockedHash=now;log("SAFE","Pausing repeated "+blockedPhase+" on unchanged screen; no blind retries");}
        }
    }
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
        verify(s,bitmap);
        long currentHash=fingerprint(bitmap);pendingHash=currentHash;
        if(!blockedPhase.isEmpty() && !words.equals(pendingText) && !words.contains("level")){blockedPhase="";ineffective=0;log("VERIFY","Recognized screen context changed; action block cleared");}
        boolean unchanged=words.equals(previous);stalls=unchanged?stalls+1:0;previous=words;
        if(scans%5==1)log("SCAN","#"+scans+" "+w+"x"+h+" phase="+phase+" text="+words.substring(0,Math.min(180,words.length())));
        if(!blockedPhase.isEmpty()){phase="PAUSED_UNVERIFIED";logOccasionally("Paused after ineffective "+blockedPhase+"; change game screen manually or restart");return;}
        if(premium(s)){phase="PREMIUM_BLOCKED";if(scans%5==1)log("SAFE","Ad/premium screen; no action");return;}
        if(has(s,"loading","please wait")&&!has(s,"level","upgrades")){phase="LOADING";return;}
        // Offline earnings: only an ordinary Collect button, never x2/ad.
        if(has(s,"offline earnings","while you were gone")){
            phase="OFFLINE";Line collect=find(s,"collect");if(collect!=null&&!collect.text.contains("x2")&&collect.box.centerY()>h/2){act(collect.box.centerX(),collect.box.centerY(),"OFFLINE_COLLECT",false);}else logOccasionally("Offline popup: no safe Collect target");return;
        }
        // Upgrades list: cost labels are positioned inside blue coin buttons at the right.
        if(has(s,"upgrades")&&!has(s,"upgrade your restaurant")){
            phase="MAIN_UPGRADES";preferMain=false;mainMenuAttempted=false;
            ArrayList<Line> prices=new ArrayList<>();for(Line l:s.lines)if(l.box.centerX()>w*0.62&&l.box.centerY()>h*0.33&&l.box.centerY()<h*0.83&&amount(l.text)>0&&l.text.matches(".*[0-9].*")&&blue(bitmap,l.box.centerX(),l.box.centerY()))prices.add(l);
            Collections.sort(prices,Comparator.comparingInt(a->a.box.top));
            if(!prices.isEmpty()){
                noAffordable=0;Line choice=prices.get(0);
                if(menuPurchases>=10){menuPurchases=0;menuScrolls=0;close(s);return;}int x=choice.box.centerX(),y=choice.box.centerY();
                if(stalls>=4){log("WAIT","Upgrade list unchanged; scroll or exit");if(scrolls<3){scrolls++;swipe(w*3/4,h*3/4,w*3/4,h/2,"UPGRADES_SCROLL");}else close(s);return;}
                log("VISION","Coin upgrade candidate "+choice.text+" at "+x+","+y);menuPurchases++;act(x,y,"COIN_UPGRADE",false);return;
            }
            if(menuScrolls<2){menuScrolls++;swipe(w*3/4,h*3/4,w*3/4,h/2,"UPGRADES_SCROLL");}else {menuScrolls=0;menuPurchases=0;noAffordable++;close(s);}return;
        }
        // Renovation: require both Before and After and an enabled blue button near the bottom.
        if(has(s,"renovate")&&has(s,"before")&&has(s,"after")){
            phase="RENOVATE";Line price=null;for(Line l:s.lines)if(l.box.centerY()>h*.64&&l.box.centerY()<h*.83&&amount(l.text)>0&&l.text.matches(".*[0-9].*")&&blue(bitmap,l.box.centerX(),l.box.centerY()))price=l;
            if(price!=null && prefs().getBoolean("autoRenovate",false) && maxSeen>=2 && stationVisits>=2 && !preferMain && noAffordable>0){log("VISION","Conservative renovation candidate after station checks; this remains heuristic");act(price.box.centerX(),price.box.centerY(),"RENOVATE",false);}else{logOccasionally("Renovation gated: completion of all stations not independently verified; waiting");}return;
        }
        // Welcome after a city/restaurant transition: recognize ordinary Continue/Play/Start buttons.
        if(has(s,"welcome","new restaurant","congratulations","next city")){
            phase="WELCOME";Line go=null;for(String label:new String[]{"continue","let's go","lets go","start","play","ok"}){go=findBelow(s,label,h/3);if(go!=null)break;}
            if(go!=null&&blue(bitmap,go.box.centerX(),go.box.centerY())){act(go.box.centerX(),go.box.centerY(),"WELCOME_CONTINUE",false);}else logOccasionally("Welcome screen; waiting for a safe blue continue button");return;
        }
        // Station panel: sustained press is supported by Eatventure itself.
        if(has(s,"level")&&!has(s,"upgrades")){
            phase="STATION";
            if(has(s,"max")){
                stationMax=true;maxSeen++;preferMain=false;
                log("STATION","MAX recognized; closing and selecting another station");
                close(s);return;
            }
            stationMax=false;
            ArrayList<Line> candidates=new ArrayList<>();
            for(Line l:s.lines)if(l.box.centerY()>h*.44&&l.box.centerY()<h*.90&&amount(l.text)>0&&l.text.matches(".*[0-9].*")&&blue(bitmap,l.box.centerX(),l.box.centerY()))candidates.add(l);
            if(!candidates.isEmpty()){
                Collections.sort(candidates,(a,b)->Integer.compare(b.box.centerY(),a.box.centerY()));
                Line buy=candidates.get(0);
                if(stalls>=3 || ineffective>=2){
                    preferMain=true;ineffective=0;
                    log("STATION","Level not progressing; trying main upgrades / other stations");
                    close(s);return;
                }
                upgradeRound++;
                log("STRATEGY",strategyName()+" station hold #"+upgradeRound+" "+holdDuration()+"ms");
                gesture(buy.box.centerX(),buy.box.centerY(),buy.box.centerX(),buy.box.centerY(),holdDuration(),"STATION_HOLD",false);
                return;
            }
            preferMain=true;log("NAV","Station purchase not blue; closing immediately to switch targets");
            close(s);return;
        }
        // Unlock station: text inside an active button, not a guessed offset.
        Line unlock=findBelow(s,"unlock",h/3);
        if(unlock!=null){phase="UNLOCK";if(stalls>=4){logOccasionally("Unlock unchanged; scanning instead of repeating");return;}act(unlock.box.centerX(),unlock.box.centerY(),"UNLOCK",false);return;}
        // Restaurant world: locate red arrow clusters in the playfield.
        // Restrict to playfield to avoid touching red close buttons / bottom navigation.
        phase="EXPLORE";
        if(preferMain && prefs().getBoolean("mainUpgrades",true)){
            if(System.currentTimeMillis()-lastMenuAttempt>2200){
                lastMenuAttempt=System.currentTimeMillis();mainMenuAttempted=true;
                // Known bottom-right Upgrades control; must be verified by OCR on next scan.
                log("NAV","Opening bottom-right upgrades control (screen-relative candidate)");
                act(Math.round(w*.91f),Math.round(h*.91f),"MAIN_MENU_OPEN",false);return;
            }
        }
        ArrayList<int[]> arrows=redArrowCandidates(bitmap);
        if(!arrows.isEmpty()){
            int[] candidate=chooseArrow(arrows);
            if(candidate!=null){
                currentStation=candidate[0];stationVisits++;
                log("STRATEGY",strategyName()+" arrow candidate #"+stationVisits+" x="+candidate[0]+" y="+candidate[1]+" count="+arrows.size());
                lastArrowAttempt=System.currentTimeMillis();act(candidate[0],Math.min(h-1,candidate[1]+Math.round(h*.035f)),"ARROW_SELECT",false);return;
            }
        }
        if(stalls>4&&stalls%6==0&&scrolls<6){scrolls++;swipe(w/2,h*3/4,w/2,h/3,"RESTAURANT_SCROLL");return;}
        logOccasionally("No confident station arrow; waiting or scrolling");
    }
    // Detect the white upward arrow INSIDE a red circular station badge.
    // Search only the restaurant playfield; do not confuse red notification dots
    // (white exclamation) or the large blue bottom navigation arrow.
    private boolean redBadge(int c){return Color.red(c)>205 && Color.green(c)>42 && Color.green(c)<135 && Color.blue(c)>45 && Color.blue(c)<150 && Color.red(c)>Color.green(c)*1.8f;}
    private boolean whiteGlyph(int c){return Color.red(c)>210 && Color.green(c)>210 && Color.blue(c)>210;}
    private boolean arrowShape(Bitmap b,int cx,int cy,int r){
        int ww=b.getWidth(),hh=b.getHeight();
        if(cx-r<0||cy-r<0||cx+r>=ww||cy+r>=hh)return false;
        int redRing=0, ring=0;
        for(int a=0;a<360;a+=30){double rad=Math.toRadians(a);int x=cx+(int)(r*.72*Math.cos(rad)),y=cy+(int)(r*.72*Math.sin(rad));ring++;if(redBadge(b.getPixel(x,y)))redRing++;}
        if(redRing<8)return false;
        // White stem below the arrowhead, plus a wide upper arrowhead.
        int stem=0,head=0;
        for(int yy=cy;yy<=cy+r/3;yy+=Math.max(2,r/7))if(whiteGlyph(b.getPixel(cx,yy)))stem++;
        for(int dx=-r/3;dx<=r/3;dx+=Math.max(2,r/7))if(whiteGlyph(b.getPixel(cx+dx,cy-r/5)))head++;
        // Exclamation badges lack a broad white arrowhead.
        return stem>=2 && head>=3;
    }
    private ArrayList<int[]> redArrowCandidates(Bitmap b){
        ArrayList<int[]> found=new ArrayList<>();
        int r=Math.max(17,Math.round(w*.028f));
        int step=Math.max(7,r/3);
        for(int yy=Math.round(h*.38f);yy<Math.round(h*.80f);yy+=step){
            for(int xx=Math.round(w*.045f);xx<Math.round(w*.95f);xx+=step){
                if(!arrowShape(b,xx,yy,r))continue;
                boolean near=false;for(int[] a:found)if(Math.abs(a[0]-xx)<r*2&&Math.abs(a[1]-yy)<r*2){near=true;break;}
                if(!near)found.add(new int[]{xx,yy});
            }
        }
        Collections.sort(found,(a,z)->Integer.compare(a[1],z[1]));
        if(scans%5==1)log("ARROWS","Shape-verified station badges="+found.size());
        return found;
    }
    private int[] chooseArrow(ArrayList<int[]> arrows){
        if(arrows.isEmpty())return null;
        int mode=strategy();
        if(mode==0)return arrows.get(0); // Sequential: top-most
        if(mode==1)return stationVisits%2==0?arrows.get(0):arrows.get(arrows.size()-1); // First & Last
        if(mode==2)return arrows.get(stationVisits%arrows.size()); // Balanced rotation
        // Adaptive: prioritize unvisited region; switch to last if progress stalls.
        return arrows.get((stalls>2||ineffective>0)?arrows.size()-1:0);
    }
    private void logOccasionally(String msg){if(scans%5==0)log("WAIT",msg);}
    private void close(Screen s){
        Line x=find(s,"x");
        if(x!=null&&x.box.centerX()>w*.65&&x.box.centerY()<h*.5){act(x.box.centerX(),x.box.centerY(),"CLOSE",false);return;}
        // Most Eatventure panels close by tapping outside. Try Android Back only
        // when a station or Upgrades panel is positively identified.
        if(phase.equals("STATION")||phase.equals("MAIN_UPGRADES")){
            log("NAV","No OCR close glyph; Android Back on confirmed panel");
            boolean ok=performGlobalAction(GLOBAL_ACTION_BACK);
            if(!ok)log("ERROR","Back action rejected");
            schedule(800);
        }else logOccasionally("No reliable close button");
    }
    private void act(int x,int y,String reason,boolean one){gesture(x,y,x,y,120,reason,one);}
    private void swipe(int x,int y,int toX,int toY,String reason){gesture(x,y,toX,toY,430,reason,false);}
    private void gesture(int x,int y,int ex,int ey,int duration,String reason,boolean one){
        if(!running||awaiting||!target.equals(foreground))return;
        if(System.currentTimeMillis()-lastGesture<500){schedule(650);return;}
        if(x<0||x>=w||y<0||y>=h||ex<0||ex>=w||ey<0||ey>=h)return;
        awaiting=true;lastGesture=System.currentTimeMillis();lastAction=reason;lastX=x;lastY=y;
        if(!one && !reason.contains("SCROLL") && !reason.equals("CLOSE")){
            pendingVerification=!reason.equals("ARROW_SELECT")&&!reason.equals("MAIN_MENU_OPEN");pendingPhase=reason;pendingText=previous;pendingLevel=stationLevel(previous);
            // fingerprint is captured in decide() before dispatch.
        }
        Path path=new Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,duration);
        boolean ok=dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){awaiting=false;taps++;log("ACTION",reason+" #"+taps+" ("+x+","+y+") -> ("+ex+","+ey+") Android completed; checking next screenshot");if(one)stop("One-tap test done");else schedule(reason.equals("RENOVATE")?5000:reason.equals("STATION_HOLD")?650:prefs().getInt("interval",1500));}
            @Override public void onCancelled(GestureDescription g){awaiting=false;log("ERROR","Gesture cancelled "+reason);if(one)stop("Test cancelled");else schedule(2000);}
        },handler);
        if(!ok){awaiting=false;log("ERROR","Gesture rejected "+reason);if(one)stop("Test rejected");else schedule(2000);}
    }
}
