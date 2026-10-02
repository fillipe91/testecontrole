package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-safe sale mode.
 *
 * The sale flow is intentionally conservative. It first performs a complete audit and creates
 * a candidate queue. A real transaction can only be attempted after explicit arming and is
 * revalidated immediately before the final confirmation. Any missing/mismatched signal aborts.
 */
public class SafeSellActivity extends Activity {
    private static final int BG = Color.rgb(13,17,23);
    private static final int PANEL = Color.rgb(24,30,38);
    private static final int PANEL2 = Color.rgb(31,39,49);
    private static final int TEXT = Color.rgb(238,244,250);
    private static final int MUTED = Color.rgb(166,180,195);
    private static final int ACCENT = Color.rgb(68,153,255);
    private static final int SAFE = Color.rgb(55,196,120);
    private static final int WARN = Color.rgb(255,183,77);
    private static final int DANGER = Color.rgb(255,93,93);

    private SaleSafetyRules rules;
    private WebView webView;
    private TextView status;
    private TextView modeBadge;
    private TextView countText;
    private Button executeButton;
    private boolean pageReady = false;
    private int soldThisCycle = 0;

    private final List<JSONObject> candidates = new ArrayList<>();
    private final Map<String, JSONObject> approvedById = new HashMap<>();

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rules = new SaleSafetyRules(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildTop());
        root.addView(buildSafetyStrip());
        root.addView(buildActions());

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        webView.setBackgroundColor(BG);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " IdleDexSafeSell/2.1");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new SellBridge(), "IdleSell");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isIdleDex(url)) {
                    pageReady = true;
                    setStatus("IdleDex conectado · Venda Segura pronta");
                    injectSafeSell();
                } else {
                    pageReady = false;
                    setStatus("Login externo · faça o login normalmente");
                }
            }
        });
        root.addView(webView);
        setContentView(root);
        webView.loadUrl("https://idledex.com/play");
        refreshHeader();
    }

    private View buildTop() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14),dp(9),dp(14),dp(8));
        wrap.setBackgroundColor(PANEL);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Venda Segura", TEXT, 20, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button back = smallButton("Início", PANEL2);
        back.setOnClickListener(v -> finish());
        Button stop = smallButton("PARAR", DANGER);
        stop.setOnClickListener(v -> {
            rules.setEmergencyStop(true);
            rules.disarm();
            if (webView != null) webView.evaluateJavascript("window.IdleSafeSell && (window.IdleSafeSell.abort=true);", null);
            setStatus("PARADA DE EMERGÊNCIA · vendas desarmadas");
            refreshHeader();
        });
        row.addView(title); row.addView(back); row.addView(space(8)); row.addView(stop);

        LinearLayout sub = new LinearLayout(this);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.setPadding(0,dp(7),0,0);
        modeBadge = badge("SIMULAÇÃO", WARN);
        countText = label("0 candidatos", MUTED, 12, false);
        countText.setPadding(dp(10),0,0,0);
        status = label("Carregando…", MUTED, 12, false);
        status.setPadding(dp(10),0,0,0);
        status.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sub.addView(modeBadge); sub.addView(countText); sub.addView(status);
        wrap.addView(row); wrap.addView(sub);
        return wrap;
    }

    private View buildSafetyStrip() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12),dp(8),dp(12),dp(8));
        c.setBackgroundColor(Color.rgb(20,45,35));
        TextView t = label("TRAVAS PERMANENTES", Color.rgb(130,240,180), 12, true);
        TextView d = label("Times ativos/salvos · Shiny · Lendário/Mítico · 4★/5★ · Evento · Travado/Favorito · dados incompletos", TEXT, 12, false);
        d.setPadding(0,dp(3),0,0);
        c.addView(t); c.addView(d);
        return c;
    }

    private View buildActions() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(8),dp(7),dp(8),dp(7));
        row.setBackgroundColor(PANEL2);
        Button audit = smallButton("Auditar Box", ACCENT);
        audit.setOnClickListener(v -> startAudit());
        executeButton = smallButton("Executar venda", SAFE);
        executeButton.setOnClickListener(v -> executeSales());
        Button settings = smallButton("Regras", PANEL2);
        settings.setOnClickListener(v -> showSettings());
        Button arm = smallButton("Armar", WARN);
        arm.setOnClickListener(v -> showArmDialog());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(3),0,dp(3),0);
        audit.setLayoutParams(lp); executeButton.setLayoutParams(lp); settings.setLayoutParams(lp); arm.setLayoutParams(lp);
        row.addView(audit); row.addView(executeButton); row.addView(settings); row.addView(arm);
        return row;
    }

    private void startAudit() {
        if (!pageReady) { setStatus("Faça login no IdleDex primeiro."); return; }
        rules.setEmergencyStop(false);
        candidates.clear(); approvedById.clear(); soldThisCycle = 0;
        refreshHeader();
        setStatus("Auditando Box e conferindo todos os times…");
        webView.evaluateJavascript("window.IdleSafeSell && window.IdleSafeSell.auditBox();", null);
    }

    private void executeSales() {
        if (!pageReady) { setStatus("Faça login no IdleDex primeiro."); return; }
        if (candidates.isEmpty()) { Toast.makeText(this,"Audite a Box primeiro.",Toast.LENGTH_LONG).show(); return; }
        if (rules.simulation()) { Toast.makeText(this,"Modo simulação está ligado. Nenhuma venda será executada.",Toast.LENGTH_LONG).show(); return; }
        if (!rules.isArmed()) { Toast.makeText(this,"Arme as vendas por 10 minutos antes de executar.",Toast.LENGTH_LONG).show(); return; }
        JSONArray a = new JSONArray();
        synchronized (candidates) { for (JSONObject c : candidates) a.put(c); }
        String js = "window.IdleSafeSell && window.IdleSafeSell.executeSales(" + JSONObject.quote(a.toString()) + "," + rules.salePrice() + "," + JSONObject.quote(rules.currency()) + ");";
        setStatus("Revalidando times e candidatos antes de qualquer venda…");
        webView.evaluateJavascript(js, null);
    }

    private void showArmDialog() {
        if (rules.simulation() || !rules.autoSell()) {
            Toast.makeText(this,"Em Regras, desligue Simulação e ative Venda automática.",Toast.LENGTH_LONG).show();
            return;
        }
        if (rules.salePrice() <= 0) {
            Toast.makeText(this,"Configure um preço maior que zero.",Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Autorizar vendas por 10 minutos?")
                .setMessage("As travas de time, Shiny, Lendário/Mítico, 4★/5★, evento, favoritos e dupla leitura continuam obrigatórias e não podem ser desligadas.")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Autorizar",(d,w)->{
                    rules.setEmergencyStop(false);
                    rules.armForMinutes(10);
                    refreshHeader();
                    setStatus("Vendas armadas por 10 min · ainda exigem revalidação final");
                }).show();
    }

    private void showSettings() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22),dp(6),dp(22),dp(12));

        TextView hard = label("Estas proteções NÃO podem ser desligadas:\n• time ativo e todos os times salvos\n• Shiny\n• Lendário, Mítico e Ultra Beast\n• 4★ e 5★\n• evento/especial\n• travado/favorito\n• dados incompletos ou ID incerto\n• único exemplar e melhores cópias", Color.DKGRAY, 13, true);
        hard.setPadding(0,0,0,dp(8));
        CheckBox simulation = check("Modo simulação", rules.simulation());
        CheckBox autoSell = check("Permitir venda automática quando armado", rules.autoSell());
        EditText minIv = field("IV mínimo que fica protegido", String.valueOf(rules.minIvProtect()), true);
        EditText best = field("Manter quantos melhores de cada espécie", String.valueOf(rules.keepBestPerSpecies()), true);
        EditText max = field("Máximo de vendas por ciclo", String.valueOf(rules.maxSalesPerCycle()), true);
        EditText price = field("Preço por Pokémon", String.valueOf(rules.salePrice()), true);
        EditText species = field("Espécies extras protegidas (separadas por vírgula)", rules.protectedSpeciesCsv(), false);
        Spinner currency = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Silver","Gold"});
        currency.setAdapter(adapter); currency.setSelection("Gold".equalsIgnoreCase(rules.currency()) ? 1 : 0);

        box.addView(hard); box.addView(simulation); box.addView(autoSell);
        box.addView(minIv); box.addView(best); box.addView(max); box.addView(price);
        box.addView(label("Moeda do anúncio", Color.DKGRAY, 12, true)); box.addView(currency); box.addView(species);
        sc.addView(box);

        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Regras da Venda Segura").setView(sc)
                .setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int iv = clamp(parseInt(minIv.getText().toString(), rules.minIvProtect()),0,186);
            int keep = clamp(parseInt(best.getText().toString(), rules.keepBestPerSpecies()),1,20);
            int maxN = clamp(parseInt(max.getText().toString(), rules.maxSalesPerCycle()),1,20);
            long pr = parseLong(price.getText().toString(), rules.salePrice());
            rules.save(simulation.isChecked(), autoSell.isChecked(), iv, keep, maxN, pr,
                    String.valueOf(currency.getSelectedItem()), species.getText().toString());
            refreshHeader(); dlg.dismiss();
        }));
        dlg.show();
    }

    private void refreshHeader() {
        runOnUiThread(() -> {
            if (rules.emergencyStop()) {
                modeBadge.setText("STOP"); modeBadge.setBackground(rounded(DANGER,99));
            } else if (rules.isArmed()) {
                modeBadge.setText("ARMADO"); modeBadge.setBackground(rounded(DANGER,99));
            } else if (rules.simulation()) {
                modeBadge.setText("SIMULAÇÃO"); modeBadge.setBackground(rounded(WARN,99));
            } else {
                modeBadge.setText("PROTEGIDO"); modeBadge.setBackground(rounded(SAFE,99));
            }
            countText.setText(candidates.size()+" candidatos");
            if (executeButton != null) executeButton.setEnabled(!candidates.isEmpty());
        });
    }

    private void injectSafeSell() {
        String js = """
(() => {
  if (location.hostname !== 'idledex.com' && !location.hostname.endsWith('.idledex.com')) return;
  if (window.IdleSafeSell) { IdleSell.pushStatus('Venda Segura v2.1 ativa.'); return; }
  const sleep = ms => new Promise(r=>setTimeout(r,ms));
  const clean = (s,n=16000) => String(s||'').replace(/\\s+/g,' ').trim().slice(0,n);
  const low = s => clean(s,1600).toLowerCase();
  const visible = el => { try { const r=el.getBoundingClientRect(),cs=getComputedStyle(el); return r.width>1&&r.height>1&&cs.display!=='none'&&cs.visibility!=='hidden'&&cs.opacity!=='0'; } catch(e){return false;} };
  const txt = el => clean((el&&(el.getAttribute?.('aria-label')||el.getAttribute?.('title')||el.innerText||el.textContent))||'',700);
  const buttons=(root=document)=>[...root.querySelectorAll('button,[role="button"],a[href],input[type="button"],input[type="submit"]')].filter(visible);
  const exact=(n,root=document)=>buttons(root).find(e=>low(txt(e))===low(n));
  const contains=(n,root=document)=>buttons(root).find(e=>low(txt(e)).includes(low(n)));
  const modalRoot=()=>{const x=[...document.querySelectorAll('[role="dialog"],dialog,[aria-modal="true"],.modal,.drawer,.sheet')].filter(visible);return x.length?x[x.length-1]:document.body;};
  const closeTop=()=>{const c=buttons().filter(e=>['fechar','close','×','voltar','cancelar'].includes(low(txt(e)))||low(e.getAttribute?.('aria-label')||'').includes('fechar'));if(!c.length)return false;c[c.length-1].click();return true;};

  function semanticId(root){
    if(!root) return '';
    const attrs=['data-pokemon-id','data-creature-id','data-instance-id','data-pokemon-uid','data-uid'];
    const nodes=[root,...(root.querySelectorAll?[...root.querySelectorAll('*')].slice(0,500):[])];
    for(const n of nodes){
      for(const a of attrs){ const v=n.getAttribute?.(a); if(v&&String(v).trim()) return a+':'+String(v).trim(); }
      const h=n.getAttribute?.('href')||'';
      const m=h.match(/(?:pokemon|creature)[\/=:-]([A-Za-z0-9_-]{4,})/i); if(m) return 'href:'+m[1];
    }
    return '';
  }

  function parseStars(root,text){
    const starAttr=[root,...(root.querySelectorAll?[...root.querySelectorAll('[data-stars],[aria-label],[title]')]:[])].find(n=>n.getAttribute?.('data-stars'));
    if(starAttr){const n=parseInt(starAttr.getAttribute('data-stars'),10);if(n>=0&&n<=5)return {known:true,value:n};}
    const m=text.match(/([0-5])\\s*(?:estrelas?|stars?)/i); if(m)return {known:true,value:parseInt(m[1],10)};
    const sequences=(text.match(/★+/g)||[]).map(s=>s.length).filter(n=>n<=5); if(sequences.length)return {known:true,value:Math.max(...sequences)};
    const labels=[...(root.querySelectorAll?[...root.querySelectorAll('[aria-label],[title]')]:[])].map(e=>(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')).join(' ');
    const lm=labels.match(/([0-5])\\s*(?:estrelas?|stars?)/i); if(lm)return {known:true,value:parseInt(lm[1],10)};
    return {known:false,value:-1};
  }

  function parsePokemon(root){
    const text=clean(root?.innerText||root?.textContent||'',18000);
    const html=clean(root?.innerHTML||'',26000);
    const lower=low(text+' '+html);
    const qualities=['EXCEPCIONAL','EXCELENTE','ÓTIMO','OTIMO','MUITO BOM','BOM','REGULAR','RUIM'];
    const quality=qualities.find(q=>lower.includes(low(q)))||'';
    const ivm=text.match(/([0-9]{1,3})\\s*[/]\\s*186/);
    const levelm=text.match(/(?:Nível|Nivel|Lv[.]?|Level)\\s*[: -]?\\s*([0-9]{1,3})/i);
    const headings=[...(root.querySelectorAll?[...root.querySelectorAll('h1,h2,h3,h4,[role="heading"]')]:[])].filter(visible).map(txt).filter(Boolean);
    const generic=['detalhes','golpes','equipe','box','pokémon','pokemon','informações','informacoes','vender','mercado'];
    let species=headings.find(h=>!generic.includes(low(h))&&h.length<60)||'';
    if(!species){const m=text.match(/^([A-Za-zÀ-ÿ0-9♀♂ .'-]{2,40})\\s+(?:Lv[.]?|Nível|Nivel|Level)\\s*[0-9]+/i);if(m)species=clean(m[1],60);}
    const stars=parseStars(root,text);
    let rarity='';
    const rm=text.match(/(?:Raridade|Rarity)\\s*[: -]?\\s*([A-Za-zÀ-ÿ ]{3,30})/i); if(rm)rarity=clean(rm[1],30);
    const shiny=lower.includes('shiny')||lower.includes('is-shiny')||lower.includes('pokemon-shiny');
    const locked=lower.includes('destravar')||lower.includes('locked')||lower.includes('bloqueado');
    const favorite=lower.includes('favorito')||lower.includes('favorite')||lower.includes('favourite');
    const event=lower.includes('evento')||lower.includes('event pokemon')||lower.includes('event-pokemon')||lower.includes('special-event');
    return {id:semanticId(root),species,quality,rarity,iv:ivm?parseInt(ivm[1],10):null,level:levelm?parseInt(levelm[1],10):null,starsKnown:stars.known,stars:stars.value,shiny,locked,favorite,event,inTeam:false};
  }
  function sameCritical(a,b){return a.id===b.id&&a.species===b.species&&a.iv===b.iv&&a.starsKnown===b.starsKnown&&a.stars===b.stars&&a.shiny===b.shiny&&a.locked===b.locked&&a.favorite===b.favorite&&a.event===b.event;}
  function norm(s){return low(s).normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');}

  async function openSection(section,sub){
    let b=exact(section)||contains(section);
    if(!b){const menu=contains('Abrir menu')||exact('Menu');if(menu){menu.click();await sleep(450);b=exact(section)||contains(section);}}
    if(section==='Loja'&&!b)b=contains('loja')||contains('mercado');
    if(!b)return false;
    b.click();await sleep(750);
    if(sub){const s=exact(sub)||contains(sub);if(s){s.click();await sleep(650);}else return false;}
    return true;
  }

  function idsIn(root){
    const out=new Set(); if(!root)return out;
    const nodes=[root,...(root.querySelectorAll?[...root.querySelectorAll('*')].slice(0,1000):[])];
    for(const n of nodes){const id=semanticId(n);if(id)out.add(id);} return out;
  }
  function findLabelNode(re){return [...document.querySelectorAll('h1,h2,h3,h4,[role="heading"],section,div,span')].filter(visible).find(e=>re.test(clean(e.innerText||e.textContent||'',80)));}
  function narrowContainer(label){
    if(!label)return null; let cur=label;
    for(let i=0;i<6&&cur;i++,cur=cur.parentElement){const t=clean(cur.innerText||'',9000);const ids=idsIn(cur);if(ids.size>0&&t.length<8500)return cur;}
    return null;
  }
  async function collectTeamProtection(){
    const activeLabel=findLabelNode(/^(equipe|equipe ativa|party)$/i);
    const savedLabel=findLabelNode(/^(times salvos|times|equipes salvas|saved teams)$/i);
    const active=narrowContainer(activeLabel), saved=narrowContainer(savedLabel);
    const ids=new Set(); if(active)for(const id of idsIn(active))ids.add(id); if(saved)for(const id of idsIn(saved))ids.add(id);
    const complete=!!active && !!saved && ids.size>0;
    return {complete,ids};
  }

  function pageFingerprint(){const d=buttons().filter(e=>low(txt(e))==='detalhes');return d.slice(0,15).map(e=>clean(e.parentElement?.innerText||'',180)).join('|')+'#'+d.length;}
  async function nextPage(seen){const n=buttons().find(e=>['próxima','proxima','próximo','proximo','next','›','»','>'].includes(low(txt(e)))&&!e.disabled&&e.getAttribute('aria-disabled')!=='true');if(!n)return false;const before=pageFingerprint();n.click();await sleep(850);const after=pageFingerprint();if(!after||after===before||seen.has(after))return false;seen.add(after);return true;}

  async function scanAll(){
    if(!(await openSection('Box','')))throw new Error('Box não encontrada');
    const team=await collectTeamProtection();
    IdleSell.pushStatus(team.complete?'Times protegidos identificados. Auditando Box…':'Não consegui confirmar todos os times: venda real ficará BLOQUEADA.');
    const arr=[],processed=new Set(),seen=new Set();let pages=0;
    while(pages++<50&&!window.IdleSafeSell.abort){
      const fp=pageFingerprint();if(fp)seen.add(fp);
      let guard=0;
      while(guard++<120&&!window.IdleSafeSell.abort){
        const details=buttons().filter(e=>low(txt(e))==='detalhes').filter(e=>!processed.has(clean(e.parentElement?.innerText||e.outerHTML,500)));
        if(!details.length)break;
        const btn=details[0],sig=clean(btn.parentElement?.innerText||btn.outerHTML,500);processed.add(sig);try{btn.scrollIntoView({block:'center'});}catch(e){}
        btn.click();await sleep(650);let root=modalRoot();const a=parsePokemon(root);await sleep(350);root=modalRoot();const b=parsePokemon(root);const consistent=sameCritical(a,b);b.inTeam=!!(b.id&&team.ids.has(b.id));arr.push({pokemon:b,consistent});closeTop();await sleep(420);
      }
      if(!(await nextPage(seen)))break;
    }
    return {arr,teamComplete:team.complete,teamIds:[...team.ids]};
  }

  function rankAll(scan){
    const groups=new Map();for(const x of scan.arr){const k=norm(x.pokemon.species||'');if(!groups.has(k))groups.set(k,[]);groups.get(k).push(x);}
    for(const g of groups.values())g.sort((x,y)=>((y.pokemon.stars||-1)-(x.pokemon.stars||-1))||((y.pokemon.iv||-1)-(x.pokemon.iv||-1))||((y.pokemon.level||-1)-(x.pokemon.level||-1)));
    const out=[];
    for(const g of groups.values())for(let i=0;i<g.length;i++){const x=g[i];x.speciesCount=g.length;x.rank=i+1;let decision={action:'PROTECT',hardReasons:['falha de avaliação']};try{decision=JSON.parse(IdleSell.evaluateSale(JSON.stringify(x.pokemon),x.consistent,scan.teamComplete,x.speciesCount,x.rank));}catch(e){}out.push({pokemon:x.pokemon,consistent:x.consistent,speciesCount:x.speciesCount,rank:x.rank,decision});}
    return out;
  }

  async function auditBox(){
    if(window.IdleSafeSell.busy)return;window.IdleSafeSell.busy=true;window.IdleSafeSell.abort=false;IdleSell.beginAudit();
    try{const scan=await scanAll();const ranked=rankAll(scan);for(const r of ranked)IdleSell.onAuditItem(JSON.stringify(r));IdleSell.onAuditComplete(JSON.stringify({total:ranked.length,teamScanComplete:scan.teamComplete,teamProtected:scan.teamIds.length}));}
    catch(e){IdleSell.pushStatus('Auditoria interrompida: '+String(e).replace('Error: ',''));IdleSell.pushEvent('sale_audit_error',JSON.stringify({message:String(e)}));}
    finally{window.IdleSafeSell.busy=false;}
  }

  function findBySemanticId(id){
    if(!id)return null;const k=id.indexOf(':');if(k<1)return null;const a=id.slice(0,k),v=id.slice(k+1);
    if(a==='href')return [...document.querySelectorAll('a[href]')].find(e=>(e.getAttribute('href')||'').includes(v))||null;
    try{return document.querySelector('['+a+'="'+CSS.escape(v)+'"]');}catch(e){return null;}
  }
  function clickCandidateNode(node){if(!node)return false;const card=node.closest?.('button,[role="button"],a,.card,[data-card]')||node;try{card.scrollIntoView({block:'center'});card.click();return true;}catch(e){return false;}}
  function setNativeValue(el,value){try{const proto=Object.getPrototypeOf(el),desc=Object.getOwnPropertyDescriptor(proto,'value');if(desc&&desc.set)desc.set.call(el,String(value));else el.value=String(value);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return true;}catch(e){return false;}}
  function fillSaleForm(price,currency){
    const inputs=[...document.querySelectorAll('input')].filter(visible);
    const priceInput=inputs.find(e=>/pre[cç]o|price|valor/i.test((e.name||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')));
    if(!priceInput||!setNativeValue(priceInput,price))return false;
    const sels=[...document.querySelectorAll('select')].filter(visible);for(const s of sels){const opt=[...s.options].find(o=>low(o.textContent)===low(currency));if(opt){s.value=opt.value;s.dispatchEvent(new Event('change',{bubbles:true}));return true;}}
    const c=exact(currency)||contains(currency);if(c){c.click();return true;}
    return true; // some UIs have a fixed currency; price field is mandatory, currency control is optional.
  }

  async function freshTeamCheck(candidate){
    if(!(await openSection('Box','')))return {ok:false,reason:'Box não abriu'};
    const team=await collectTeamProtection();if(!team.complete)return {ok:false,reason:'times não confirmados'};
    if(candidate.pokemon.id&&team.ids.has(candidate.pokemon.id))return {ok:false,reason:'Pokémon entrou em um time'};
    return {ok:true,team};
  }

  async function sellOne(candidate,price,currency){
    const tc=await freshTeamCheck(candidate);if(!tc.ok){IdleSell.pushStatus('VENDA BLOQUEADA: '+tc.reason);return false;}
    if(!(await openSection('Loja','')))return false;
    const sellTab=exact('Vender')||contains('Vender');if(!sellTab)return false;sellTab.click();await sleep(700);
    const creatures=exact('Criaturas')||contains('Criaturas')||exact('Pokémon')||contains('Pokémon');if(creatures){creatures.click();await sleep(650);}
    const node=findBySemanticId(candidate.pokemon.id);if(!node){IdleSell.pushStatus('Venda cancelada: ID exato do candidato não apareceu no vendedor.');return false;}
    if(!clickCandidateNode(node))return false;await sleep(650);
    let root=modalRoot(),p1=parsePokemon(root);await sleep(350);root=modalRoot();let p2=parsePokemon(root);p2.inTeam=tc.team.ids.has(p2.id);
    const consistent=sameCritical(p1,p2)&&p2.id===candidate.pokemon.id;
    if(!IdleSell.canSell(JSON.stringify(p2),consistent,tc.team.complete,candidate.speciesCount,candidate.rank)){IdleSell.pushStatus('Venda bloqueada na revalidação do Pokémon.');closeTop();return false;}
    if(!fillSaleForm(price,currency)){IdleSell.pushStatus('Venda cancelada: campo de preço não reconhecido.');closeTop();return false;}
    const submit=exact('Anunciar',root)||exact('Vender',root)||contains('Anunciar',root);if(!submit){IdleSell.pushStatus('Venda cancelada: botão de anúncio não reconhecido.');closeTop();return false;}
    submit.click();await sleep(650);
    const confirmRoot=modalRoot(),ct=low(confirmRoot.innerText||confirmRoot.textContent||'');
    if(!ct.includes('vender')&&!ct.includes('anunciar')&&!ct.includes('confirm')){IdleSell.pushStatus('Venda cancelada: confirmação inesperada.');closeTop();return false;}
    const finalTeam=await freshTeamCheck(candidate);if(!finalTeam.ok){IdleSell.pushStatus('Venda cancelada na última checagem de time.');closeTop();return false;}
    // reopen seller candidate after team check may have changed the screen; fail closed rather than guessing.
    if(!(await openSection('Loja',''))){IdleSell.pushStatus('Venda cancelada: não foi possível voltar ao vendedor com segurança.');return false;}
    const st=exact('Vender')||contains('Vender');if(!st)return false;st.click();await sleep(650);const cr=exact('Criaturas')||contains('Criaturas')||exact('Pokémon');if(cr){cr.click();await sleep(500);}const n2=findBySemanticId(candidate.pokemon.id);if(!n2||!clickCandidateNode(n2))return false;await sleep(550);root=modalRoot();p1=parsePokemon(root);await sleep(300);p2=parsePokemon(modalRoot());p2.inTeam=finalTeam.team.ids.has(p2.id);
    if(!IdleSell.canSell(JSON.stringify(p2),sameCritical(p1,p2)&&p2.id===candidate.pokemon.id,finalTeam.team.complete,candidate.speciesCount,candidate.rank))return false;
    if(!fillSaleForm(price,currency))return false;const s2=exact('Anunciar',root)||exact('Vender',root)||contains('Anunciar',root);if(!s2)return false;s2.click();await sleep(600);const d=modalRoot();const confirm=exact('Confirmar',d)||exact('Anunciar',d)||exact('Vender',d)||exact('Sim',d);if(!confirm)return false;
    if(!IdleSell.canSell(JSON.stringify(p2),true,finalTeam.team.complete,candidate.speciesCount,candidate.rank))return false;
    confirm.click();await sleep(900);IdleSell.onSold(JSON.stringify(p2));return true;
  }

  async function executeSales(json,price,currency){
    if(window.IdleSafeSell.busy)return;window.IdleSafeSell.busy=true;window.IdleSafeSell.abort=false;
    try{const list=JSON.parse(json);let done=0;for(const c of list){if(window.IdleSafeSell.abort||IdleSell.isEmergencyStop())break;if(await sellOne(c,price,currency))done++;else IdleSell.pushEvent('sale_skipped',JSON.stringify({id:c.pokemon?.id||'',species:c.pokemon?.species||''}));await sleep(600);}IdleSell.pushStatus('Ciclo encerrado · '+done+' venda(s) confirmada(s).');}
    catch(e){IdleSell.pushStatus('Venda interrompida com segurança: '+String(e).replace('Error: ',''));}
    finally{window.IdleSafeSell.busy=false;}
  }

  window.IdleSafeSell={busy:false,abort:false,auditBox,executeSales};
  IdleSell.pushStatus('Venda Segura v2.1 ativa.');
})();
""";
        webView.evaluateJavascript(js, null);
    }

    private boolean isIdleDex(String url) {
        try { Uri u = Uri.parse(url); String h=u.getHost(); return h!=null&&(h.equals("idledex.com")||h.endsWith(".idledex.com")); }
        catch(Exception e){ return false; }
    }

    private void setStatus(String s) { runOnUiThread(() -> status.setText(s)); }
    private TextView label(String text,int color,int size,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView badge(String text,int color){TextView t=label(text,Color.WHITE,11,true);t.setPadding(dp(9),dp(4),dp(9),dp(4));t.setGravity(Gravity.CENTER);t.setBackground(rounded(color,99));return t;}
    private Button smallButton(String text,int color){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(11);b.setBackground(rounded(color,10));return b;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private View space(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return v;}
    private CheckBox check(String text,boolean v){CheckBox c=new CheckBox(this);c.setText(text);c.setChecked(v);return c;}
    private EditText field(String hint,String value,boolean numeric){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int parseInt(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}
    private long parseLong(String s,long f){try{return Long.parseLong(s.trim());}catch(Exception e){return f;}}
    private int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}

    @Override protected void onDestroy(){ if(webView!=null)webView.removeJavascriptInterface("IdleSell"); super.onDestroy(); }

    public class SellBridge {
        @JavascriptInterface public void beginAudit(){ runOnUiThread(()->{candidates.clear();approvedById.clear();soldThisCycle=0;refreshHeader();}); }
        @JavascriptInterface public boolean isEmergencyStop(){ return rules.emergencyStop(); }
        @JavascriptInterface public String evaluateSale(String json, boolean consistent, boolean teamScanComplete, int speciesCount, int rank){
            try{return rules.evaluate(new JSONObject(json),consistent,teamScanComplete,speciesCount,rank).toString();}
            catch(Exception e){return "{\"action\":\"PROTECT\",\"hardReasons\":[\"erro de avaliação\"]}";}
        }
        @JavascriptInterface public void onAuditItem(String json){
            try{
                JSONObject item=new JSONObject(json), d=item.optJSONObject("decision"), p=item.optJSONObject("pokemon");
                if(d!=null&&p!=null&&"SELL_CANDIDATE".equals(d.optString("action"))){
                    String id=p.optString("id","");
                    if(!id.isEmpty()) synchronized(candidates){ if(!approvedById.containsKey(id)){ candidates.add(item);approvedById.put(id,item); } }
                }
                refreshHeader();
            }catch(Exception ignored){}
        }
        @JavascriptInterface public void onAuditComplete(String json){
            try{JSONObject o=new JSONObject(json);setStatus("Auditoria concluída · "+o.optInt("total")+" analisados · "+candidates.size()+" candidatos · times protegidos: "+o.optInt("teamProtected"));}
            catch(Exception e){setStatus("Auditoria concluída · "+candidates.size()+" candidatos");}
            refreshHeader();
        }
        @JavascriptInterface public boolean canSell(String json, boolean consistent, boolean teamScanComplete, int speciesCount, int rank){
            try{
                JSONObject pkm=new JSONObject(json);String id=pkm.optString("id","");JSONObject approved=approvedById.get(id);if(approved==null)return false;
                JSONObject original=approved.optJSONObject("pokemon");if(original==null)return false;
                if(!original.optString("species","").equals(pkm.optString("species","")))return false;
                if(original.optInt("iv",-999)!=pkm.optInt("iv",-998))return false;
                if(original.optInt("stars",-999)!=pkm.optInt("stars",-998))return false;
                JSONObject gate=rules.finalGate(pkm,consistent,teamScanComplete,speciesCount,rank,soldThisCycle);
                return gate.optBoolean("authorized",false);
            }catch(Exception e){return false;}
        }
        @JavascriptInterface public void onSold(String json){ soldThisCycle++; setStatus("Venda segura confirmada · "+soldThisCycle+"/"+rules.maxSalesPerCycle()); refreshHeader(); }
        @JavascriptInterface public void pushStatus(String msg){ setStatus(msg); }
        @JavascriptInterface public void pushEvent(String type,String json){ /* reservado para log detalhado */ }
    }
}
