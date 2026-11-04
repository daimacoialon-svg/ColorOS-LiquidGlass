package top.liquidglass.vector;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;

/** V138: material-first settings, with legacy preference keys retained for renderer compatibility. */
public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView rootStatus;
    private final Handler rootStatusHandler=new Handler(Looper.getMainLooper());
    private boolean rootStatusVisible=false;
    private boolean rootStatusBusy=false;
    private final Runnable rootStatusPoll=new Runnable(){
        @Override public void run(){
            if(!rootStatusVisible||rootStatus==null)return;
            if(!rootStatusBusy){
                rootStatusBusy=true;
                RootHelperStatus.checkAsync(MainActivity.this, result->{
                    rootStatusBusy=false;
                    if(rootStatusVisible&&rootStatus!=null)rootStatus.setText(result);
                });
            }
            rootStatusHandler.postDelayed(this,5000);
        }
    };
    @Override protected void onResume(){super.onResume();rootStatusVisible=true;rootStatusHandler.removeCallbacks(rootStatusPoll);rootStatusHandler.post(rootStatusPoll);}
    @Override protected void onStop(){rootStatusVisible=false;rootStatusHandler.removeCallbacks(rootStatusPoll);super.onStop();}

    // Event-only signal: no configuration data is carried by this broadcast.
    // Launcher must fetch the authoritative values from the app provider.
    private void notifyConfigChanged128(){
        getContentResolver().notifyChange(GlassConfigProvider.URI,null);
        try {
            android.content.Intent i=new android.content.Intent("top.liquidglass.vector.CONFIG_CHANGED_128");
            i.setPackage("com.android.launcher");
            sendBroadcast(i);
        }catch(Throwable ignored){}
    }
    private LinearLayout body;
    private final int ink=0xFF172635, muted=0xFF657587, blue=0xFF2979E5;
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(bold?ink:muted);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void section(String title,String desc){
        LinearLayout card=column();card.setPadding(dp(18),dp(16),dp(18),dp(16));card.setBackground(bg(Color.WHITE,22));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));body.addView(card,lp);
        TextView heading=text(title,18,true);card.addView(heading);
        if(desc!=null&&!desc.isEmpty()){TextView sub=text(desc,12,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(12));card.addView(sub,p);}
        current=card;
    }
    private LinearLayout current;
    private void toggle(String label,String key,boolean def){
        Switch s=new Switch(this);s.setText(label);s.setTextColor(ink);s.setTextSize(14);s.setPadding(0,dp(8),0,dp(8));s.setChecked(prefs.getBoolean(key,def));
        s.setOnCheckedChangeListener((v,checked)->{
            prefs.edit().putBoolean(key,checked).commit();
            notifyConfigChanged128();
        });current.addView(s);
    }
    private void slider(String label,String key,int def,int min,int max){
        TextView caption=text("",13,false);current.addView(caption);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(Math.max(0,Math.min(max-min,prefs.getInt(key,def)-min)));current.addView(bar);
        Runnable update=()->caption.setText(label+"  ·  "+(bar.getProgress()+min));update.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean fromUser){update.run();if(fromUser){prefs.edit().putInt(key,progress+min).apply();notifyConfigChanged128();}}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){prefs.edit().putInt(key,b.getProgress()+min).commit();notifyConfigChanged128();}
        });
    }
    // V95: decimal dp is persisted as tenths to avoid SharedPreferences float/int migration issues.
    private void microBlurSlider95(){
        final String key="custom_blur_tenths_95";
        int legacy=prefs.getInt("custom_blur_dp",3);
        int initial=Math.max(0,Math.min(400,prefs.getInt(key,legacy*10)));
        TextView caption=text("",13,false);current.addView(caption);
        SeekBar bar=new SeekBar(this);bar.setMax(400);bar.setProgress(initial);current.addView(bar);
        Runnable update=()->caption.setText(String.format(java.util.Locale.US,"桌面文件夹背景模糊半径  ·  %.1f dp",bar.getProgress()/10f));update.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean fromUser){update.run();if(fromUser){prefs.edit().putInt(key,progress).putInt("custom_blur_dp",Math.round(progress/10f)).apply();notifyConfigChanged128();}}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){prefs.edit().putInt(key,b.getProgress()).putInt("custom_blur_dp",Math.round(b.getProgress()/10f)).commit();notifyConfigChanged128();}
        });
    }
    private void action(String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(blue);b.setOnClickListener(v->run.run());current.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private String styleLabel186(){
        int style=prefs.getInt("optical_style_186",0);
        return style==2?"泡泡液态玻璃":style==1?"iOS 27 风格":"自定义 / 旧版";
    }
    private void applyOpticalStyle186(int style){
        SharedPreferences.Editor e=prefs.edit();
        e.putInt("optical_style_186",style)
         .putInt("nonlinear_107",style==2?86:68)
         .putInt("thickness_107",style==2?82:60)
         .putInt("glass_tint_opacity",style==2?7:9)
         .putInt("surface_mode_195",style==2?3:2)
         .putInt("surface_strength_195",style==2?95:58)
         .putInt("surface_softness_195",style==2?92:145)
         .putInt("custom_blur_tenths_95",style==2?5:12)
         .putInt("custom_blur_dp",style==2?1:1)
         .putInt("custom_blur_mode",1).commit();
        notifyConfigChanged128();
        Toast.makeText(this,"已应用风格；新曲面模式 3 将在配置刷新后生效",Toast.LENGTH_LONG).show();
        recreate();
    }
    @Override protected void onPause(){super.onPause();notifyConfigChanged128();}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("glass",0);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF3F6FA);
        body=column();body.setPadding(dp(18),dp(28),dp(18),dp(32));scroll.addView(body);
        TextView heading=text("Liquid Glass",28,true);body.addView(heading);
        TextView version=text("V1.9.205.188  ·  双风格光学设置",13,false);LinearLayout.LayoutParams v=new LinearLayout.LayoutParams(-1,-2);v.setMargins(0,dp(4),0,dp(20));body.addView(version,v);
        section("一键切换玻璃风格","两种风格共享稳定壁纸采样与抗锯齿。按钮会同时应用光学模式和推荐参数；不会强制启用文件夹或 Dock。");
        TextView activeStyle186=text("当前风格："+styleLabel186(),15,true);current.addView(activeStyle186);
        LinearLayout styleButtons186=new LinearLayout(this);styleButtons186.setOrientation(LinearLayout.HORIZONTAL);current.addView(styleButtons186);
        Button bubble186=new Button(this);bubble186.setAllCaps(false);bubble186.setText("泡泡液态玻璃");
        Button ios186=new Button(this);ios186.setAllCaps(false);ios186.setText("iOS 27 风格");
        styleButtons186.addView(bubble186,new LinearLayout.LayoutParams(0,dp(58),1));
        styleButtons186.addView(ios186,new LinearLayout.LayoutParams(0,dp(58),1));
        bubble186.setOnClickListener(clickedView186 -> applyOpticalStyle186(2));
        ios186.setOnClickListener(clickedView186 -> applyOpticalStyle186(1));
        TextView styleNote186=text("泡泡：连续边缘透镜与环境响应高光。iOS 风格：较弱曲率与柔和反射。两者均为模拟光学风格，并非苹果原生渲染。手动调整下方参数不会自动覆盖这两套预设。",12,false);
        current.addView(styleNote186);
        section("玻璃材质总览","文件夹与 Dock 共用光学参数；两者可分别启用。预设不会强制打开旧版实验模式。");
        toggle("启用桌面文件夹玻璃", "enabled",false);
        TextView presetInfo=text("玻璃预设仅在手动点击时应用；文件夹与 Dock 共用玻璃渲染参数，不改变功能开关。",12,false);current.addView(presetInfo);
        action("应用清透液态玻璃推荐参数",()->{
            prefs.edit().putBoolean("enabled",true)
                .putInt("custom_blur_dp",2).putInt("custom_blur_tenths_95",20).putInt("custom_blur_mode",1)
                .putInt("glass_tint_opacity",6).putInt("tint",0).putInt("tint_strength",0)
                .putInt("nonlinear_107",72).putInt("thickness_107",65)
                .putInt("interior_107",48).putInt("focus_107",45).putInt("reflection_107",22)
                .putInt("refraction_percent",0).putBoolean("real_refraction",false)
                .putInt("surface_mode_195",3).putInt("surface_strength_195",65)
                .putInt("surface_softness_195",100).commit();
            notifyConfigChanged128();
            Toast.makeText(this,"已应用统一透镜推荐参数；Dock 开关保持原状态",Toast.LENGTH_LONG).show();
            recreate();
        });
        TextView presetHint=text("推荐参数是测试起点，不代表已达到 iOS 目标。旧二维折射默认关闭，避免与统一透镜叠加。",12,false);
        current.addView(presetHint);
        
        toggle("启用 Dock 玻璃","dock_glass_204",false);
        TextView dockInfo=text("Dock 保持原有尺寸和位置；V132 与文件夹共用直接背景采样及透镜位移 Shader，并按容器几何自适应。",12,false);current.addView(dockInfo);

        section("二维曲面透镜","统一曲面负责文件夹和 Dock 的背景折射与厚度。反射、高光和底色分别调整，避免把不同效果混在同一滑块。");
        slider("二维曲面折射强度 (%)","nonlinear_107",65,0,100);
        TextView unifiedInfo162=text("0% 为无统一曲面位移；100% 为最强。建议先用 65% 比较 Dock 与各种文件夹。该滑块不控制背景模糊或底色透明度。",12,false);
        current.addView(unifiedInfo162);
        section("玻璃外观","背景模糊和底色独立于折射；应用图标不会被自定义壁纸模糊处理。");
        slider("玻璃底色不透明度 (%)","glass_tint_opacity",18,0,100);
        microBlurSlider95();
        TextView modeLabel=text("背景模糊模式",13,false);current.addView(modeLabel);
        Spinner blurMode=new Spinner(this);
        String[] blurModes={"关闭模糊 · 纯折射","自定义壁纸模糊 · 推荐","ColorOS 原生兼容"};
        blurMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,blurModes));
        blurMode.setSelection(Math.max(0,Math.min(2,prefs.getInt("custom_blur_mode",1))));current.addView(blurMode);
        blurMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){prefs.edit().putInt("custom_blur_mode",pos).commit();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        // Legacy native_alpha_probe is deliberately disabled: blur and material opacity are separate.
        prefs.edit().putBoolean("native_alpha_probe",false).apply();
        TextView blurInfo=text("自定义模糊使用壁纸采样，不模糊应用图标。系统模糊开关不再强制开启；ColorOS 自身仍可能启用原生模糊。",12,false);current.addView(blurInfo);
        // V1.9.199: legacy soft-highlight controls removed from UI. Existing
        // preferences remain for compatibility; the unified surface owns reflection.
        section("曲面环境反射","V185：依据二维曲面方向与实时壁纸明暗计算柔和表面反射。不是仅沿容器轮廓绘制亮线；当前只能反映壁纸环境，不能捕获其他应用窗口。");
        TextView surfaceLabel=text("环境反射与动态边缘高光",13,false);current.addView(surfaceLabel);
        Spinner surfaceSpinner=new Spinner(this);
        String[] surfaceModes={"0 · 关闭新增环境反射","1 · 柔和曲面环境反射","2 · 曲面反射 + 动态边缘高光","3 · 增强动态边缘高光"};
        surfaceSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,surfaceModes));
        surfaceSpinner.setSelection(Math.max(0,Math.min(3,prefs.getInt("surface_mode_195",3))));current.addView(surfaceSpinner);
        surfaceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                prefs.edit().putInt("surface_mode_195",pos).commit();
                notifyConfigChanged128();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        slider("环境反射与高光强度 (%)","surface_strength_195",100,0,200);
        slider("反射柔和度 (%)","surface_softness_195",100,50,200);
        TextView surfaceHint=text("新反射使用 V182 曲面（实验模式 2），背景亮度与局部纹理影响高光。旧版轮廓明暗仍兼容，模式 0 只关闭新增环境反射。建议先将旧描边强度调低对比。",12,false);
        current.addView(surfaceHint);
        section("玻璃底色色调","选择玻璃底色的冷暖色调与叠加浓度；与模糊、折射、表面反射独立。");
        TextView tintLabel=text("色调",13,false);current.addView(tintLabel);
        Spinner tint=new Spinner(this);String[] names={"原生 · 推荐","冷白","浅灰","深色"};
        tint.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        tint.setSelection(Math.max(0,Math.min(3,prefs.getInt("tint",0))));current.addView(tint);
        tint.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){prefs.edit().putInt("tint",pos).commit();notifyConfigChanged128();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        slider("底色色调强度 (%)","tint_strength",30,0,90);
        TextView tintInfo=text("底色色调强度为 0% 时不添加颜色；原生模式不添加色调。颜色变化需在玻璃底色不透明度和当前渲染路径允许的情况下观察。",12,false);current.addView(tintInfo);
        section("旧版兼容与实验","旧二维壁纸折射仅用于对照测试。默认折叠，避免误以为它是第二套必需的光学系统。");
        final LinearLayout legacyContainer=column();
        final Button legacyButton=new Button(this);
        legacyButton.setAllCaps(false);legacyButton.setText("展开旧版二维折射实验");
        current.addView(legacyButton);
        current.addView(legacyContainer);
        legacyContainer.setVisibility(View.GONE);
        final LinearLayout savedCurrent=current;
        current=legacyContainer;
        toggle("旧二维壁纸折射（实验对照）","real_refraction",false);
        slider("旧二维折射强度 (%)","refraction_percent",0,0,100);
        toggle("旧版玻璃细节增强","clear_refraction",false);
        toggle("应用图标高光实验","icon_optics_204",false);
        current=savedCurrent;
        legacyButton.setOnClickListener(clickedView->{
            boolean show=legacyContainer.getVisibility()!=View.VISIBLE;
            legacyContainer.setVisibility(show?View.VISIBLE:View.GONE);
            legacyButton.setText(show?"收起旧版实验参数":"展开旧版二维折射实验");
        });
        section("维护与状态","Root 脚本更新、同步状态和诊断日志。");
        rootStatus=text("正在检查 Root 脚本与运行进程…",12,false);current.addView(rootStatus);
        action("检查并更新 Root 辅助脚本",()->RootHelperUpdater.runAsync(this,result->{
            rootStatus.setText(result);
            rootStatusHandler.removeCallbacks(rootStatusPoll);
            rootStatusHandler.postDelayed(rootStatusPoll,1000);
        }));
        TextView serviceHint=text("安装或更新脚本后请重启手机：旧版服务进程不会因文件被覆盖而自动重启。重启后检查配置同步是否正常。",12,false);current.addView(serviceHint);
        toggle("详细诊断日志","diagnostics",false);
        TextView foot=text("提示：旧版校准、对照图和诊断模式不再显示在首页，已有数据不会自动删除。",12,false);body.addView(foot);
        setContentView(scroll);
        // V1.9.203: status checks are read-only; updates require explicit button tap.
    }
}
