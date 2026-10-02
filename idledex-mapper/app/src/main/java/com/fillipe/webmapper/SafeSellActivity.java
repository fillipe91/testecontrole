package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Audits and explains. Intentionally has no sell/select-for-sale/release capability. */
public class SafeSellActivity extends Activity {
    private static final int BG=0xff101827,PANEL=0xff1b293d,INK=0xffeef4ff,MUTED=0xffa7b8ce,BLUE=0xff64b5ff,GREEN=0xff68d5af,YELLOW=0xffffcd77;
    private SaleSafetyRules rules;
    private WebView web;
    private LinearLayout list,body;
    private ScrollView report;
    private TextView status,counts;
    private Button audit;
    private volatile String runToken="";
    private boolean running=false,complete=false;
    private String filter="ALL",query="";
    private JSONObject teams=new JSONObject();
    private final LinkedHashMap<String,JSONObject> rows=new LinkedHashMap<>();

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override public void onCreate(Bundle state){
        super.onCreate(state);rules=new SaleSafetyRules(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(14),dp(10),dp(14),0);
        LinearLayout head=new LinearLayout(this);TextView title=text("Venda Segura",24,INK,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button stop=button("PARAR",0xff653d4d);stop.setOnClickListener(v->stop("Auditoria parada. Nenhuma venda foi executada."));head.addView(stop);root.addView(head);
        TextView tag=text("SIMULAÇÃO  ·  NPC JESSIE",12,GREEN,true);tag.setPadding(0,dp(4),0,dp(8));root.addView(tag);
        status=text("Audite sua coleção. Nada será vendido ou selecionado no NPC.",13,MUTED,false);root.addView(status);
        counts=text("0 encontrados",14,INK,true);counts.setPadding(0,dp(9),0,dp(6));root.addView(counts);
        LinearLayout actions=new LinearLayout(this);audit=button("Auditar Box",0xff245886);Button game=button("Jogo",PANEL), results=button("Resultado",PANEL), settings=button("Regras",PANEL);
        for(Button b:new Button[]{audit,game,results,settings}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(2),0,dp(2),0);actions.addView(b,lp);}root.addView(actions);
        audit.setOnClickListener(v->start());game.setOnClickListener(v->showGame(true));results.setOnClickListener(v->{showGame(false);render();});settings.setOnClickListener(v->settings());
        web=new WebView(this);web.setBackgroundColor(BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.addJavascriptInterface(new Bridge(),"IdleSell");web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){invalidate();status.setText("Carregando jogo…");}
            @Override public void onPageFinished(WebView v,String url){if(isGame(url)){inject();status.setText("Faça login, se necessário, e toque em Auditar Box.");}else status.setText("Conclua o login no jogo.");}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        report=new ScrollView(this);body=column();body.setPadding(0,dp(12),0,dp(20));report.addView(body);root.addView(report,new LinearLayout.LayoutParams(-1,0,1));
        buildReport();setContentView(root);showGame(true);web.loadUrl("https://idledex.com/play");
    }
    private boolean isGame(String url){try{Uri u=Uri.parse(url);return "https".equals(u.getScheme())&&"idledex.com".equals(u.getHost())&&"/play".equals(u.getPath());}catch(Exception e){return false;}}
    private String readAsset(String name)throws Exception{try(java.io.InputStream in=getAssets().open(name);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private void inject(){try{String reader=readAsset("box-reader.js"),driver=readAsset("box-audit.js");web.evaluateJavascript(reader+"\n"+driver,null);}catch(Exception e){status.setText("Não foi possível carregar a auditoria.");}}
    private void invalidate(){runToken="";running=false;complete=false;rules.disarm();if(audit!=null)audit.setEnabled(true);}
    private void stop(String message){if(web!=null)web.evaluateJavascript("window.IdleBoxAudit?.stop();",null);rules.setEmergencyStop(true);invalidate();status.setText(message);render();}
    private void start(){
        if(running)return;if(!isGame(web.getUrl())){status.setText("Entre em idledex.com/play primeiro.");showGame(true);return;}
        rules.setEmergencyStop(false);rows.clear();complete=false;teams=new JSONObject();running=true;runToken=UUID.randomUUID().toString();audit.setEnabled(false);counts.setText("Iniciando leitura 1 de 2…");showGame(true);
        status.setText("Mantenha o jogo aberto. Não altere Box ou times durante a auditoria.");
        web.evaluateJavascript("window.IdleBoxAudit ? (window.IdleBoxAudit.start("+JSONObject.quote(runToken)+"),true) : false",result->{if(!"true".equals(result))stop("Auditoria indisponível. Reabra o jogo e tente novamente.");});
    }
    private void showGame(boolean yes){web.setVisibility(yes?View.VISIBLE:View.GONE);report.setVisibility(yes?View.GONE:View.VISIBLE);}
    private void buildReport(){
        LinearLayout info=card();info.addView(text("Se houver dúvida, fica com você.",18,INK,true));
        info.addView(text("A Jessie compra Pokémon por silver. Seus cartões e os times salvos não fornecem IDs individuais verificáveis. Esta versão apenas analisa; venda real está bloqueada.",13,MUTED,false));
        Button why=button("Entender as proteções",PANEL);why.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Proteções permanentes").setMessage("Equipe ativa e times salvos; Shiny; Lendário, Mítico e Ultra Beast; 4★ ou mais; evento/especial; trava/favorito; Excelente e Excepcional; IV alto; espécies configuradas; únicos e ao menos 3 melhores.\n\nDados ausentes, leitura divergente ou identidade incerta impedem candidatura. Times salvos protegem conservadoramente toda a espécie visível.\n\nNenhum botão deste painel vende Pokémon.").setPositiveButton("Entendi",null).show());info.addView(why);body.addView(info);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Buscar Pokémon ou motivo");search.setTextColor(INK);search.setHintTextColor(MUTED);body.addView(search);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){query=SalePolicy.normalize(s.toString());render();}public void afterTextChanged(Editable e){}});
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout filters=new LinearLayout(this);
        String[] keys={"ALL","PROTECT","REVIEW","SELL_CANDIDATE"},labels={"Todos","Protegidos","Revisar","Candidatos"};
        for(int i=0;i<keys.length;i++){String key=keys[i];Button b=button(labels[i],PANEL);b.setOnClickListener(v->{filter=key;render();});filters.addView(b);}scroll.addView(filters);body.addView(scroll);
        list=column();body.addView(list);render();
    }
    private void render(){
        if(list==null)return;list.removeAllViews();int protectedN=0,reviewN=0,candidateN=0,shown=0;
        List<JSONObject> all=new ArrayList<>();Map<String,List<JSONObject>> groups=new HashMap<>();
        Set<String> savedDex=new HashSet<>();JSONArray saved=teams.optJSONArray("saved");
        if(saved!=null)for(int i=0;i<saved.length();i++){JSONArray sprites=saved.optJSONObject(i).optJSONArray("sprites");if(sprites!=null)for(int j=0;j<sprites.length();j++){String sprite=sprites.optJSONObject(j).optString("sprite");java.util.regex.Matcher m=java.util.regex.Pattern.compile("/sprites/poke/(\\d+)").matcher(sprite);if(m.find())savedDex.add(m.group(1));}}
        for(JSONObject item:rows.values()){JSONObject p=item.optJSONObject("pokemon");if(p==null)continue;try{p.put("savedSpecies",savedDex.contains(p.optString("speciesKey")));}catch(Exception ignored){}
            String key=p.optString("speciesKey","");groups.computeIfAbsent(key,k->new ArrayList<>()).add(p);all.add(item);}
        for(JSONObject item:all){JSONObject p=item.optJSONObject("pokemon");List<JSONObject> group=groups.get(p.optString("speciesKey",""));int rank=1;boolean rankKnown=complete;
            for(JSONObject other:group){if(other.isNull("iv")||other.optInt("iv",-1)<0||other.isNull("stars"))rankKnown=false;
                if(other.optInt("stars",-1)>p.optInt("stars",-1)||(other.optInt("stars",-1)==p.optInt("stars",-1)&&other.optInt("iv",-1)>p.optInt("iv",-1)))rank++;}
            JSONObject d=rules.evaluate(p,item.optBoolean("consistent"),complete,teams.optBoolean("complete"),group.size(),rankKnown?rank:0);String a=d.optString("action","REVIEW");
            if(a.equals("PROTECT"))protectedN++;else if(a.equals("REVIEW"))reviewN++;else candidateN++;
            String reasons=reasonText(d);if(!filter.equals("ALL")&&!filter.equals(a))continue;
            if(!query.isEmpty()&&!SalePolicy.normalize(p.optString("species")+" "+reasons).contains(query))continue;
            if(shown++>=100)continue;
            LinearLayout c=card();int color=a.equals("PROTECT")?GREEN:a.equals("REVIEW")?YELLOW:BLUE;
            c.addView(text(a.equals("PROTECT")?"PROTEGIDO":a.equals("REVIEW")?"REVISAR":"CANDIDATO À VENDA",11,color,true));
            c.addView(text(p.optString("species","Pokémon"),21,INK,true));
            c.addView(text((p.isNull("stars")?"?":p.optString("stars"))+"★  ·  IV "+(p.isNull("iv")?"?":p.optString("iv"))+"/186  ·  "+p.optString("quality","?"),13,MUTED,false));
            c.addView(text(reasons,13,INK,false));TextView id=text("ID Box: "+p.optString("id","não confirmado"),10,MUTED,false);id.setTextIsSelectable(true);c.addView(id);list.addView(c);
        }
        counts.setText(rows.size()+" encontrados  ·  "+protectedN+" protegidos  ·  "+reviewN+" revisar  ·  "+candidateN+" candidatos");
        if(shown==0)list.addView(text(rows.isEmpty()?"Sua auditoria aparecerá aqui, Pokémon por Pokémon.":"Nenhum resultado neste filtro.",14,MUTED,false));
        if(shown>100)list.addView(text("Mostrando 100 de "+shown+". Use a busca para ver os demais.",13,MUTED,false));
    }
    private String reasonText(JSONObject d){StringBuilder s=new StringBuilder();for(String key:new String[]{"protectionReasons","reviewReasons"}){JSONArray a=d.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++)s.append("• ").append(a.optString(i)).append('\n');}return s.toString().trim();}
    private void settings(){if(running){status.setText("Pare a auditoria antes de mudar as regras.");return;}LinearLayout c=column();c.setPadding(dp(22),dp(10),dp(22),dp(10));
        c.addView(text("Limites só podem ficar mais protetores. Simulação é obrigatória nesta versão.",14,Color.DKGRAY,false));
        EditText iv=field(c,"Proteger IV a partir de (0–135)",String.valueOf(rules.minIvProtect()),true),best=field(c,"Manter pelo menos (3–100)",String.valueOf(rules.keepBestPerSpecies()),true),species=field(c,"Espécies protegidas, separadas por vírgula",rules.protectedSpeciesCsv(),false);
        new AlertDialog.Builder(this).setTitle("Sua proteção").setView(c).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{try{int a=Integer.parseInt(iv.getText().toString()),b=Integer.parseInt(best.getText().toString());if(a<0||a>135||b<3||b>100)throw new Exception();rules.save(a,b,species.getText().toString());render();}catch(Exception e){status.setText("Valores inválidos. Regras anteriores preservadas.");}}).show();}
    private EditText field(LinearLayout c,String label,String value,boolean number){c.addView(text(label,12,Color.DKGRAY,true));EditText e=new EditText(this);e.setText(value);if(number)e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);c.addView(e);return e;}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(15),dp(13),dp(15),dp(13));GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(16));c.setBackground(g);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));c.setLayoutParams(lp);return c;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(INK);GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(10));b.setBackground(g);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onPause(){super.onPause();if(running)stop("Auditoria pausada ao sair da tela. Execute novamente.");}
    @Override protected void onDestroy(){invalidate();if(web!=null){web.removeJavascriptInterface("IdleSell");web.destroy();}super.onDestroy();}
    public final class Bridge {
        @JavascriptInterface public boolean isStopped(String token){return !token.equals(runToken)||rules.emergencyStop();}
        @JavascriptInterface public void report(String token,String kind,String json){
            if(token.isEmpty()||!token.equals(runToken)||json.length()>4_000_000)return;
            runOnUiThread(()->{if(!token.equals(runToken)||!isGame(web.getUrl()))return;try{JSONObject o=new JSONObject(json);
                if(kind.equals("item")){JSONObject p=o.getJSONObject("pokemon");String id=p.getString("id");JSONObject row=new JSONObject().put("pokemon",p).put("consistent",false);rows.put(id,row);counts.setText(rows.size()+" encontrados · leitura "+o.optInt("round")+" de 2");}
                else if(kind.equals("progress"))status.setText("Leitura "+o.optInt("round")+"/2 · página "+o.optInt("page")+"/"+o.optInt("pages"));
                else if(kind.equals("status"))status.setText(o.optString("message"));
                else if(kind.equals("complete")){JSONArray arr=o.getJSONArray("rows");rows.clear();boolean duplicate=false;for(int i=0;i<arr.length();i++){JSONObject row=arr.getJSONObject(i);String id=row.getJSONObject("pokemon").getString("id");if(rows.put(id,row)!=null)duplicate=true;}complete=o.optBoolean("complete")&&!duplicate;teams=o.getJSONObject("teams");running=false;runToken="";audit.setEnabled(true);status.setText(complete?"Dupla leitura concluída. Venda real bloqueada: IDs do NPC/times não verificados.":"Coleção mudou. Resultado para revisão; refaça a auditoria.");showGame(false);render();}
                else if(kind.equals("error")){invalidate();status.setText(o.optString("message"));showGame(false);render();}
            }catch(Exception e){stop("Erro ao ler auditoria. Venda permanece bloqueada.");}});
        }
    }
}
