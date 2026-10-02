package com.fillipe.webmapper;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Safety policy for Pokemon sales.
 *
 * Important design choice: the strongest protections are hard coded and cannot be disabled
 * from the UI. When any required signal is missing, the Pokemon is protected/reviewed rather
 * than sold. This class never clicks anything; it only returns a decision.
 */
public class SaleSafetyRules {
    private static final String PREFS = "idledex_safe_sale_v1";
    private final SharedPreferences p;

    private static final Set<String> LEGENDARY_MYTHICAL = new HashSet<>(Arrays.asList(
            "articuno","zapdos","moltres","mewtwo","mew",
            "raikou","entei","suicune","lugia","ho-oh","hooh","celebi",
            "regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","jirachi","deoxys",
            "uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","phione","manaphy","darkrai","shaymin","arceus",
            "victini","cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","keldeo","meloetta","genesect",
            "xerneas","yveltal","zygarde","diancie","hoopa","volcanion",
            "type: null","type null","silvally","tapu koko","tapu lele","tapu bulu","tapu fini","cosmog","cosmoem","solgaleo","lunala","nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","necrozma","magearna","marshadow","poipole","naganadel","stakataka","blacephalon","zeraora","meltan","melmetal",
            "zacian","zamazenta","eternatus","kubfu","urshifu","zarude","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus",
            "wo-chien","chien-pao","ting-lu","chi-yu","koraidon","miraidon","okidogi","munkidori","fezandipiti","ogerpon","terapagos","pecharunt"
    ));

    public SaleSafetyRules(Context context) {
        p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        init();
    }

    private void init() {
        if (p.getBoolean("initialized", false)) return;
        p.edit()
                .putBoolean("initialized", true)
                .putBoolean("simulation", true)
                .putBoolean("autoSell", false)
                .putString("minIvProtect", "135")
                .putString("protectStarsFrom", "4")
                .putString("keepBestPerSpecies", "3")
                .putString("maxSalesPerCycle", "3")
                .putString("price", "0")
                .putString("currency", "Silver")
                .putString("protectedQualities", "Excepcional,Excelente")
                .putString("protectedSpecies", "Scyther,Scizor")
                .putLong("armedUntil", 0L)
                .putBoolean("emergencyStop", false)
                .apply();
    }

    public boolean simulation() { return p.getBoolean("simulation", true); }
    public boolean autoSell() { return p.getBoolean("autoSell", false); }
    public int minIvProtect() { return clamp(intPref("minIvProtect", 135), 0, 186); }
    public int protectStarsFrom() { return clamp(intPref("protectStarsFrom", 4), 1, 5); }
    public int keepBestPerSpecies() { return clamp(intPref("keepBestPerSpecies", 3), 1, 20); }
    public int maxSalesPerCycle() { return clamp(intPref("maxSalesPerCycle", 3), 1, 20); }
    public long salePrice() { return Math.max(0L, longPref("price", 0L)); }
    public String currency() { return p.getString("currency", "Silver"); }
    public boolean emergencyStop() { return p.getBoolean("emergencyStop", false); }
    public long armedUntil() { return p.getLong("armedUntil", 0L); }
    public boolean isArmed() {
        return autoSell() && !simulation() && !emergencyStop() && salePrice() > 0 && System.currentTimeMillis() < armedUntil();
    }

    public void save(boolean simulation, boolean autoSell, int minIvProtect, int keepBestPerSpecies,
                     int maxSalesPerCycle, long price, String currency, String protectedSpecies) {
        p.edit()
                .putBoolean("simulation", simulation)
                .putBoolean("autoSell", autoSell)
                .putString("minIvProtect", String.valueOf(clamp(minIvProtect, 0, 186)))
                // 4 stars is a hard minimum protection. UI cannot lower it.
                .putString("protectStarsFrom", "4")
                .putString("keepBestPerSpecies", String.valueOf(clamp(keepBestPerSpecies, 1, 20)))
                .putString("maxSalesPerCycle", String.valueOf(clamp(maxSalesPerCycle, 1, 20)))
                .putString("price", String.valueOf(Math.max(0L, price)))
                .putString("currency", "Gold".equalsIgnoreCase(currency) ? "Gold" : "Silver")
                .putString("protectedSpecies", protectedSpecies == null ? "" : protectedSpecies.trim())
                .apply();
        if (simulation || !autoSell || price <= 0) disarm();
    }

