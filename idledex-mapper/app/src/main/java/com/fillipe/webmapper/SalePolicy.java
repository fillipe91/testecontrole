package com.fillipe.webmapper;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.text.Normalizer;

/** Pure, fail-closed policy. Never turns missing evidence into false. */
public final class SalePolicy {
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

    public static String normalize(String s) {
        return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").trim().toLowerCase(Locale.ROOT);
    }
    public static JSONObject evaluate(JSONObject p, boolean consistent, boolean complete,
            boolean teamsComplete, int count, int rank, int minIv, int keep, String speciesCsv) {
        JSONArray protections=new JSONArray(), doubts=new JSONArray();
        String species=normalize(p.optString("species")), rarity=normalize(p.optString("rarity"));
        int stars=p.optInt("stars",-1), iv=p.optInt("iv",-1);
        if(p.optBoolean("inTeam"))protections.put("Equipe ativa");
        if(p.optBoolean("savedSpecies"))protections.put("Espécie presente em time salvo — todos os exemplares preservados por precaução");
        if(p.optBoolean("shiny"))protections.put("Shiny");
        if(p.optBoolean("locked"))protections.put("Travado pelo dono ou pelo jogo");
        if(p.optBoolean("favorite"))protections.put("Favoritado");
        if(p.optBoolean("event")||p.optBoolean("special"))protections.put("Evento ou especial");
        if(p.optBoolean("listed"))protections.put("Já anunciado no mercado");
        if(LEGENDARY_MYTHICAL.contains(species)||rarity.contains("legend")||rarity.contains("myth")||rarity.contains("mitic")||rarity.contains("ultra"))protections.put("Lendário, Mítico ou Ultra Beast");
        if(stars>=4)protections.put(stars+"★ — alta qualidade protegida");
        if(Arrays.asList("excelente","excepcional","impecavel").contains(normalize(p.optString("quality"))))protections.put("Qualidade "+p.optString("quality"));
        int threshold=Math.max(0,Math.min(135,minIv));
        if(iv>=threshold&&iv<=186)protections.put("IV alto: "+iv+"/186 (limite "+threshold+")");
        for(String s:speciesCsv.split(","))if(!species.isEmpty()&&species.equals(normalize(s))){protections.put("Espécie protegida nas regras");break;}
        if(complete&&count==1)protections.put("Exemplar único");
        if(complete&&rank>0&&rank<=Math.max(3,keep))protections.put("Entre os "+Math.max(3,keep)+" melhores da espécie (empates preservados)");
        if(!complete)doubts.put("Auditoria incompleta ou coleção alterada");
        if(!consistent)doubts.put("Duas leituras independentes não conferiram");
        if(!teamsComplete)doubts.put("IDs dos times salvos não disponíveis");
        if(p.optString("id").isEmpty()||!"data-creature".equals(p.optString("identitySource")))doubts.put("ID individual da Box não comprovado");
        if(!p.optBoolean("crossSurfaceIdentity"))doubts.put("Mesmo ID não comprovado no NPC Jessie");
        if(species.isEmpty()||p.isNull("speciesKey")||p.optString("speciesKey").isEmpty())doubts.put("Espécie ou forma não identificada");
        if(!p.optBoolean("detailVerified")||iv<0||iv>186||p.isNull("iv"))doubts.put("Detalhe/IV não confirmado");
        if(stars<0||stars>6||p.isNull("stars")||p.optString("quality").isEmpty())doubts.put("Estrelas/qualidade não confirmadas");
        if(!Arrays.asList("common","uncommon","rare","epic","pseudo","pseudo-legendary","legendary","mythical","ultra-beast").contains(rarity))doubts.put("Raridade não reconhecida");
        for(String field:Arrays.asList("shiny","savedSpecies","locked","favorite","event","special","inTeam","listed"))
            if(!(p.opt(field) instanceof Boolean))doubts.put("Estado não confirmado: "+label(field));
        if(count<1||rank<1)doubts.put("Duplicatas/ranking não confirmados");
        if(!p.optString("readError").isEmpty())doubts.put(p.optString("readError"));
        JSONObject result=new JSONObject();
        try {
            result.put("action",protections.length()>0?"PROTECT":doubts.length()>0?"REVIEW":"SELL_CANDIDATE");
            result.put("protectionReasons",protections);result.put("reviewReasons",doubts);
            result.put("simulation",true);result.put("authorized",false);
        }catch(Exception ignored){}
        return result;
    }
    private static String label(String f){switch(f){case "locked":return "trava";case "favorite":return "favorito";case "event":return "evento";case "special":return "especial";case "inTeam":return "equipe";case "listed":return "anúncio";default:return f;}}
    private SalePolicy(){}
}
