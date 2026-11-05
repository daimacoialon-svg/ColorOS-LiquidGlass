package top.liquidglass.vector;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * V230 separately versioned material extension.  The V202 LG1 schema remains
 * exactly 33 fields, including its ordinal positions and Root helper script.
 * A complete LG230 record is always applied atomically. It is never used for
 * legacy visual effects when the user switches the unified experiment off.
 */
final class MaterialConfig230 {
    static final String GLOBAL_KEY="liquidglass_material_snapshot_230";
    static final String EXTRA="lg_material_230";
    static final String PREF_ON="unified_material_230";
    static final String PREF_STRENGTH="material_optical_strength_230";
    static final String PREF_THICKNESS="material_thickness_230";
    static final String PREF_ENV="material_environment_230";
    static final String PREF_QUALITY="material_quality_230";

    static final class State {
        final boolean enabled;
        final int strength,thickness,environment,quality;
        State(boolean on,int s,int t,int e,int q){
            enabled=on;strength=s;thickness=t;environment=e;quality=q;
        }
    }
    private static volatile State active=new State(false,70,55,60,0);
    private static volatile String applied="";
    private static volatile String latestDirect="";
    private MaterialConfig230(){}
    static State get(){return active;}

    static String snapshot(Context context){
        android.content.SharedPreferences p=context.getSharedPreferences("glass",Context.MODE_PRIVATE);
        return "LG230|"+(p.getBoolean(PREF_ON,false)?1:0)+"|"+
            bounded(p.getInt(PREF_STRENGTH,70),0,100)+"|"+
            bounded(p.getInt(PREF_THICKNESS,55),0,100)+"|"+
            bounded(p.getInt(PREF_ENV,60),0,100)+"|"+
            bounded(p.getInt(PREF_QUALITY,0),0,2);
    }
    private static int bounded(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    static synchronized boolean apply(String raw,boolean direct){
        try{
            if(raw==null || !raw.matches("LG230(\\|[0-9]+){5}"))return false;
            String[] x=raw.split("\\|",-1);
            int on=Integer.parseInt(x[1]),s=Integer.parseInt(x[2]),t=Integer.parseInt(x[3]),
                e=Integer.parseInt(x[4]),q=Integer.parseInt(x[5]);
            if(on>1 || s>100 || t>100 || e>100 || q>2)return false;
            if(direct)latestDirect=raw;
            if(raw.equals(applied))return false;
            active=new State(on==1,s,t,e,q);
            applied=raw;
            // A direct UI change must recover a failed shader without Launcher restart.
            UnifiedGlass230.reset240(direct?"ui_config_changed":"snapshot_changed");
            Log.i("LiquidGlass230","MATERIAL_APPLIED_230 source="+(direct?"UI_DIRECT":"GLOBAL")+
                " enabled="+(on==1)+" strength="+s+" thickness="+t+
                " environment="+e+" quality="+q);
            return true;
        }catch(Throwable fail){Log.w("LiquidGlass230","MATERIAL_REJECT_230",fail);return false;}
    }
    static synchronized boolean restoreGlobal(Context context){
        try{
            String raw=Settings.Global.getString(context.getContentResolver(),GLOBAL_KEY);
            // Suppress an older Root publication immediately after a newer UI event.
            if(!latestDirect.isEmpty()){
                if(!latestDirect.equals(raw))return false;
                latestDirect="";
            }
            return apply(raw,false);
        }catch(Throwable t){Log.w("LiquidGlass230","MATERIAL_RESTORE_230",t);return false;}
    }
}
