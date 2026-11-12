package com.example.galaxyimageautoclick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE=17;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private AutomationConfig config;
    private LinearLayout stepList;
    private TextView status,logs;
    private EditText random,timeout,cycles,scan;
    private CheckBox alert,skip,mini;
    private boolean live=true;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);config=AutomationConfig.load(this);buildUI();
        ui.post(new Runnable(){@Override public void run(){if(live){updateStatus();ui.postDelayed(this,650);}}});
    }
    @Override protected void onDestroy(){live=false;super.onDestroy();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
    private TextView text(String val,int size) {TextView v=new TextView(this);v.setText(val);v.setTextSize(size);v.setTextColor(0xFF203047);v.setPadding(0,dp(7),0,dp(7));return v;}
    private Button button(String label, Runnable action) {Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private EditText num(String value){EditText v=new EditText(this);v.setSingleLine(true);v.setInputType(InputType.TYPE_CLASS_NUMBER);v.setText(value);v.setSelectAllOnFocus(true);return v;}
    private int read(EditText field,int fallback,int low,int high){try{return Math.max(low,Math.min(high,Integer.parseInt(field.getText().toString().trim())));}catch(Exception e){return fallback;}}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void buildUI(){
        ScrollView sv=new ScrollView(this);setContentView(sv);
        LinearLayout root=vertical();root.setPadding(dp(16),dp(12),dp(16),dp(22));root.setBackgroundColor(0xFFF3F6FA);sv.addView(root);
        TextView title=text("갤럭시 이미지 자동클릭",24);title.setTextColor(0xFF164B8D);root.addView(title);
        root.addView(text("Android 11 이상 · 등록한 이미지를 순서대로 검색하고 터치합니다.",13));
        root.addView(button("① 접근성 서비스 설정 열기",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(text("② 이미지 등록 및 순서 설정",18));
        root.addView(button("＋ 이미지 추가 (PNG / JPG)",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i,PICK_IMAGE);
        }));
        stepList=vertical();root.addView(stepList);refreshSteps();
        root.addView(text("③ 자동클릭 설정",18));
        root.addView(text("이미지 중심 랜덤 범위(%) · 권장 15",13));random=num(""+config.randomPercent);root.addView(random);
        root.addView(text("이미지를 못 찾을 때 대기 시간(초)",13));timeout=num(""+config.waitSeconds);root.addView(timeout);
        root.addView(text("전체 순서 반복 횟수 · 0 = 무한",13));cycles=num(""+config.cycleCount);root.addView(cycles);
        root.addView(text("검색 간격(밀리초) · 권장 650",13));scan=num(""+config.scanMs);root.addView(scan);
        alert=new CheckBox(this);alert.setText("미인식 시 경고 상태 표시");alert.setChecked(config.alert);root.addView(alert);
        skip=new CheckBox(this);skip.setText("미인식 시 다음 이미지로 건너뛰기");skip.setChecked(config.skipMissing);root.addView(skip);
        mini=new CheckBox(this);mini.setText("다른 앱 위에 작은 실행 상태창 표시");mini.setChecked(config.miniMonitor);root.addView(mini);
        root.addView(button("설정 저장",()->{saveFields();Toast.makeText(this,"설정 저장 완료",Toast.LENGTH_SHORT).show();}));
        root.addView(text("④ 실행 / 중지",18));
        root.addView(button("▶ 자동클릭 시작",()->{
            saveFields();
            if(config.steps.isEmpty()){showMessage("이미지를 먼저 추가하세요.");return;}
            if(ClickService.instance==null){showMessage("접근성 서비스가 꺼져 있습니다. 먼저 설정에서 이미지 자동클릭 서비스를 켜주세요.");return;}
            ClickService.instance.startAutomation();
            new AlertDialog.Builder(this).setTitle("자동클릭 시작").setMessage("이제 대상 앱을 여세요. 이미지가 화면에 나타나면 순서대로 터치합니다. 작은 상태창의 '중지'를 이용할 수 있습니다.")
                .setPositiveButton("확인",(d,w)->{}).show();
        }));
        root.addView(button("■ 자동클릭 중지",()->{
            if(ClickService.instance!=null)ClickService.instance.stopAutomation();
        }));
        root.addView(text("⑤ 실행 상태 및 동작 기록",18));
        status=text("대기 중",15);root.addView(status);
        logs=text("",12);root.addView(logs);
        root.addView(button("동작 기록 지우기",()->{
            getSharedPreferences(AutomationConfig.PREF,MODE_PRIVATE).edit().remove("logs").apply();
        }));
        root.addView(text("안내: 잠금 화면, 금융·보안 앱, 보호된 화면, 일부 게임에서는 캡처 및 터치가 제한됩니다. 화면과 등록 이미지의 크기가 같아야 인식이 안정적입니다. 사용자가 손으로 터치하면 자동 터치와 충돌할 수 있습니다.",12));
    }
    private void saveFields(){
        config.randomPercent=read(random,15,0,45);config.waitSeconds=read(timeout,30,3,600);
        config.cycleCount=read(cycles,0,0,10000);config.scanMs=read(scan,650,300,5000);
        config.alert=alert.isChecked();config.skipMissing=skip.isChecked();config.miniMonitor=mini.isChecked();config.save(this);
    }
    private void refreshSteps(){
        if(stepList==null)return;stepList.removeAllViews();
        for(int i=0;i<config.steps.size();i++){
            final int p=i;AutomationConfig.Step s=config.steps.get(i);
            LinearLayout group=vertical();group.setPadding(dp(8),dp(6),dp(8),dp(6));group.setBackgroundColor(Color.WHITE);
            group.addView(text((i+1)+". "+s.name+"  /  "+s.clicks+"회  / "+s.gapMs+"ms",14));
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit=button("횟수",()->editStep(p));Button up=button("↑",()->move(p,-1));
            Button down=button("↓",()->move(p,1));Button del=button("삭제",()->{config.steps.remove(p);config.save(this);refreshSteps();});
            for(Button b:new Button[]{edit,up,down,del}) actions.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));
            group.addView(actions);stepList.addView(group);
        }
    }
    private void move(int p,int amount){int q=p+amount;if(q<0||q>=config.steps.size())return;
        java.util.Collections.swap(config.steps,p,q);config.save(this);refreshSteps();}
    private void editStep(int p){
        AutomationConfig.Step s=config.steps.get(p);LinearLayout layout=vertical();layout.setPadding(dp(20),0,dp(20),0);
        layout.addView(text("한 번 인식했을 때 클릭 횟수 (1~100)",13));EditText count=num(""+s.clicks);layout.addView(count);
        layout.addView(text("클릭 사이 간격 (100~5000ms)",13));EditText gap=num(""+s.gapMs);layout.addView(gap);
        new AlertDialog.Builder(this).setTitle(s.name).setView(layout).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{
            s.clicks=read(count,s.clicks,1,100);s.gapMs=read(gap,s.gapMs,100,5000);
            config.save(this);refreshSteps();
        }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try {
            File dir=new File(getFilesDir(),"templates");dir.mkdirs();
            File file=new File(dir,"image_"+System.currentTimeMillis()+".png");
            // Android bitmap decoding normalizes jpg/png to PNG, removing uri permission dependency.
            android.graphics.Bitmap bm;
            try(InputStream in=getContentResolver().openInputStream(uri)) {bm=android.graphics.BitmapFactory.decodeStream(in);}
            if(bm==null)throw new Exception("이미지 읽기 실패");
            if(bm.getWidth()<16||bm.getHeight()<16)throw new Exception("이미지는 최소 16×16 픽셀이어야 합니다");
            try(FileOutputStream out=new FileOutputStream(file)){bm.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            bm.recycle();
            AutomationConfig.Step s=new AutomationConfig.Step();s.file=file.getAbsolutePath();
            s.name="이미지 "+(config.steps.size()+1);config.steps.add(s);config.save(this);refreshSteps();
        } catch(Exception e){showMessage("이미지 추가 실패: "+e.getMessage());}
    }
    private void showMessage(String msg){new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("확인",null).show();}
    private void updateStatus(){
        android.content.SharedPreferences sp=getSharedPreferences(AutomationConfig.PREF,MODE_PRIVATE);
        if(status!=null)status.setText("상태: "+sp.getString("state","대기 중")+"  ·  이미지 "+sp.getInt("step",0)+"  ·  클릭 "+sp.getInt("success",0)+"회");
        if(logs!=null)logs.setText(sp.getString("logs","아직 동작 기록이 없습니다."));
    }
}