    public void setEmergencyStop(boolean on) {
        SharedPreferences.Editor e = p.edit().putBoolean("emergencyStop", on);
        if (on) e.putLong("armedUntil", 0L);
        e.apply();
    }

    public void armForMinutes(int minutes) {
        if (simulation() || !autoSell() || salePrice() <= 0) return;
        p.edit().putLong("armedUntil", System.currentTimeMillis() + clamp(minutes, 1, 30) * 60_000L).apply();
    }

    public void disarm() { p.edit().putLong("armedUntil", 0L).apply(); }

    public String protectedSpeciesCsv() { return p.getString("protectedSpecies", "Scyther,Scizor"); }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("simulation", simulation());
            o.put("autoSell", autoSell());
            o.put("minIvProtect", minIvProtect());
            o.put("protectStarsFrom", 4);
            o.put("keepBestPerSpecies", keepBestPerSpecies());
            o.put("maxSalesPerCycle", maxSalesPerCycle());
            o.put("price", salePrice());
            o.put("currency", currency());
            o.put("protectedQualities", new JSONArray(split(p.getString("protectedQualities", "Excepcional,Excelente"))));
            o.put("protectedSpecies", new JSONArray(split(protectedSpeciesCsv())));
            o.put("hardProtections", new JSONArray(Arrays.asList(
                    "team_active_or_saved", "shiny", "legendary_mythical_ultra_beast", "stars_4_or_5",
                    "event_special", "locked_favorite", "unknown_or_incomplete_data", "unique_copy",
                    "best_copies_per_species", "identity_mismatch"
            )));
            o.put("armed", isArmed());
            o.put("armedUntil", armedUntil());
            o.put("emergencyStop", emergencyStop());
        } catch (JSONException ignored) {}
        return o;
    }

    /**
     * Evaluate a scanned Pokemon. The returned action is one of PROTECT, REVIEW or SELL_CANDIDATE.
     * This method intentionally does not return SELL; transaction authorization is a separate step.
     */
    public JSONObject evaluate(JSONObject pokemon, boolean consistent, boolean teamScanComplete,
                               int speciesCount, int rankWithinSpecies) {
        JSONObject out = new JSONObject();
        List<String> hard = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        try {
            String id = pokemon.optString("id", "").trim();
            String species = pokemon.optString("species", "").trim();
            String quality = pokemon.optString("quality", "").trim();
            String rarity = pokemon.optString("rarity", "").trim();
            boolean hasIv = pokemon.has("iv") && !pokemon.isNull("iv");
            int iv = pokemon.optInt("iv", -1);
            boolean starsKnown = pokemon.optBoolean("starsKnown", false);
            int stars = pokemon.optInt("stars", -1);
            boolean shiny = pokemon.optBoolean("shiny", false);
            boolean locked = pokemon.optBoolean("locked", false);
            boolean favorite = pokemon.optBoolean("favorite", false);
            boolean event = pokemon.optBoolean("event", false);
            boolean inTeam = pokemon.optBoolean("inTeam", false);

            if (emergencyStop()) hard.add("PARADA DE EMERGÊNCIA ativa");
            if (!consistent) hard.add("dupla leitura não conferiu");
            if (!teamScanComplete) hard.add("não foi possível confirmar todos os times; venda bloqueada");
            if (id.isEmpty()) hard.add("identificador único do Pokémon não encontrado");
            if (species.isEmpty()) hard.add("espécie não identificada");
            if (!hasIv || iv < 0 || iv > 186) hard.add("IV ausente ou inválido");
            if (!starsKnown || stars < 0 || stars > 5) hard.add("estrelas não puderam ser confirmadas");

            // Hard non-disableable protections requested by the user.
            if (inTeam) hard.add("TIME PROTEGIDO: equipe ativa ou time salvo");
            if (shiny) hard.add("Shiny nunca pode ser vendido");
            if (isLegendaryOrMythical(species, rarity)) hard.add("Lendário/Mítico/Ultra Beast protegido");
            if (starsKnown && stars >= 4) hard.add(stars + "★ protegido: 4★/5★ nunca vende");
            if (event) hard.add("Pokémon de evento/especial protegido");
            if (locked || favorite) hard.add("travado/favorito protegido");

            if (containsIgnoreCase(split(p.getString("protectedSpecies", "Scyther,Scizor")), species))
                hard.add("espécie protegida manualmente: " + species);
            if (containsIgnoreCase(split(p.getString("protectedQualities", "Excepcional,Excelente")), quality))
                hard.add("qualidade protegida: " + quality);
            if (hasIv && iv >= minIvProtect())
                hard.add("IV protegido: " + iv + " ≥ " + minIvProtect());

            if (speciesCount <= 1) hard.add("único exemplar da espécie");
            if (rankWithinSpecies > 0 && rankWithinSpecies <= keepBestPerSpecies())
                hard.add("está entre os " + keepBestPerSpecies() + " melhores exemplares da espécie");
            if (rankWithinSpecies <= 0) hard.add("ranking entre duplicatas não pôde ser confirmado");

            String action;
            if (!hard.isEmpty()) action = "PROTECT";
            else {
                // Remaining weak duplicates are candidates. Real sale still needs revalidation.
                action = "SELL_CANDIDATE";
                if (simulation()) soft.add("modo simulação: nenhuma venda será executada");
                if (!autoSell()) soft.add("venda automática desligada");
            }

            out.put("action", action);
            out.put("hardReasons", new JSONArray(hard));
            out.put("notes", new JSONArray(soft));
            out.put("armed", isArmed());
            out.put("simulation", simulation());
        } catch (JSONException ignored) {}
        return out;
    }

    /** Final transaction gate. Every destructive sale passes here immediately before confirmation. */
    public JSONObject finalGate(JSONObject pokemon, boolean consistent, boolean teamScanComplete,
                                int speciesCount, int rankWithinSpecies, int soldThisCycle) {
        JSONObject base = evaluate(pokemon, consistent, teamScanComplete, speciesCount, rankWithinSpecies);
        try {
            JSONArray reasons = base.optJSONArray("hardReasons");
            if (reasons == null) reasons = new JSONArray();
            if (!"SELL_CANDIDATE".equals(base.optString("action"))) {
                base.put("authorized", false);
                return base;
            }
            if (!isArmed()) reasons.put("vendas não estão armadas/autorizadas");
            if (salePrice() <= 0) reasons.put("preço de venda não configurado");
            if (soldThisCycle >= maxSalesPerCycle()) reasons.put("limite de vendas do ciclo atingido");
            boolean authorized = reasons.length() == 0;
            base.put("hardReasons", reasons);
            base.put("authorized", authorized);
            base.put("action", authorized ? "SELL" : "PROTECT");
        } catch (JSONException ignored) {}
        return base;
    }

    private boolean isLegendaryOrMythical(String species, String rarity) {
        String s = normalize(species);
        String r = normalize(rarity);
        if (r.contains("legend") || r.contains("mitic") || r.contains("mythic") || r.contains("ultra beast") || r.contains("ultracriatura")) return true;
        return LEGENDARY_MYTHICAL.contains(s);
    }

    private int intPref(String key, int fallback) {
        try { return Integer.parseInt(p.getString(key, String.valueOf(fallback)).trim()); }
        catch (Exception e) { return fallback; }
    }

    private long longPref(String key, long fallback) {
        try { return Long.parseLong(p.getString(key, String.valueOf(fallback)).trim()); }
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
        String v = normalize(value);
        for (String s : list) if (normalize(s).equals(v)) return true;
        return false;
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT)
                .replace("á","a").replace("à","a").replace("ã","a").replace("â","a")
                .replace("é","e").replace("ê","e").replace("í","i")
                .replace("ó","o").replace("ô","o").replace("õ","o")
                .replace("ú","u").replace("ç","c");
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
