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
    private ScrollView report, home;
    private LinearLayout controls;
    private AppUpdater updater;
    private TextView updateStatus;
    private int visibleLimit=40;
    private String screen="home";
    private TextView status,counts;
    private Button audit,gameTab,resultsTab,stopButton;
    private volatile String runToken="";
    private boolean running=false,complete=false;
    private String filter="ALL",query="";
    private JSONObject teams=new JSONObject();
    private final LinkedHashMap<String,JSONObject> rows=new LinkedHashMap<>();

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override public void onCreate(Bundle state){
        super.onCreate(state);rules=new SaleSafetyRules(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        updater=new AppUpdater(this,message->{if(updateStatus!=null)updateStatus.setText(message);});
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(android.view.Gravity.CENTER_VERTICAL);head.setPadding(dp(18),dp(8),dp(14),dp(8));
        TextView title=text("IdleDex",24,INK,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("COMPANION",11,GREEN,true);head.addView(badge);
        Button settings=button("Proteção",PANEL);settings.setContentDescription("Configurar regras de proteção");settings.setOnClickListener(v->settings());head.addView(settings);root.addView(head);
        controls=column();controls.setPadding(dp(14),0,dp(14),dp(8));
        status=text("Pause o AUTO no jogo antes de analisar a Box.",13,MUTED,false);controls.addView(status);
        counts=text("0 encontrados",13,INK,true);controls.addView(counts);
        LinearLayout actions=new LinearLayout(this);
        audit=button("Analisar Box · simulação",0xff245886);actions.addView(audit,new LinearLayout.LayoutParams(0,dp(48),1));audit.setOnClickListener(v->start());
        stopButton=button("Parar",0xff653d4d);stopButton.setEnabled(false);stopButton.setOnClickListener(v->stop("Leitura parada. Os resultados são parciais."));actions.addView(stopButton,new LinearLayout.LayoutParams(dp(76),dp(48)));controls.addView(actions);root.addView(controls);
        FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        web=new WebView(this);web.setBackgroundColor(BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.addJavascriptInterface(new Bridge(),"IdleSell");web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){invalidate();status.setText("Carregando jogo…");}
            @Override public void onPageFinished(WebView v,String url){if(isGame(url)){inject();status.setText("Pause o AUTO e deixe a Box sem filtros para analisar.");}else status.setText("Conclua o login no jogo para continuar.");}
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError error){if(req.isForMainFrame())status.setText("Não foi possível carregar o jogo. Confira sua conexão e use Recarregar no Início.");}
        });
        content.addView(web,new FrameLayout.LayoutParams(-1,-1));
        report=new ScrollView(this);report.setFillViewport(true);body=column();body.setPadding(dp(14),dp(12),dp(14),dp(20));report.addView(body);content.addView(report,new FrameLayout.LayoutParams(-1,-1));
        home=new ScrollView(this);home.setFillViewport(true);buildHome();content.addView(home,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(10),dp(8),dp(10),dp(8));tabs.setBackgroundColor(PANEL);
        Button homeTab=button("Início",PANEL);gameTab=button("Jogo",PANEL);resultsTab=button("Minha Box",PANEL);
        for(Button button:new Button[]{homeTab,gameTab,resultsTab})tabs.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(tabs);
        homeTab.setOnClickListener(v->showHome());gameTab.setOnClickListener(v->showGame(true));resultsTab.setOnClickListener(v->{showGame(false);render();});
        buildReport();setContentView(root);showHome();
        // Load lazily: the home screen does not keep an unseen game running.
        updater.checkForUpdate(false);
    }
    private void buildHome(){
        LinearLayout c=column();c.setPadding(dp(20),dp(20),dp(20),dp(24));home.addView(c);
        c.addView(text("Seu jogo.\nSua coleção protegida.",30,INK,true));
        c.addView(text("Jogue e organize seus Pokémon no mesmo lugar.",15,MUTED,false));
        LinearLayout hero=card();hero.addView(text("Continuar aventura",20,INK,true));hero.addView(text("O jogo fica aberto na mesma tela. Alterne para a Box sem perder sua sessão.",14,MUTED,false));
        Button play=button("Abrir jogo  →",0xff245886);hero.addView(play,new LinearLayout.LayoutParams(-1,dp(52)));play.setOnClickListener(v->showGame(true));c.addView(hero);
        LinearLayout collection=card();collection.addView(text("Conheça sua Box",20,INK,true));collection.addView(text("1. Pause o AUTO no jogo.\n2. Deixe a Box sem filtros.\n3. Toque em Analisar Box.",14,MUTED,false));
        Button box=button("Ver minha coleção",PANEL);collection.addView(box,new LinearLayout.LayoutParams(-1,dp(48)));box.setOnClickListener(v->{showGame(false);render();});c.addView(collection);
        LinearLayout safety=card();safety.addView(text("SIMULAÇÃO ATIVA",12,GREEN,true));safety.addView(text("A análise explica quem está protegido e o que precisa de revisão. Nenhum Pokémon é vendido por este app.",14,INK,false));c.addView(safety);
        LinearLayout updates=card();updates.addView(text("Aplicativo · v"+BuildConfig.VERSION_NAME,17,INK,true));updateStatus=text("Atualizações verificadas com segurança.",13,MUTED,false);updates.addView(updateStatus);
        Button update=button("Verificar atualização",0xff245886);update.setOnClickListener(v->updater.checkForUpdate(true));updates.addView(update,new LinearLayout.LayoutParams(-1,dp(48)));c.addView(updates);
        Button reload=button("Recarregar jogo",PANEL);reload.setOnClickListener(v->{if(running){status.setText("Pare a análise antes de recarregar.");showGame(true);return;}showGame(true);web.reload();});c.addView(reload,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void showHome(){if(running)stop("Leitura interrompida ao voltar ao Início. Analise novamente quando estiver pronto.");screen="home";home.setVisibility(View.VISIBLE);web.setVisibility(View.GONE);report.setVisibility(View.GONE);controls.setVisibility(View.GONE);if(!running)web.onPause();}
    private boolean isGame(String url){try{Uri u=Uri.parse(url);return "https".equals(u.getScheme())&&"idledex.com".equals(u.getHost())&&"/play".equals(u.getPath());}catch(Exception e){return false;}}
    private String readAsset(String name)throws Exception{try(java.io.InputStream in=getAssets().open(name);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private void inject(){try{String reader=readAsset("box-reader.js"),driver=readAsset("box-audit.js");web.evaluateJavascript(reader+"\n"+driver,null);}catch(Exception e){status.setText("Não foi possível carregar a auditoria.");}}
    private void invalidate(){runToken="";running=false;complete=false;rules.disarm();if(audit!=null)audit.setEnabled(true);if(stopButton!=null)stopButton.setEnabled(false);}
    private void stop(String message){if(web!=null)web.evaluateJavascript("window.IdleBoxAudit?.stop();",null);rules.setEmergencyStop(true);invalidate();status.setText(message);render();}
    private void start(){
        if(running)return;if(!isGame(web.getUrl())){status.setText("O jogo ainda está carregando. Aguarde e tente novamente.");showGame(true);return;}
        rules.setEmergencyStop(false);visibleLimit=40;rows.clear();complete=false;teams=new JSONObject();running=true;runToken=UUID.randomUUID().toString();audit.setEnabled(false);stopButton.setEnabled(true);counts.setText("Lendo Box · etapa 1 de 2…");showGame(true);
        status.setText("Lendo os IDs atuais da Box. Se a coleção mudar durante a leitura, tudo ficará em Revisar.");
        web.evaluateJavascript("window.IdleBoxAudit ? (window.IdleBoxAudit.start("+JSONObject.quote(runToken)+"),true) : false",result->{if(!"true".equals(result))stop("Auditoria indisponível. Reabra o jogo e tente novamente.");});
    }
    private void showGame(boolean yes){if(!yes&&running)stop("Leitura interrompida. Estes resultados são parciais; analise novamente para concluir.");screen=yes?"game":"report";home.setVisibility(View.GONE);controls.setVisibility(View.VISIBLE);web.setVisibility(yes?View.VISIBLE:View.GONE);report.setVisibility(yes?View.GONE:View.VISIBLE);if(yes||running)web.onResume();else web.onPause();if(yes&&web.getUrl()==null)web.loadUrl("https://idledex.com/play");gameTab.setTextColor(yes?GREEN:INK);resultsTab.setTextColor(yes?INK:GREEN);}
    private void buildReport(){
        LinearLayout info=card();info.addView(text("Sua coleção, explicada.",18,INK,true));
        info.addView(text("Verde: proteção encontrada. Amarelo: faltam informações. Azul: candidato em simulação. Times salvos e venda no NPC ainda exigem confirmação; nenhuma venda é executada.",13,MUTED,false));
        Button why=button("Entender as proteções",PANEL);why.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Proteções permanentes").setMessage("Equipe ativa e times salvos; Shiny; Lendário, Mítico e Ultra Beast; 4★ ou mais; evento/especial; trava/favorito; Excelente e Excepcional; IV alto; espécies configuradas; únicos e ao menos 3 melhores.\n\nDados ausentes, leitura divergente ou identidade incerta impedem candidatura. Times salvos protegem conservadoramente toda a espécie visível.\n\nNenhum botão deste painel vende Pokémon.").setPositiveButton("Entendi",null).show());info.addView(why);body.addView(info);
        EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Buscar Pokémon ou motivo");search.setTextColor(INK);search.setHintTextColor(MUTED);body.addView(search);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){query=SalePolicy.normalize(s.toString());visibleLimit=40;render();}public void afterTextChanged(Editable e){}});
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout filters=new LinearLayout(this);
        String[] keys={"ALL","PROTECT","REVIEW","SELL_CANDIDATE"},labels={"Todos","Protegidos","Revisar","Candidatos"};
        for(int i=0;i<keys.length;i++){String key=keys[i];Button b=button(labels[i],PANEL);b.setOnClickListener(v->{filter=key;visibleLimit=40;render();});filters.addView(b);}scroll.addView(filters);body.addView(scroll);
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
            if(shown++>=visibleLimit)continue;
            LinearLayout c=card();int color=a.equals("PROTECT")?GREEN:a.equals("REVIEW")?YELLOW:BLUE;
            c.addView(text(a.equals("PROTECT")?"PROTEGIDO":a.equals("REVIEW")?"REVISAR":"CANDIDATO À VENDA",11,color,true));
            c.addView(text(p.optString("species","Pokémon"),21,INK,true));
            c.addView(text((p.isNull("stars")?"?":p.optString("stars"))+"★  ·  IV "+(p.isNull("iv")?"?":p.optString("iv"))+"/186  ·  "+p.optString("quality","?"),13,MUTED,false));
            c.addView(text(reasons,13,INK,false));TextView id=text("ID Box: "+p.optString("id","não confirmado"),10,MUTED,false);id.setTextIsSelectable(true);c.addView(id);list.addView(c);
        }
        counts.setText(rows.size()+" encontrados  ·  "+protectedN+" protegidos  ·  "+reviewN+" revisar  ·  "+candidateN+" candidatos");
        if(shown==0)list.addView(text(rows.isEmpty()?"Sua auditoria aparecerá aqui, Pokémon por Pokémon.":"Nenhum resultado neste filtro.",14,MUTED,false));
        if(shown>visibleLimit){Button more=button("Mostrar mais · "+visibleLimit+" de "+shown,PANEL);more.setOnClickListener(v->{visibleLimit+=40;render();});list.addView(more,new LinearLayout.LayoutParams(-1,dp(48)));}
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
    @Override protected void onPause(){super.onPause();if(running)stop("Auditoria pausada ao sair do aplicativo. Execute novamente.");web.onPause();CookieManager.getInstance().flush();}
    @Override protected void onResume(){super.onResume();if(web!=null&&screen.equals("game"))web.onResume();if(updater!=null)updater.resumePendingInstall();}
    @Override public void onBackPressed(){if(!screen.equals("home")){showHome();return;}super.onBackPressed();}
    @Override protected void onDestroy(){if(updater!=null)updater.destroy();invalidate();if(web!=null){web.removeJavascriptInterface("IdleSell");web.destroy();}super.onDestroy();}
    public final class Bridge {
        @JavascriptInterface public boolean isStopped(String token){return !token.equals(runToken)||rules.emergencyStop();}
        @JavascriptInterface public void report(String token,String kind,String json){
            if(token.isEmpty()||!token.equals(runToken)||json.length()>4_000_000)return;
            runOnUiThread(()->{if(!token.equals(runToken)||!isGame(web.getUrl()))return;try{JSONObject o=new JSONObject(json);
                if(kind.equals("item")){JSONObject p=o.getJSONObject("pokemon");String id=p.getString("id");JSONObject row=new JSONObject().put("pokemon",p).put("consistent",false);rows.put(id,row);counts.setText(rows.size()+" encontrados · leitura "+o.optInt("round")+" de 2");}
                else if(kind.equals("progress"))status.setText("Leitura "+o.optInt("round")+"/2 · página "+o.optInt("page")+"/"+o.optInt("pages"));
                else if(kind.equals("status"))status.setText(o.optString("message"));
                else if(kind.equals("complete")){JSONArray arr=o.getJSONArray("rows");rows.clear();boolean duplicate=false;for(int i=0;i<arr.length();i++){JSONObject row=arr.getJSONObject(i);String id=row.getJSONObject("pokemon").getString("id");if(rows.put(id,row)!=null)duplicate=true;}complete=o.optBoolean("complete")&&!duplicate;teams=o.getJSONObject("teams");running=false;runToken="";audit.setEnabled(true);stopButton.setEnabled(false);status.setText(complete?(teams.optBoolean("complete")?"Box relida. Toque em Resultados para ver os Pokémon e as proteções.":"Box relida. Veja as proteções em Minha Box. Dados não confirmados exigem revisão."):"A Box ou algum ID mudou durante a leitura. Dados divergentes exigem revisão.");render();}
                else if(kind.equals("error")){invalidate();stopButton.setEnabled(false);status.setText(o.optString("message")+" Nada foi vendido.");render();}
            }catch(Exception e){stop("Erro ao ler auditoria. Venda permanece bloqueada.");}});
        }
    }
}
