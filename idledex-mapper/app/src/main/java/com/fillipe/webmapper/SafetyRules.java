package com.fillipe.webmapper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SafetyRules {
    public static final String PREFS = "idledex_companion_v2";
    private final SharedPreferences p;

    public SafetyRules(Context context) {
        p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        init();
        // Disable the older soltura route until identity is proven across Box and NPC views.
        p.edit().putBoolean("simulation", true).putBoolean("autoRelease", false)
                .putLong("armedUntil", 0L).apply();
    }

    private void init() {
        if (p.getBoolean("initialized", false)) return;
        p.edit()
                .putBoolean("initialized", true)
                .putString("preset", "Farm forte")
                .putBoolean("simulation", true)
                .putBoolean("autoRelease", false)
                .putBoolean("autoScan", false)
                .putBoolean("keepShiny", true)
                .putBoolean("protectLocked", true)
                .putBoolean("requireCompleteData", true)
                .putString("minIv", "145")
                .putString("protectedQualities", "Excepcional,Excelente")
                .putString("protectedSpecies", "Scyther,Scizor")
                .putString("intervalMinutes", "10")
                .putString("maxReleasesPerCycle", "3")
                .putBoolean("emergencyStop", false)
                .putLong("armedUntil", 0L)
                .apply();
    }

    public void applyPreset(String preset) {
        SharedPreferences.Editor e = p.edit().putString("preset", preset).putLong("armedUntil", 0L);
        if ("Segurança máxima".equals(preset)) {
            e.putBoolean("simulation", true).putBoolean("autoRelease", false)
                    .putBoolean("autoScan", false).putBoolean("keepShiny", true)
                    .putBoolean("protectLocked", true).putBoolean("requireCompleteData", true)
                    .putString("minIv", "145").putString("protectedQualities", "Excepcional,Excelente")
                    .putString("protectedSpecies", "Scyther,Scizor").putString("maxReleasesPerCycle", "1");
        } else if ("Caçar Shiny".equals(preset)) {
            e.putBoolean("simulation", true).putBoolean("autoRelease", false)
                    .putBoolean("autoScan", true).putBoolean("keepShiny", true)
                    .putBoolean("protectLocked", true).putBoolean("requireCompleteData", true)
                    .putString("minIv", "155").putString("protectedQualities", "Excepcional")
                    .putString("protectedSpecies", "Scyther,Scizor").putString("maxReleasesPerCycle", "3");
        } else if ("Farm forte".equals(preset)) {
            e.putBoolean("simulation", true).putBoolean("autoRelease", false)
                    .putBoolean("autoScan", true).putBoolean("keepShiny", true)
                    .putBoolean("protectLocked", true).putBoolean("requireCompleteData", true)
                    .putString("minIv", "145").putString("protectedQualities", "Excepcional,Excelente")
                    .putString("protectedSpecies", "Scyther,Scizor").putString("maxReleasesPerCycle", "3");
        }
        e.apply();
    }

    public String preset() { return p.getString("preset", "Farm forte"); }
    public boolean simulation() { return true; }
    public boolean autoRelease() { return false; }
    public boolean autoScan() { return p.getBoolean("autoScan", false); }
    public boolean keepShiny() { return p.getBoolean("keepShiny", true); }
    public boolean protectLocked() { return p.getBoolean("protectLocked", true); }
    public boolean requireCompleteData() { return p.getBoolean("requireCompleteData", true); }
    public boolean emergencyStop() { return p.getBoolean("emergencyStop", false); }
    public int minIv() { return clamp(intPref("minIv", 145), 0, 186); }
    public int intervalMinutes() { return clamp(intPref("intervalMinutes", 10), 2, 120); }
    public int maxReleasesPerCycle() { return clamp(intPref("maxReleasesPerCycle", 3), 1, 20); }
    public long armedUntil() { return p.getLong("armedUntil", 0L); }
    public boolean isArmed() { return false; }

    public void setEmergencyStop(boolean on) {
        SharedPreferences.Editor e = p.edit().putBoolean("emergencyStop", on);
        if (on) e.putLong("armedUntil", 0L);
        e.apply();
    }

    public void armForMinutes(int minutes) {
        disarm();
    }

    public void disarm() { p.edit().putLong("armedUntil", 0L).apply(); }

    public void save(boolean simulation, boolean autoRelease, boolean autoScan, boolean keepShiny,
                     boolean protectLocked, boolean requireCompleteData, int minIv,
                     String qualities, String species, int intervalMinutes, int maxReleases) {
        p.edit()
                .putString("preset", "Personalizado")
                .putBoolean("simulation", true)
                .putBoolean("autoRelease", false)
                .putBoolean("autoScan", autoScan)
                .putBoolean("keepShiny", keepShiny)
                .putBoolean("protectLocked", protectLocked)
                .putBoolean("requireCompleteData", requireCompleteData)
                .putString("minIv", String.valueOf(clamp(minIv,0,186)))
                .putString("protectedQualities", qualities == null ? "" : qualities.trim())
                .putString("protectedSpecies", species == null ? "" : species.trim())
                .putString("intervalMinutes", String.valueOf(clamp(intervalMinutes,2,120)))
                .putString("maxReleasesPerCycle", String.valueOf(clamp(maxReleases,1,20)))
                .apply();
        disarm();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("preset", preset());
            o.put("simulation", simulation());
            o.put("autoRelease", autoRelease());
            o.put("autoScan", autoScan());
            o.put("keepShiny", keepShiny());
            o.put("protectLocked", protectLocked());
            o.put("requireCompleteData", requireCompleteData());
            o.put("minIv", minIv());
            o.put("protectedQualities", new JSONArray(split(p.getString("protectedQualities", "Excepcional,Excelente"))));
            o.put("protectedSpecies", new JSONArray(split(p.getString("protectedSpecies", "Scyther,Scizor"))));
            o.put("intervalMinutes", intervalMinutes());
            o.put("maxReleasesPerCycle", maxReleasesPerCycle());
            o.put("emergencyStop", emergencyStop());
            o.put("armed", isArmed());
            o.put("armedUntil", armedUntil());
        } catch (JSONException ignored) {}
        return o;
    }

    public JSONObject evaluate(JSONObject pokemon, boolean consistent, int releasedThisCycle) {
        JSONObject out = new JSONObject();
        try {
            List<String> reasons = new ArrayList<>();
            String species = pokemon.optString("species", "").trim();
            String quality = pokemon.optString("quality", "").trim();
            boolean hasIv = !pokemon.isNull("iv") && pokemon.has("iv");
            int iv = pokemon.optInt("iv", -1);
            boolean shiny = pokemon.optBoolean("shiny", false);
            boolean locked = pokemon.optBoolean("locked", false);

            if (emergencyStop()) reasons.add("PARADA DE EMERGÊNCIA ativa");
            if (!consistent) reasons.add("dupla leitura não conferiu");
            if (requireCompleteData() && (species.isEmpty() || quality.isEmpty() || !hasIv || iv < 0 || iv > 186)) reasons.add("dados incompletos ou inválidos");
            if (keepShiny() && shiny) reasons.add("Shiny protegido");
            if (protectLocked() && locked) reasons.add("Pokémon travado protegido");
            if (containsIgnoreCase(split(p.getString("protectedSpecies", "Scyther,Scizor")), species)) reasons.add("espécie protegida: " + species);
            if (containsIgnoreCase(split(p.getString("protectedQualities", "Excepcional,Excelente")), quality)) reasons.add("qualidade protegida: " + quality);
            if (hasIv && iv >= minIv()) reasons.add("IV protegido: " + iv + " ≥ " + minIv());

            boolean protectedPokemon = !reasons.isEmpty();
            String action;
            if (protectedPokemon) action = "KEEP";
            else if (simulation() || !autoRelease() || !isArmed()) action = "QUEUE";
            else if (releasedThisCycle >= maxReleasesPerCycle()) {
                action = "QUEUE";
                reasons.add("limite de solturas do ciclo atingido");
            } else action = "RELEASE";

            out.put("action", action);
            out.put("reasons", new JSONArray(reasons));
            out.put("armed", isArmed());
            out.put("simulation", simulation());
            out.put("emergencyStop", emergencyStop());
        } catch (JSONException ignored) {}
        return out;
    }

    public String protectedSpeciesCsv() { return p.getString("protectedSpecies", "Scyther,Scizor"); }
    public String protectedQualitiesCsv() { return p.getString("protectedQualities", "Excepcional,Excelente"); }

    private int intPref(String key, int fallback) {
        try { return Integer.parseInt(p.getString(key, String.valueOf(fallback)).trim()); }
        catch (Exception e) { return fallback; }
    }

    private List<String> split(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        for (String s : list) if (s.toLowerCase(Locale.ROOT).equals(v)) return true;
        return false;
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
