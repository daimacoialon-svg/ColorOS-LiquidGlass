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

    // V203: UI commit -> immediate direct snapshot broadcast; Root is asynchronous fallback.
    private final Handler publishHandler194=new Handler(Looper.getMainLooper());
    private final Runnable rootSignal194=()->new Thread(()->{
        String expected=ConfigPublisher202.snapshot(getApplicationContext());
        boolean ok=ConfigPublisher202.publishVerified(expected);
        android.util.Log.i("LiquidGlass203","CONFIG_203_BACKGROUND_PUBLISH verified="+ok);
    },"LiquidGlass-BackgroundPublish203").start();
    private void notifyConfigChanged128(){
        getContentResolver().notifyChange(GlassConfigProvider.URI,null);
        String snapshot=ConfigPublisher202.snapshot(getApplicationContext());
        try{
            android.content.Intent i=new android.content.Intent("top.liquidglass.vector.CONFIG_CHANGED_128");
            i.setPackage("com.android.launcher");
            i.putExtra("lg_snapshot_203",snapshot);
            sendBroadcast(i);
            android.util.Log.i("LiquidGlass203","CONFIG_203_DIRECT_SENT fields="+snapshot.split("\\|",-1).length);
        }catch(Throwable t){android.util.Log.w("LiquidGlass203","CONFIG_203_DIRECT_FAILED",t);}
        publishHandler194.removeCallbacks(rootSignal194);
        publishHandler194.postDelayed(rootSignal194,900L);
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
            putBooleanIfChanged193(key,checked);
        });current.addView(s);
    }
    private void slider(String label,String key,int def,int min,int max){
        TextView caption=text("",13,false);current.addView(caption);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(Math.max(0,Math.min(max-min,prefs.getInt(key,def)-min)));current.addView(bar);
        Runnable update=()->caption.setText(label+"  ·  "+(bar.getProgress()+min));update.run();
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean fromUser){update.run();}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){putIntIfChanged193(key,b.getProgress()+min);}
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
            public void onProgressChanged(SeekBar b,int progress,boolean fromUser){update.run();}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){updateMicroBlur193(b.getProgress());}
        });
    }
    private void updateMicroBlur193(int progress){
        int dp=Math.round(progress/10f);
        if(prefs.getInt("custom_blur_tenths_95",-1)==progress && prefs.getInt("custom_blur_dp",-1)==dp)return;
        prefs.edit().putInt("custom_blur_tenths_95",progress).putInt("custom_blur_dp",dp).commit();
        audit193("USER_CHANGED_BLUR");
        notifyConfigChanged128();
    }
    private void action(String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextColor(blue);b.setOnClickListener(v->run.run());current.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));
    }
    // V222: opt-in visual diagnostics are NOT part of the saved glass settings.
    // Exit returns all production paths to their normal, no-probe state.
    private void setLensDiagnostic222(int requested){
        final int mode=Math.max(0,Math.min(4,requested));
        new Thread(()->{
            Process process=null;
            boolean ok=false;
            try{
                String command="settings put global liquidglass_aa_probe_208 0 && " +
                    "settings put global liquidglass_lens_diagnostic_222 "+mode+" && " +
                    "settings put global liquidglass_debug_opt_in_219 "+(mode==0?0:1);
                process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                ok=process.waitFor(8,java.util.concurrent.TimeUnit.SECONDS) && process.exitValue()==0;
                if(!ok && process.isAlive())process.destroyForcibly();
            }catch(Throwable t){android.util.Log.w("LiquidGlass222","DIAGNOSTIC_COMMAND_FAILED",t);}
            finally{if(process!=null)process.destroy();}
            final boolean succeeded=ok;
            if(succeeded){
                try{
                    android.content.Intent refresh=new android.content.Intent("top.liquidglass.vector.CONFIG_CHANGED_128");
                    refresh.setPackage("com.android.launcher");
                    refresh.putExtra("lg_snapshot_203",ConfigPublisher202.snapshot(getApplicationContext()));
                    sendBroadcast(refresh);
                }catch(Throwable t){android.util.Log.w("LiquidGlass222","DIAGNOSTIC_REFRESH_FAILED",t);}
            }
            runOnUiThread(()->Toast.makeText(this,succeeded?
                (mode==0?"诊断已关闭，已恢复正常二维曲面透镜":"已切换全容器透镜诊断模式 "+mode):
                "Root 诊断切换失败，请检查 KernelSU 授权",Toast.LENGTH_LONG).show());
        },"LiquidGlass-Diagnostic222").start();
    }
    private String styleLabel186(){
        int style=prefs.getInt("optical_style_186",0);
        return style==2?"泡泡液态玻璃":style==1?"iOS 27 风格":"自定义参数（统一曲面）";
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
        Toast.makeText(this,"已应用二维曲面透镜风格，Launcher 将实时刷新玻璃参数",Toast.LENGTH_LONG).show();
        recreate();
    }
    // V193: opening, backgrounding and closing the settings UI are read-only.
    @Override protected void onPause(){
        audit193("UI_ON_PAUSE_READ_ONLY");
        super.onPause();
    }
    private void audit193(String event){
        if(prefs==null)return;
        android.util.Log.i("LiquidGlass193",event+" uptime="+android.os.SystemClock.uptimeMillis()
            +" style="+prefs.getInt("optical_style_186",0)
            +" lens="+prefs.getInt("nonlinear_107",65)
            +" thickness="+prefs.getInt("thickness_107",65)
            +" surface="+prefs.getInt("surface_mode_195",3)
            +" strength="+prefs.getInt("surface_strength_195",100)
            +" softness="+prefs.getInt("surface_softness_195",100)
            +" nativeAlpha="+prefs.getBoolean("native_alpha_probe",false));
    }
    private void putIntIfChanged193(String key,int value){
        if(prefs.contains(key) && prefs.getInt(key,value)==value)return;
        boolean saved=prefs.edit().putInt(key,value).commit();
        android.util.Log.i("LiquidGlass209","CONFIG_209_INT_SAVE key="+key+" value="+value+" committed="+saved);
        if(!saved)return;
        audit193("USER_CHANGED_"+key);
        notifyConfigChanged128();
    }
    private void putBooleanIfChanged193(String key,boolean value){
        if(prefs.contains(key) && prefs.getBoolean(key,value)==value)return;
        boolean saved=prefs.edit().putBoolean(key,value).commit();
        android.util.Log.i("LiquidGlass204","CONFIG_204_SWITCH_SAVE key="+key+" value="+value+" committed="+saved+" readback="+prefs.getBoolean(key,!value));
        if(!saved)return;
        audit193("USER_CHANGED_"+key);
        notifyConfigChanged128();
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("glass",0);
        ConfigMigration194.reconcileOnce(this,prefs);
        // V219: disable retired visual overlays and legacy 2D displacement on
        // first upgrade. Keep normal lens, folder, Dock, icon and blur settings.
        if(!prefs.getBoolean("legacy_probe_cleanup_219",false)){
            prefs.edit().putBoolean("diagnostics",false)
                .putBoolean("wallpaper_compare",false).putInt("probe_mode",0)
                .putBoolean("real_refraction",false).putInt("refraction_percent",0)
                .putBoolean("clear_refraction",false)
                .putBoolean("native_blur_experiment",false)
                .putBoolean("native_alpha_probe",false)
                .putInt("dynamic_sheen_mode",0).commit();
            notifyConfigChanged128();
            LegacyDiagnostics219.cleanupAsync(this,(success,reason)->{
                if(success)notifyConfigChanged128();
                else android.util.Log.w("LiquidGlass219","ROOT_DIAGNOSTICS_CLEANUP_FAILED "+reason);
            });
        }
        // V217: persist first-run defaults before publishing. A saved explicit OFF
        // remains authoritative across app restarts, Launcher restarts and reboot.
        SharedPreferences.Editor firstDefaults217=prefs.edit();
        boolean needsDefaults217=false;
        if(!prefs.contains("enabled")){firstDefaults217.putBoolean("enabled",true);needsDefaults217=true;}
        if(!prefs.contains("dock_glass_204")){firstDefaults217.putBoolean("dock_glass_204",true);needsDefaults217=true;}
        if(!prefs.contains("icon_optics_204")){firstDefaults217.putBoolean("icon_optics_204",true);needsDefaults217=true;}
        if(needsDefaults217){
            boolean savedDefaults217=firstDefaults217.commit();
            android.util.Log.i("LiquidGlass217","BOOT_DEFAULTS_217 committed="+savedDefaults217+
                " folder="+prefs.getBoolean("enabled",true)+" dock="+prefs.getBoolean("dock_glass_204",true)+
                " icon="+prefs.getBoolean("icon_optics_204",true));
            if(savedDefaults217)notifyConfigChanged128();
        }
        audit193("UI_ON_CREATE_V194");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF3F6FA);
        body=column();body.setPadding(dp(18),dp(28),dp(18),dp(32));scroll.addView(body);
        TextView heading=text("Liquid Glass",28,true);body.addView(heading);
        TextView version=text("V1.9.205.222  ·  全容器防折叠透镜与矢量图标薄内缘",13,false);LinearLayout.LayoutParams v=new LinearLayout.LayoutParams(-1,-2);v.setMargins(0,dp(4),0,dp(20));body.addView(version,v);
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
        toggle("启用桌面文件夹玻璃（开机恢复，手动关闭后保持关闭）", "enabled",true);
        TextView presetInfo=text("首次配置默认启用文件夹与 Dock 玻璃；重启时恢复上次保存的开关状态，手动关闭不会被强制打开。",12,false);current.addView(presetInfo);
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
        TextView presetHint=text("推荐参数是测试起点，不代表已达到 iOS 目标。旧二维折射默认关闭；正式路径始终由统一曲面透镜负责。",12,false);
        current.addView(presetHint);
        
        toggle("启用 Dock 玻璃（默认开启，手动关闭后保持关闭）","dock_glass_204",true);
        TextView dockInfo=text("Dock 保持原有尺寸和位置；V132 与文件夹共用直接背景采样及透镜位移 Shader，并按容器几何自适应。",12,false);current.addView(dockInfo);

        section("二维曲面透镜","统一曲面负责文件夹和 Dock 的背景折射与厚度。反射、高光和底色分别调整，避免把不同效果混在同一滑块。");
        slider("二维曲面折射强度 (%)","nonlinear_107",65,0,100);
        TextView unifiedInfo162=text("0% 为无曲面位移，100% 最强。文件夹和 Dock 共用同一张二维高度场，不叠加旧版折射。",12,false);
        current.addView(unifiedInfo162);
        final LinearLayout advancedLens219=column();
        final Button lensDetails219=new Button(this);
        lensDetails219.setText("展开曲面几何高级参数");lensDetails219.setAllCaps(false);
        current.addView(lensDetails219);current.addView(advancedLens219);
        advancedLens219.setVisibility(View.GONE);
        final LinearLayout beforeAdvanced219=current;
        current=advancedLens219;
        slider("曲面厚度感 (%)","thickness_107",55,0,100);
        slider("内区曲率参与度 (%)","interior_107",20,0,100);
        slider("中心聚焦参与度 (%)","focus_107",45,0,100);
        slider("曲面局部反射比例 (%)","reflection_107",30,0,100);
        current.addView(text("前三项参与统一的全容器单调映射幅度，主折射强度控制整体位移；反射比例单独控制曲面高光。参数有固定上限，避免高强度局部折叠。",12,false));
        current=beforeAdvanced219;
        lensDetails219.setOnClickListener(clicked->{
            boolean open=advancedLens219.getVisibility()!=View.VISIBLE;
            advancedLens219.setVisibility(open?View.VISIBLE:View.GONE);
            lensDetails219.setText(open?"收起曲面几何高级参数":"展开曲面几何高级参数");
        });
        section("玻璃外观","背景模糊和底色独立于折射；应用图标不会被自定义壁纸模糊处理。");
        slider("玻璃底色不透明度 (%)","glass_tint_opacity",18,0,100);
        microBlurSlider95();
        TextView modeLabel=text("背景模糊模式",13,false);current.addView(modeLabel);
        Spinner blurMode=new Spinner(this);
        String[] blurModes={"关闭模糊 · 纯折射","自定义壁纸模糊 · 推荐","ColorOS 原生兼容"};
        blurMode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,blurModes));
        blurMode.setSelection(Math.max(0,Math.min(2,prefs.getInt("custom_blur_mode",1))));current.addView(blurMode);
        blurMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){putIntIfChanged193("custom_blur_mode",pos);}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        // Legacy native_alpha_probe is deliberately disabled: blur and material opacity are separate.
        // V193: do not mutate native_alpha_probe while constructing the settings page.
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
                if(prefs.getInt("surface_mode_195",3)!=pos){
                    putIntIfChanged193("surface_mode_195",pos);
                }
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        slider("环境反射与高光强度 (%)","surface_strength_195",100,0,200);
        slider("反射柔和度 (%)","surface_softness_195",100,50,200);
        TextView surfaceHint=text("V190 模式 3：环境反射开关仅控制反射光学层，不关闭二维折射与壁纸采样；柔和度控制局部高光的方向性，轮廓清晰度仍受曲面坡度影响。",12,false);
        current.addView(surfaceHint);
        section("玻璃底色色调","选择玻璃底色的冷暖色调与叠加浓度；与模糊、折射、表面反射独立。");
        TextView tintLabel=text("色调",13,false);current.addView(tintLabel);
        Spinner tint=new Spinner(this);String[] names={"原生 · 推荐","冷白","浅灰","深色"};
        tint.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        tint.setSelection(Math.max(0,Math.min(3,prefs.getInt("tint",0))));current.addView(tint);
        tint.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){putIntIfChanged193("tint",pos);}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        slider("底色色调强度 (%)","tint_strength",30,0,90);
        TextView tintInfo=text("底色色调强度为 0% 时不添加颜色；原生模式不添加色调。颜色变化需在玻璃底色不透明度和当前渲染路径允许的情况下观察。",12,false);current.addView(tintInfo);
        section("应用图标内缘立体光影","使用平滑矢量轮廓生成薄内缘明暗：左上、右下较亮，右上、左下较暗。取消旧像素法线的放射状内拉伪影，不在图标外形成月晕。");
        toggle("应用图标薄内缘明暗光学效果","icon_optics_204",true);
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

        current=savedCurrent;
        legacyButton.setOnClickListener(clickedView->{
            boolean show=legacyContainer.getVisibility()!=View.VISIBLE;
            legacyContainer.setVisibility(show?View.VISIBLE:View.GONE);
            legacyButton.setText(show?"收起旧版实验参数":"展开旧版二维折射实验");
        });
        section("维护与状态","V222 对整片文件夹与 Dock 玻璃使用同一连续单调映射；取消 V221 只针对底部和角部的采样特例。保留 V219 稳定 Shader 作为异常回退。");
        final LinearLayout lensDiagnostics222=column();
        Button showLensDiagnostics222=new Button(this);
        showLensDiagnostics222.setAllCaps(false);
        showLensDiagnostics222.setText("展开全容器折射诊断（默认关闭）");
        current.addView(showLensDiagnostics222);
        current.addView(lensDiagnostics222);
        lensDiagnostics222.setVisibility(View.GONE);
        final LinearLayout beforeLensDiagnostics222=current;
        current=lensDiagnostics222;
        current.addView(text("模式仅在开启时临时显示，不写入 33 项玻璃配置。4 号全域九点采样是诊断对照，可能明显增加 GPU 负担。",12,false));
        action("1 · 全容器采样坐标网格",()->setLensDiagnostic222(1));
        action("2 · 映射 Jacobian 压缩/拉伸热图",()->setLensDiagnostic222(2));
        action("3 · 原图/折射左右分屏对照",()->setLensDiagnostic222(3));
        action("4 · 全域九点采样实验（临时）",()->setLensDiagnostic222(4));
        action("0 · 关闭诊断并恢复正常透镜",()->setLensDiagnostic222(0));
        current=beforeLensDiagnostics222;
        showLensDiagnostics222.setOnClickListener(v->{
            boolean open=lensDiagnostics222.getVisibility()!=View.VISIBLE;
            lensDiagnostics222.setVisibility(open?View.VISIBLE:View.GONE);
            showLensDiagnostics222.setText(open?"收起全容器折射诊断":"展开全容器折射诊断（默认关闭）");
        });
        action("一键关闭所有旧版绘制和可视诊断模式",()->{
            LegacyDiagnostics219.cleanupAsync(this,(success,reason)->{
                if(success)notifyConfigChanged128();
                Toast.makeText(this,success?"历史可视诊断已关闭，备份位于 /data/local/tmp/liquidglass_legacy_globals_before_219.txt":
                    "关闭失败："+reason,Toast.LENGTH_LONG).show();
            });
        });
        action("退出折射诊断模式（恢复正常透镜）",()->{
            // Settings.Global requires privileged write access. Use the existing
            // KernelSU root path instead of calling putInt as an ordinary app.
            new Thread(()->{
                boolean ok=false;
                Process process=null;
                try{
                    process=new ProcessBuilder("su","-c","settings put global liquidglass_aa_probe_208 0 && settings put global liquidglass_lens_diagnostic_222 0 && settings put global liquidglass_debug_opt_in_219 0")
                        .redirectErrorStream(true).start();
                    ok=process.waitFor(7,java.util.concurrent.TimeUnit.SECONDS) && process.exitValue()==0;
                    if(!ok && process.isAlive())process.destroyForcibly();
                }catch(Throwable ignored){}
                finally{if(process!=null)process.destroy();}
                final boolean succeeded=ok;
                if(succeeded){
                    try{
                        android.content.Intent refresh=new android.content.Intent("top.liquidglass.vector.CONFIG_CHANGED_128");
                        refresh.setPackage("com.android.launcher");
                        refresh.putExtra("lg_snapshot_203",ConfigPublisher202.snapshot(getApplicationContext()));
                        sendBroadcast(refresh);
                    }catch(Throwable ignored){}
                }
                runOnUiThread(()->Toast.makeText(this,succeeded?
                    "已恢复模式 0；请返回桌面检查":"切换失败：请用 Termux 的 su 命令恢复模式 0",Toast.LENGTH_LONG).show());
            },"LiquidGlass-DiagnosticReset217").start();
        });
        rootStatus=text("正在检查 Root 脚本与运行进程…",12,false);current.addView(rootStatus);
        action("检查并更新 Root 辅助脚本",()->RootHelperUpdater.runAsync(this,result->{
            rootStatus.setText(result);
            rootStatusHandler.removeCallbacks(rootStatusPoll);
            rootStatusHandler.postDelayed(rootStatusPoll,1000);
        }));
        TextView serviceHint=text("V222：已验证的 UI→Launcher 实时参数链保持不变。正式 Shader 采用全容器单调变形 + 线性过滤；四组对照诊断默认关闭，手动开启后请务必恢复模式 0。",12,false);current.addView(serviceHint);
        toggle("详细诊断日志","diagnostics",false);
        TextView foot=text("提示：旧版校准、对照图和诊断模式不再显示在首页，已有数据不会自动删除。",12,false);body.addView(foot);
        setContentView(scroll);
        audit193("UI_RENDER_COMPLETE_NO_IMPLICIT_WRITE");
        // V193: only explicit user actions can persist optical settings.
    }
}
