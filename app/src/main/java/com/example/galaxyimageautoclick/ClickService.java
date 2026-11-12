package com.example.galaxyimageautoclick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;

public class ClickService extends AccessibilityService {
    public static ClickService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private boolean running=false, busy=false;
    private int generation=0, index=0, completed=0, clicksSent=0;
    private long stepStart=0;
    private AutomationConfig config;
    private Bitmap template;
    private WindowManager windowManager;
    private View monitor;
    private TextView monitorText;

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); instance=this;
        AutomationConfig.status(this,"대기 중", "접근성 서비스 연결",0,0);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {stopAutomation();}
    @Override public void onDestroy() {stopAutomation(); removeMonitor(); instance=null;worker.shutdownNow();super.onDestroy();}
    public void startAutomation() {
        stopAutomation(); config=AutomationConfig.load(this);
        if(config.steps.isEmpty()) {log("이미지를 먼저 등록하세요");return;}
        generation++; running=true;busy=false;index=0;completed=0;clicksSent=0;
        stepStart=android.os.SystemClock.elapsedRealtime();
        if(config.miniMonitor) showMonitor();
        log("자동클릭 시작"); scan(generation);
    }
    public void stopAutomation() {
        running=false; busy=false;generation++; clearTemplate(); removeMonitor();
        AutomationConfig.status(this,"중지됨",null,index,clicksSent);
    }
    private void log(String s) {
        AutomationConfig.status(this,running?"실행 중":"중지됨",s,index+1,clicksSent);
        if(monitorText!=null) monitorText.setText((running?"● 실행 중":"■ 중지")+"  "+(index+1)+"/"+(config==null?0:config.steps.size())+"  클릭 "+clicksSent+"\n"+s);
    }
    private void clearTemplate() { if(template!=null){template.recycle();template=null;} }
    private void next() {
        clearTemplate(); index++;
        if(index>=config.steps.size()) {
            completed++;index=0;
            if(config.cycleCount>0 && completed>=config.cycleCount){log("지정된 반복 완료");stopAutomation();return;}
        }
        stepStart=android.os.SystemClock.elapsedRealtime();scan(generation);
    }
    private void scan(int token) {
        if(!running || token!=generation || busy) return;
        if(android.os.SystemClock.elapsedRealtime()-stepStart > config.waitSeconds*1000L){
            String msg=(index+1)+"번 이미지 미인식 ("+config.waitSeconds+"초)";
            log(msg);
            if(config.alert) {
                // Non-blocking status overlay + device notification alternative: activity alert when visible.
                AutomationConfig.status(this,"미인식 알림",msg,index+1,clicksSent);
                if(monitorText!=null)monitorText.setText("⚠ "+msg+"\n탭하여 중지");
            }
            if(config.skipMissing){next();}else stopAutomation();return;
        }
        AutomationConfig.Step step=config.steps.get(index);
        if(template==null) {
            template=BitmapFactory.decodeFile(step.file);
            if(template==null){log("파일을 읽을 수 없음: "+step.name);next();return;}
        }
        busy=true;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                @Override public void onSuccess(ScreenshotResult result) {
                    if(token!=generation || !running){busy=false;result.getHardwareBuffer().close();return;}
                    Bitmap frame=null;
                    try {
                        frame=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                        if(frame!=null)frame=frame.copy(Bitmap.Config.ARGB_8888,false);
                    } catch(Exception e){log("화면 처리 오류: "+e.getMessage());}
                    finally {result.getHardwareBuffer().close();}
                    if(frame==null){busy=false;scheduleScan(token);return;}
                    final Bitmap finalFrame=frame;
                    final Bitmap target=template;
                    worker.execute(()-> {
                        ImageMatcher.Result match=null;
                        try {match=ImageMatcher.find(finalFrame,target,0.87);} catch(Exception ignored){}
                        finalFrame.recycle();
                        ImageMatcher.Result found=match;
                        main.post(()-> {
                            busy=false;
                            if(token!=generation || !running)return;
                            if(found==null){scheduleScan(token);return;}
                            log(step.name+" 인식 ("+(int)(found.score*100)+"%)");
                            clickSeries(token,found,step,0);
                        });
                    });
                }
                @Override public void onFailure(int errorCode){
                    busy=false;
                    if(token==generation && running){
                        if(errorCode==ERROR_TAKE_SCREENSHOT_SECURE_WINDOW){log("보호된 화면: 캡처 불가");stopAutomation();}
                        else {log("화면 캡처 실패: "+errorCode);scheduleScan(token);}
                    }
                }
            });
        } catch(Exception e){busy=false;log("캡처 요청 오류: "+e.getMessage());scheduleScan(token);}
    }
    private void scheduleScan(int token){main.postDelayed(()->scan(token),config==null?650:config.scanMs);}
    private void clickSeries(int token,ImageMatcher.Result match,AutomationConfig.Step step,int n){
        if(token!=generation || !running)return;
        if(n>=step.clicks){main.postDelayed(this::next,450);return;}
        double dx=(random.nextDouble()*2-1)*config.randomPercent/100.0;
        double dy=(random.nextDouble()*2-1)*config.randomPercent/100.0;
        float x=(float)(match.x+match.width*(.5+dx));
        float y=(float)(match.y+match.height*(.5+dy));
        Path path=new Path();path.moveTo(x,y);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,85);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(stroke).build();
        boolean sent=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gestureDescription){
                if(token!=generation || !running)return;
                clicksSent++;log(step.name+" 클릭 "+(n+1)+"/"+step.clicks+" ("+(int)x+","+(int)y+")");
                main.postDelayed(()->clickSeries(token,match,step,n+1),step.gapMs);
            }
            @Override public void onCancelled(GestureDescription gestureDescription){
                if(token==generation){log("터치 취소됨: 다른 터치와 충돌할 수 있음");stopAutomation();}
            }
        },main);
        if(!sent){log("터치 전송 실패");stopAutomation();}
    }
    private void showMonitor() {
        if(monitor!=null)return;
        try {
            windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
            LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(18,12,18,12);
            GradientDrawable bg=new GradientDrawable();bg.setColor(0xDD182638);bg.setCornerRadius(18);bar.setBackground(bg);
            TextView label=new TextView(this);label.setTextColor(Color.WHITE);label.setTextSize(12);label.setText("이미지 자동클릭 시작");
            bar.addView(label);monitorText=label;
            TextView stop=new TextView(this);stop.setText("   ■ 중지");stop.setTextColor(0xFFFFC5C5);stop.setTextSize(13);
            bar.addView(stop);stop.setOnClickListener(v->stopAutomation());
            WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.START;lp.x=18;lp.y=70;
            windowManager.addView(bar,lp);monitor=bar;
        } catch(Exception e){monitor=null;monitorText=null;}
    }
    private void removeMonitor(){
        if(monitor!=null && windowManager!=null)try{windowManager.removeView(monitor);}catch(Exception ignored){}
        monitor=null;monitorText=null;
    }
}
