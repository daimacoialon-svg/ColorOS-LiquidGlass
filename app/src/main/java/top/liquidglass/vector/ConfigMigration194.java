package top.liquidglass.vector;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;
import java.util.Map;

/** V194 one-time reconciliation of pre-V194 divergent boot and app configurations.
 *  Never turns a saved ON switch OFF. Never repeats after a successful migration.
 */
final class ConfigMigration194 {
    private static final String TAG="LiquidGlass194";
    private static final String KEY="root_snapshot_reconciled_194";
    private static final String[] NAMES={
        "enabled","alpha","tint","tint_strength","highlight","diagnostics",
        "highlight_strength","probe_mode","highlight_width","directional_light",
        "native_blur_experiment","blur_radius_percent","native_alpha_probe",
        "wallpaper_compare","real_refraction","custom_blur_dp","refraction_percent",
        "glass_tint_opacity","custom_blur_mode","clear_refraction","dynamic_sheen_mode",
        "surface_mode_195","surface_strength_195","surface_softness_195",
        "icon_optics_204","dock_glass_204","nonlinear_107","thickness_107",
        "interior_107","focus_107","reflection_107","optical_style_186"
    };
    private static boolean isBool(String key){
        return "enabled".equals(key)||"highlight".equals(key)||"diagnostics".equals(key)
            ||"directional_light".equals(key)||"native_blur_experiment".equals(key)
            ||"native_alpha_probe".equals(key)||"wallpaper_compare".equals(key)
            ||"real_refraction".equals(key)||"clear_refraction".equals(key)
            ||"icon_optics_204".equals(key)||"dock_glass_204".equals(key);
    }
    static void reconcileOnce(Context context,SharedPreferences prefs){
        if(prefs.getBoolean(KEY,false))return;
        try {
            String raw=Settings.Global.getString(context.getContentResolver(),"liquidglass_boot_snapshot_v1");
            if(raw==null||!raw.startsWith("LG1|")){Log.i(TAG,"MIGRATION_194 stage=WAIT_SNAPSHOT");return;}
            String[] parts=raw.split("\\|",-1);
            // Older runtime snapshots may be 31 fields, V194 has 32.
            if(parts.length!=32 && parts.length!=33){Log.w(TAG,"MIGRATION_194 stage=REJECT_FIELDS count="+parts.length);return;}
            int[] v=new int[parts.length-1];
            for(int i=0;i<v.length;i++)v[i]=Integer.parseInt(parts[i+1]);
            if(v[0]!=0&&v[0]!=1)return;
            if(v[1]<0||v[1]>255||v[21]<0||v[21]>3||v[22]<0||v[22]>200||v[23]<50||v[23]>200)return;
            // The already visible, enabled boot renderer is the migration baseline.
            // When it is OFF, never allow a stale boot value to override an app ON.
            if(v[0]!=1 || !prefs.contains("enabled")){
                prefs.edit().putBoolean(KEY,true).commit();
                Log.i(TAG,"MIGRATION_194 stage=KEEP_APP reason=boot_off_or_unconfigured");
                return;
            }
            boolean differs=false;
            for(int i=0;i<v.length;i++){
                String key=NAMES[i];
                if(isBool(key)){
                    if(!prefs.contains(key)||prefs.getBoolean(key,false)!=(v[i]!=0)){differs=true;break;}
                }else if(!prefs.contains(key)||prefs.getInt(key,Integer.MIN_VALUE)!=v[i]){differs=true;break;}
            }
            if(!differs){prefs.edit().putBoolean(KEY,true).commit();Log.i(TAG,"MIGRATION_194 stage=ALREADY_MATCHED");return;}
            SharedPreferences backup=context.getSharedPreferences("glass_before_v194_migration",0);
            if(!backup.getBoolean("backup_created",false)){
                SharedPreferences.Editor be=backup.edit();
                for(Map.Entry<String,?> e:prefs.getAll().entrySet()){
                    Object o=e.getValue();String k=e.getKey();
                    if(o instanceof Boolean)be.putBoolean(k,(Boolean)o);
                    else if(o instanceof Integer)be.putInt(k,(Integer)o);
                    else if(o instanceof Long)be.putLong(k,(Long)o);
                    else if(o instanceof Float)be.putFloat(k,(Float)o);
                    else if(o instanceof String)be.putString(k,(String)o);
                }
                be.putBoolean("backup_created",true);
                if(!be.commit()){Log.e(TAG,"MIGRATION_194 stage=BACKUP_FAILED");return;}
            }
            SharedPreferences.Editor edit=prefs.edit();
            for(int i=0;i<v.length;i++){
                String name=NAMES[i];
                // Existing user preferences are authoritative. Migration must not undo
                // an explicit Dock OFF or diagnostics ON from the current settings UI.
                if(prefs.contains(name) || "dock_glass_204".equals(name))continue;
                // Never re-enable OEM native blur and native alpha probe via migration.
                if("native_blur_experiment".equals(name)||"native_alpha_probe".equals(name))continue;
                if(isBool(name))edit.putBoolean(name,v[i]!=0); else edit.putInt(name,v[i]);
            }
            // Boot snapshot stores integer dp but not the UI's tenths of a dp.
            edit.putInt("custom_blur_tenths_95",Math.max(0,Math.min(400,v[15]*10)));
            edit.putBoolean(KEY,true);
            if(edit.commit())Log.i(TAG,"MIGRATION_194 stage=BOOT_TO_APP enabled=true fields="+v.length);
            else Log.e(TAG,"MIGRATION_194 stage=COMMIT_FAILED");
        }catch(Throwable t){Log.w(TAG,"MIGRATION_194 stage=FAILED reason="+t.getClass().getSimpleName());}
    }
}
