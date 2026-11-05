package top.liquidglass.vector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.TimeUnit;

/** One-time opt-out of legacy visual probe overlays; does not alter lens settings. */
final class LegacyDiagnostics219 {
    private LegacyDiagnostics219() {}
    private static final String[] GLOBAL_ZERO = new String[]{
        "liquidglass_refraction_visibility_test",
        "liquidglass_visibility_122",
        "liquidglass_refraction_composition_test",
        "liquidglass_refraction_sample_test",
        "liquidglass_source_isolation_90",
        "liquidglass_residual_isolation_120",
        "liquidglass_lens_diagnostic_111",
        "liquidglass_aa_probe_208",
        "liquidglass_optical_diagnostic_147",
        "liquidglass_optical_probe_141",
        "liquidglass_optical_probe_156",
        "liquidglass_optical_probe_118",
        "liquidglass_layer_probe_151",
        "liquidglass_layer_probe_79",
        "liquidglass_native_preview_isolation_149",
        "liquidglass_folder_native_blur_isolation",
        "liquidglass_surface_rollback_194",
        "liquidglass_surface_unified_192",
        "liquidglass_rim_mode_190",
        "liquidglass_surface_pass_189",
        "liquidglass_refraction_path_169",
        "liquidglass_refraction_blur_isolation_168",
        "liquidglass_composite_probe_157",
        "liquidglass_draglayer_layer_probe_166",
        "liquidglass_agsl_paint_alpha_171",
        "liquidglass_shared_interior_lens_63",
        "liquidglass_valid_sample_domain_70",
        "liquidglass_edge_diagnostic_69",
        "liquidglass_coordinate_probe_155",
        "liquidglass_legacy_renderer_219",
        "liquidglass_debug_opt_in_219",
        "liquidglass_mapping_probe_81",
        "liquidglass_final_sample_probe_77",
        "liquidglass_oem_scroll_isolation_163",
        "liquidglass_layer_composite_175",
        "liquidglass_texture_input_experiment_87",
        "liquidglass_material_switch_ab",
    };
    interface Callback {void finished(boolean success,String detail);}
    static void cleanupAsync(Context context,Callback callback){
        final Context app=context.getApplicationContext();
        new Thread(()->{
            boolean good=false;String detail="UNKNOWN";
            Process process=null;
            try{
                StringBuilder script=new StringBuilder();
                script.append("backup=/data/local/tmp/liquidglass_legacy_globals_before_219.txt; ")
                    .append("if [ ! -f \"$backup\" ]; then : > \"$backup\"; chmod 600 \"$backup\"; capture=1; else capture=0; fi; ");
                for(String key:GLOBAL_ZERO){
                    script.append("if [ \"$capture\" = 1 ]; then old=$(settings get global ")
                        .append(key).append(" 2>/dev/null); printf '%s=%s\\n' '")
                        .append(key).append("' \"$old\" >> \"$backup\"; fi; ")
                        .append("settings put global ").append(key)
                        .append(" 0 >/dev/null 2>&1 || exit 12; ");
                }
                // Historical default is 1; never accidentally route into V172.
                script.append("settings put global liquidglass_optical_pipeline_173 1 >/dev/null 2>&1 || exit 13");
                process=new ProcessBuilder("su","-c",script.toString())
                    .redirectErrorStream(true).start();
                boolean ended=process.waitFor(35,TimeUnit.SECONDS);
                if(!ended){process.destroyForcibly();detail="ROOT_TIMEOUT";}
                else {good=process.exitValue()==0;detail=good?"CLEARED":"EXIT_"+process.exitValue();}
            }catch(Throwable t){detail=t.getClass().getSimpleName();}
            finally{if(process!=null)process.destroy();}
            final boolean success=good;final String reason=detail;
            Log.i("LiquidGlass219","LEGACY_PROBES_219 status="+reason+" count="+GLOBAL_ZERO.length);
            if(success){
                SharedPreferences prefs=app.getSharedPreferences("glass",Context.MODE_PRIVATE);
                prefs.edit().putBoolean("legacy_probe_cleanup_219",true).commit();
            }
            if(callback!=null)new Handler(Looper.getMainLooper()).post(()->callback.finished(success,reason));
        },"LiquidGlass-LegacyCleanup219").start();
    }
}
