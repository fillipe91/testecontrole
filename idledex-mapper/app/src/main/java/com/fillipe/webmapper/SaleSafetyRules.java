package com.fillipe.webmapper;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Persistent preferences may strengthen protections, never lower the permanent floor. */
public final class SaleSafetyRules {
    private final SharedPreferences p;
    public SaleSafetyRules(Context context){
        p=context.getSharedPreferences("idledex_safe_sale_v1",Context.MODE_PRIVATE);
        // Revoke old 2.1.0 arming across upgrade/process recreation.
        p.edit().putBoolean("simulation",true).putBoolean("autoSell",false).putLong("armedUntil",0L).apply();
    }
    public boolean simulation(){return true;}
    public boolean isArmed(){return false;}
    public boolean emergencyStop(){return p.getBoolean("emergencyStop",false);}
    public void setEmergencyStop(boolean value){p.edit().putBoolean("emergencyStop",value).putLong("armedUntil",0L).apply();}
    public void disarm(){p.edit().putLong("armedUntil",0L).putBoolean("autoSell",false).apply();}
    public int minIvProtect(){return Math.max(0,Math.min(135,number("minIvProtect",135)));}
    public int keepBestPerSpecies(){return Math.max(3,Math.min(100,number("keepBestPerSpecies",3)));}
    public String protectedSpeciesCsv(){return p.getString("protectedSpecies","Scyther,Scizor");}
    private int number(String key,int fallback){try{return Integer.parseInt(p.getString(key,""));}catch(Exception e){return fallback;}}
    public void save(int iv,int keep,String species){p.edit().putString("minIvProtect",String.valueOf(Math.max(0,Math.min(135,iv))))
        .putString("keepBestPerSpecies",String.valueOf(Math.max(3,Math.min(100,keep))))
        .putString("protectedSpecies",species==null?"":species.trim()).apply();disarm();}
    public JSONObject evaluate(JSONObject pokemon,boolean consistent,boolean complete,boolean teamsComplete,int count,int rank){
        return SalePolicy.evaluate(pokemon,consistent,complete&&!emergencyStop(),teamsComplete,count,rank,minIvProtect(),keepBestPerSpecies(),protectedSpeciesCsv());
    }
    /** No execution capability until a verified cross-surface ID contract exists. */
    public JSONObject finalGate(){JSONObject o=new JSONObject();try{o.put("authorized",false);o.put("reason","Venda real indisponível: identidade no NPC e nos times salvos não comprovada");}catch(Exception ignored){}return o;}
}
