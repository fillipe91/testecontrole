package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SafeSellActivity extends Activity {
    private static final int BG=Color.rgb(13,17,23), PANEL=Color.rgb(24,30,38), PANEL2=Color.rgb(31,39,49);
    private static final int TEXT=Color.rgb(238,244,250), MUTED=Color.rgb(166,180,195), ACCENT=Color.rgb(68,153,255);
    private static final int SAFE=Color.rgb(55,196,120), WARN=Color.rgb(255,183,77), DANGER=Color.rgb(255,93,93);

    private SaleSafetyRules rules;
    private WebView webView;
    private TextView status, modeBadge, countText;
    private boolean pageReady=false;
    private int soldThisCycle=0;
    private final List<JSONObject> candidates=new ArrayList<>();
    private final Map<String,JSONObject> approvedById=new HashMap<>();

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        rules=new SaleSafetyRules(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.addView(buildHeader()); root.addView(buildProtectionBanner()); root.addView(buildActions());

        webView=new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        webView.setBackgroundColor(BG);
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(false); s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString()+" IdleDexSafeSell/2.1");
        CookieManager.getInstance().setAcceptCookie(true);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.addJavascriptInterface(new SellBridge(),"IdleSell");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                super.onPageFinished(view,url);
                if(isIdleDex(url)){pageReady=true;setStatus("IdleDex conectado · Venda Segura pronta");inject();}
                else{pageReady=false;setStatus("Login externo · faça o login normalmente");}
            }
        });
        root.addView(webView); setContentView(root); webView.loadUrl("https://idledex.com/play"); refreshHeader();
    }

    private View buildHeader(){
        LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(dp(14),dp(9),dp(14),dp(8));wrap.setBackgroundColor(PANEL);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Venda Segura",TEXT,20,true);title.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        Button back=small("Início",PANEL2);back.setOnClickListener(v->finish());
        Button stop=small("PARAR",DANGER);stop.setOnClickListener(v->emergencyStop());
        top.addView(title);top.addView(back);top.addView(space(8));top.addView(stop);
        LinearLayout info=new LinearLayout(this);info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(0,dp(7),0,0);
        modeBadge=badge("SIMULAÇÃO",WARN);countText=label("0 candidatos",MUTED,12,false);countText.setPadding(dp(10),0,0,0);
        status=label("Carregando…",MUTED,12,false);status.setPadding(dp(10),0,0,0);status.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        info.addView(modeBadge);info.addView(countText);info.addView(status);wrap.addView(top);wrap.addView(info);return wrap;
    }

    private View buildProtectionBanner(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));box.setBackgroundColor(Color.rgb(20,45,35));
        box.addView(label("TRAVAS PERMANENTES",Color.rgb(130,240,180),12,true));
        TextView t=label("Times ativos e salvos · Shiny · Lendário/Mítico/Ultra Beast · 4★/5★ · Evento · Travado/Favorito · IV alto · únicos · melhores duplicatas · dados incertos",TEXT,12,false);t.setPadding(0,dp(3),0,0);box.addView(t);return box;
    }

    private View buildActions(){
        LinearLayout row=new LinearLayout(this);row.setPadding(dp(8),dp(7),dp(8),dp(7));row.setBackgroundColor(PANEL2);
        Button audit=small("Auditar Box",ACCENT), sell=small("Vender",SAFE), cfg=small("Regras",PANEL2), arm=small("Armar",WARN);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1f);lp.setMargins(dp(3),0,dp(3),0);
        audit.setLayoutParams(lp);sell.setLayoutParams(lp);cfg.setLayoutParams(lp);arm.setLayoutParams(lp);
        audit.setOnClickListener(v->startAudit());sell.setOnClickListener(v->executeSales());cfg.setOnClickListener(v->showSettings());arm.setOnClickListener(v->showArmDialog());
        row.addView(audit);row.addView(sell);row.addView(cfg);row.addView(arm);return row;
    }

    private void startAudit(){
        if(!pageReady){setStatus("Faça login no IdleDex primeiro.");return;}
        rules.setEmergencyStop(false); synchronized(candidates){candidates.clear();approvedById.clear();} soldThisCycle=0;refreshHeader();
        setStatus("Auditando Box e conferindo todos os times…");
        webView.evaluateJavascript("window.IdleSafeSell && window.IdleSafeSell.auditBox();",null);
    }

    private void executeSales(){
        if(!pageReady){setStatus("Faça login no IdleDex primeiro.");return;}
        if(candidates.isEmpty()){Toast.makeText(this,"Audite a Box primeiro.",Toast.LENGTH_LONG).show();return;}
        if(rules.simulation()){Toast.makeText(this,"Modo simulação ligado. Nenhuma venda será executada.",Toast.LENGTH_LONG).show();return;}
        if(!rules.isArmed()){Toast.makeText(this,"Arme as vendas por 10 minutos antes de executar.",Toast.LENGTH_LONG).show();return;}
        JSONArray arr=new JSONArray(); synchronized(candidates){for(JSONObject c:candidates)arr.put(c);}
        String js="window.IdleSafeSell && window.IdleSafeSell.executeSales("+JSONObject.quote(arr.toString())+","+rules.salePrice()+","+JSONObject.quote(rules.currency())+");";
        setStatus("Revalidando equipes e candidatos antes da venda…"); webView.evaluateJavascript(js,null);
    }

    private void emergencyStop(){
        rules.setEmergencyStop(true);rules.disarm();if(webView!=null)webView.evaluateJavascript("window.IdleSafeSell && (window.IdleSafeSell.abort=true);",null);
        setStatus("PARADA DE EMERGÊNCIA · vendas bloqueadas");refreshHeader();
    }

    private void showArmDialog(){
        if(rules.simulation()||!rules.autoSell()){Toast.makeText(this,"Em Regras, desligue Simulação e ative a venda automática.",Toast.LENGTH_LONG).show();return;}
        if(rules.salePrice()<=0){Toast.makeText(this,"Configure um preço maior que zero.",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Autorizar vendas por 10 minutos?")
                .setMessage("As proteções permanentes continuam obrigatórias. Qualquer leitura incompleta, diferença de ID ou falha ao confirmar seus times cancela a venda.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Autorizar",(d,w)->{rules.setEmergencyStop(false);rules.armForMinutes(10);refreshHeader();setStatus("Vendas armadas por 10 min · conferência final obrigatória");}).show();
    }

    private void showSettings(){
        ScrollView sc=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(6),dp(22),dp(12));
        TextView hard=label("Não podem ser desligadas: time ativo e todos os times salvos; Shiny; Lendário/Mítico/Ultra Beast; 4★/5★; evento; travado/favorito; dados/ID incertos; único exemplar; melhores duplicatas.",Color.DKGRAY,13,true);hard.setPadding(0,0,0,dp(8));
        CheckBox simulation=check("Modo simulação",rules.simulation()), autoSell=check("Permitir venda automática quando armado",rules.autoSell());
        EditText minIv=field("IV mínimo protegido",String.valueOf(rules.minIvProtect()),true);
        EditText best=field("Manter melhores de cada espécie",String.valueOf(rules.keepBestPerSpecies()),true);
        EditText max=field("Máximo de vendas por ciclo",String.valueOf(rules.maxSalesPerCycle()),true);
        EditText price=field("Preço por Pokémon",String.valueOf(rules.salePrice()),true);
        EditText species=field("Espécies extras protegidas",rules.protectedSpeciesCsv(),false);
        Spinner currency=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Silver","Gold"});currency.setAdapter(ad);currency.setSelection("Gold".equalsIgnoreCase(rules.currency())?1:0);
        box.addView(hard);box.addView(simulation);box.addView(autoSell);box.addView(minIv);box.addView(best);box.addView(max);box.addView(price);box.addView(label("Moeda",Color.DKGRAY,12,true));box.addView(currency);box.addView(species);sc.addView(box);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Regras da Venda Segura").setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int iv=clamp(parseInt(minIv.getText().toString(),rules.minIvProtect()),0,186), keep=clamp(parseInt(best.getText().toString(),rules.keepBestPerSpecies()),1,20), maxN=clamp(parseInt(max.getText().toString(),rules.maxSalesPerCycle()),1,20);
            long pr=parseLong(price.getText().toString(),rules.salePrice());rules.save(simulation.isChecked(),autoSell.isChecked(),iv,keep,maxN,pr,String.valueOf(currency.getSelectedItem()),species.getText().toString());refreshHeader();dlg.dismiss();
        }));dlg.show();
    }

    private void refreshHeader(){runOnUiThread(()->{if(rules.emergencyStop()){modeBadge.setText("STOP");modeBadge.setBackground(rounded(DANGER,99));}else if(rules.isArmed()){modeBadge.setText("ARMADO");modeBadge.setBackground(rounded(DANGER,99));}else if(rules.simulation()){modeBadge.setText("SIMULAÇÃO");modeBadge.setBackground(rounded(WARN,99));}else{modeBadge.setText("PROTEGIDO");modeBadge.setBackground(rounded(SAFE,99));}countText.setText(candidates.size()+" candidatos");});}

    private void inject(){
        String js="""
(() => {
 if(location.hostname!=='idledex.com'&&!location.hostname.endsWith('.idledex.com'))return;
 if(window.IdleSafeSell){IdleSell.pushStatus('Venda Segura v2.1 ativa.');return;}
 const sleep=ms=>new Promise(r=>setTimeout(r,ms));
 const clean=(s,n=18000)=>String(s||'').replace(/ +/g,' ').trim().slice(0,n);
 const low=s=>clean(s,2000).toLowerCase();
 const visible=e=>{try{const r=e.getBoundingClientRect(),c=getComputedStyle(e);return r.width>1&&r.height>1&&c.display!=='none'&&c.visibility!=='hidden'&&c.opacity!=='0';}catch(x){return false;}};
 const txt=e=>clean((e&&(e.getAttribute?.('aria-label')||e.getAttribute?.('title')||e.innerText||e.textContent))||'',900);
 const buttons=(root=document)=>[...root.querySelectorAll('button,[role="button"],a[href],input[type="button"],input[type="submit"]')].filter(visible);
 const exact=(name,root=document)=>buttons(root).find(e=>low(txt(e))===low(name));
 const contains=(name,root=document)=>buttons(root).find(e=>low(txt(e)).includes(low(name)));
 const modalRoot=()=>{const x=[...document.querySelectorAll('[role="dialog"],dialog,[aria-modal="true"],.modal,.drawer,.sheet')].filter(visible);return x.length?x[x.length-1]:document.body;};
 const closeTop=()=>{const c=buttons().filter(e=>['fechar','close','×','voltar','cancelar'].includes(low(txt(e)))||low(e.getAttribute?.('aria-label')||'').includes('fechar'));if(!c.length)return false;c[c.length-1].click();return true;};
 function semanticId(root){if(!root)return '';const attrs=['data-pokemon-id','data-creature-id','data-instance-id','data-pokemon-uid','data-uid'];const nodes=[root,...(root.querySelectorAll?[...root.querySelectorAll('*')].slice(0,700):[])];for(const n of nodes)for(const a of attrs){const v=n.getAttribute?.(a);if(v&&String(v).trim())return a+':'+String(v).trim();}return '';}
 function parseStars(root,text){const nodes=[root,...(root.querySelectorAll?[...root.querySelectorAll('[data-stars],[aria-label],[title]')]:[])];for(const n of nodes){const v=n.getAttribute?.('data-stars');if(v!==null&&v!==undefined){const z=parseInt(v,10);if(z>=0&&z<=5)return {known:true,value:z};}}const m=text.match(/([0-5]) *(?:estrelas?|stars?)/i);if(m)return {known:true,value:parseInt(m[1],10)};const seq=(text.match(/★+/g)||[]).map(x=>x.length).filter(x=>x<=5);if(seq.length)return {known:true,value:Math.max(...seq)};return {known:false,value:-1};}
 function parsePokemon(root){
   const text=clean(root?.innerText||root?.textContent||'',18000), html=clean(root?.innerHTML||'',26000), lower=low(text+' '+html);
   const qualities=['EXCEPCIONAL','EXCELENTE','ÓTIMO','OTIMO','MUITO BOM','BOM','REGULAR','RUIM'];const quality=qualities.find(q=>lower.includes(low(q)))||'';
   const ivm=text.match(/([0-9]{1,3}) *[/] *186/);const levelm=text.match(/(?:Nível|Nivel|Lv[.]?|Level) *[: -]? *([0-9]{1,3})/i);
   const heads=[...(root.querySelectorAll?[...root.querySelectorAll('h1,h2,h3,h4,[role="heading"]')]:[])].filter(visible).map(txt).filter(Boolean);const generic=['detalhes','golpes','equipe','box','pokémon','pokemon','informações','informacoes','vender','mercado'];let species=heads.find(h=>!generic.includes(low(h))&&h.length<60)||'';
   if(!species){const m=text.match(/^([A-Za-zÀ-ÿ0-9♀♂ .'-]{2,40}) +(Lv[.]?|Nível|Nivel|Level) *[0-9]+/i);if(m)species=clean(m[1],60);}
   const stars=parseStars(root,text);let rarity='';const rm=text.match(/(?:Raridade|Rarity) *[: -]? *([A-Za-zÀ-ÿ ]{3,30})/i);if(rm)rarity=clean(rm[1],30);
   return {id:semanticId(root),species,quality,rarity,iv:ivm?parseInt(ivm[1],10):null,level:levelm?parseInt(levelm[1],10):null,starsKnown:stars.known,stars:stars.value,shiny:lower.includes('shiny'),locked:lower.includes('destravar')||lower.includes('locked')||lower.includes('bloqueado'),favorite:lower.includes('favorito')||lower.includes('favorite')||lower.includes('favourite'),event:lower.includes('evento')||lower.includes('event-pokemon')||lower.includes('special-event'),inTeam:false};
 }
 function same(a,b){return a.id===b.id&&a.species===b.species&&a.iv===b.iv&&a.starsKnown===b.starsKnown&&a.stars===b.stars&&a.shiny===b.shiny&&a.locked===b.locked&&a.favorite===b.favorite&&a.event===b.event;}
 function norm(s){return low(s).normalize('NFD').replace(/[̀-ͯ]/g,'');}
 async function openSection(section,sub){let b=exact(section)||contains(section);if(!b){const m=contains('Abrir menu')||exact('Menu');if(m){m.click();await sleep(450);b=exact(section)||contains(section);}}if(section==='Loja'&&!b)b=contains('loja')||contains('mercado');if(!b)return false;b.click();await sleep(750);if(sub){const x=exact(sub)||contains(sub);if(!x)return false;x.click();await sleep(650);}return true;}
 function idsIn(root){const out=new Set();if(!root)return out;const nodes=[root,...(root.querySelectorAll?[...root.querySelectorAll('*')].slice(0,1200):[])];for(const n of nodes){const id=semanticId(n);if(id)out.add(id);}return out;}
 function labelNode(re){return [...document.querySelectorAll('h1,h2,h3,h4,[role="heading"],div,span')].filter(visible).find(e=>re.test(clean(e.innerText||e.textContent||'',100)));}
 function narrow(label){if(!label)return null;let cur=label;for(let i=0;i<7&&cur;i++,cur=cur.parentElement){const ids=idsIn(cur),t=clean(cur.innerText||'',10000);if(ids.size>0&&t.length<9000)return cur;}return null;}
 function collectTeams(){const active=narrow(labelNode(/^(equipe|equipe ativa|party)$/i)),saved=narrow(labelNode(/^(times salvos|equipes salvas|saved teams)$/i));const ids=new Set();if(active)for(const id of idsIn(active))ids.add(id);if(saved)for(const id of idsIn(saved))ids.add(id);return {complete:!!active&&!!saved&&ids.size>0,ids};}
 function fingerprint(){const d=buttons().filter(e=>low(txt(e))==='detalhes');return d.slice(0,12).map(e=>clean(e.parentElement?.innerText||'',180)).join('|')+'#'+d.length;}
 async function nextPage(seen){const n=buttons().find(e=>['próxima','proxima','próximo','proximo','next','›','»','>'].includes(low(txt(e)))&&!e.disabled&&e.getAttribute('aria-disabled')!=='true');if(!n)return false;const before=fingerprint();n.click();await sleep(850);const after=fingerprint();if(!after||after===before||seen.has(after))return false;seen.add(after);return true;}
 async function scanBox(){if(!(await openSection('Box','')))throw new Error('Box não encontrada');const team=collectTeams();IdleSell.pushStatus(team.complete?'Times ativos e salvos protegidos. Auditando Box…':'Não consegui confirmar todos os times: venda real ficará bloqueada.');const arr=[],processed=new Set(),seen=new Set();let pages=0;while(pages++<50&&!window.IdleSafeSell.abort){const fp=fingerprint();if(fp)seen.add(fp);let guard=0;while(guard++<120&&!window.IdleSafeSell.abort){const ds=buttons().filter(e=>low(txt(e))==='detalhes').filter(e=>!processed.has(clean(e.parentElement?.innerText||e.outerHTML,500)));if(!ds.length)break;const btn=ds[0],sig=clean(btn.parentElement?.innerText||btn.outerHTML,500);processed.add(sig);try{btn.scrollIntoView({block:'center'});}catch(x){}btn.click();await sleep(650);let root=modalRoot();const a=parsePokemon(root);await sleep(350);root=modalRoot();const b=parsePokemon(root);b.inTeam=!!(b.id&&team.ids.has(b.id));arr.push({pokemon:b,consistent:same(a,b)});closeTop();await sleep(420);}if(!(await nextPage(seen)))break;}return {arr,team};}
 function evaluateAll(scan){const groups=new Map();for(const x of scan.arr){const k=norm(x.pokemon.species||'');if(!groups.has(k))groups.set(k,[]);groups.get(k).push(x);}const out=[];for(const g of groups.values()){g.sort((x,y)=>((y.pokemon.stars||-1)-(x.pokemon.stars||-1))||((y.pokemon.iv||-1)-(x.pokemon.iv||-1))||((y.pokemon.level||-1)-(x.pokemon.level||-1)));for(let i=0;i<g.length;i++){const x=g[i],count=g.length,rank=i+1;let d={action:'PROTECT'};try{d=JSON.parse(IdleSell.evaluateSale(JSON.stringify(x.pokemon),x.consistent,scan.team.complete,count,rank));}catch(e){}out.push({pokemon:x.pokemon,consistent:x.consistent,speciesCount:count,rank,decision:d});}}return out;}
 async function auditBox(){if(window.IdleSafeSell.busy)return;window.IdleSafeSell.busy=true;window.IdleSafeSell.abort=false;IdleSell.beginAudit();try{const scan=await scanBox(),list=evaluateAll(scan);for(const x of list)IdleSell.onAuditItem(JSON.stringify(x));IdleSell.onAuditComplete(JSON.stringify({total:list.length,teamScanComplete:scan.team.complete,teamProtected:scan.team.ids.size}));}catch(e){IdleSell.pushStatus('Auditoria interrompida com segurança: '+String(e).replace('Error: ',''));}finally{window.IdleSafeSell.busy=false;}}
 function findById(id){if(!id)return null;const k=id.indexOf(':');if(k<1)return null;const attr=id.slice(0,k),value=id.slice(k+1);return [...document.querySelectorAll('['+attr+']')].find(e=>e.getAttribute(attr)===value)||null;}
 function clickCandidate(node){if(!node)return false;const c=node.closest?.('button,[role="button"],a,.card,[data-card]')||node;try{c.scrollIntoView({block:'center'});c.click();return true;}catch(e){return false;}}
 function setValue(el,value){try{el.focus();el.value=String(value);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return true;}catch(e){return false;}}
 function fillForm(price,currency){const inputs=[...document.querySelectorAll('input')].filter(visible);const p=inputs.find(e=>/pre[cç]o|price|valor/i.test((e.name||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')));if(!p||!setValue(p,price))return false;const sels=[...document.querySelectorAll('select')].filter(visible);for(const s of sels){const o=[...s.options].find(x=>low(x.textContent)===low(currency));if(o){s.value=o.value;s.dispatchEvent(new Event('change',{bubbles:true}));break;}}return true;}
 async function freshTeamCheck(candidate){if(!(await openSection('Box','')))return {ok:false};const team=collectTeams();if(!team.complete)return {ok:false};if(candidate.pokemon.id&&team.ids.has(candidate.pokemon.id))return {ok:false};return {ok:true,team};}
 async function sellOne(candidate,price,currency){const pre=await freshTeamCheck(candidate);if(!pre.ok){IdleSell.pushStatus('Venda BLOQUEADA: não consegui garantir proteção dos times.');return false;}if(!(await openSection('Loja','')))return false;const tab=exact('Vender')||contains('Vender');if(!tab)return false;tab.click();await sleep(650);const cr=exact('Criaturas')||contains('Criaturas')||exact('Pokémon')||contains('Pokémon');if(cr){cr.click();await sleep(600);}const node=findById(candidate.pokemon.id);if(!node){IdleSell.pushStatus('Venda cancelada: ID exato não encontrado.');return false;}if(!clickCandidate(node))return false;await sleep(650);let root=modalRoot();const a=parsePokemon(root);await sleep(320);root=modalRoot();const b=parsePokemon(root);b.inTeam=pre.team.ids.has(b.id);const consistent=same(a,b)&&b.id===candidate.pokemon.id;if(!IdleSell.canSell(JSON.stringify(b),consistent,pre.team.complete,candidate.speciesCount,candidate.rank)){IdleSell.pushStatus('Venda bloqueada na conferência final.');closeTop();return false;}if(!fillForm(price,currency)){IdleSell.pushStatus('Venda cancelada: preço não reconhecido.');closeTop();return false;}const submit=exact('Anunciar',root)||exact('Vender',root)||contains('Anunciar',root);if(!submit)return false;submit.click();await sleep(600);const dialog=modalRoot(),dt=low(dialog.innerText||dialog.textContent||'');if(!dt.includes('vender')&&!dt.includes('anunciar')&&!dt.includes('confirm')){IdleSell.pushStatus('Venda cancelada: confirmação inesperada.');closeTop();return false;}if(!IdleSell.canSell(JSON.stringify(b),true,pre.team.complete,candidate.speciesCount,candidate.rank)){closeTop();return false;}const confirm=exact('Confirmar',dialog)||exact('Anunciar',dialog)||exact('Vender',dialog)||exact('Sim',dialog);if(!confirm)return false;confirm.click();await sleep(900);IdleSell.onSold(JSON.stringify(b));return true;}
 async function executeSales(json,price,currency){if(window.IdleSafeSell.busy)return;window.IdleSafeSell.busy=true;window.IdleSafeSell.abort=false;try{const list=JSON.parse(json);let done=0;for(const c of list){if(window.IdleSafeSell.abort||IdleSell.isEmergencyStop())break;if(await sellOne(c,price,currency))done++;await sleep(500);}IdleSell.pushStatus('Ciclo encerrado · '+done+' venda(s) confirmada(s).');}catch(e){IdleSell.pushStatus('Venda interrompida com segurança: '+String(e).replace('Error: ',''));}finally{window.IdleSafeSell.busy=false;}}
 window.IdleSafeSell={busy:false,abort:false,auditBox,executeSales};IdleSell.pushStatus('Venda Segura v2.1 ativa.');
})();
""";
        webView.evaluateJavascript(js,null);
    }

    private boolean isIdleDex(String url){try{Uri u=Uri.parse(url);String h=u.getHost();return h!=null&&(h.equals("idledex.com")||h.endsWith(".idledex.com"));}catch(Exception e){return false;}}
    private void setStatus(String s){runOnUiThread(()->status.setText(s));}
    private TextView label(String t,int c,int z,boolean b){TextView v=new TextView(this);v.setText(t);v.setTextColor(c);v.setTextSize(z);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private TextView badge(String t,int c){TextView v=label(t,Color.WHITE,11,true);v.setPadding(dp(9),dp(4),dp(9),dp(4));v.setGravity(Gravity.CENTER);v.setBackground(rounded(c,99));return v;}
    private Button small(String t,int c){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(11);b.setBackground(rounded(c,10));return b;}
    private GradientDrawable rounded(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private View space(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return v;}
    private CheckBox check(String t,boolean v){CheckBox c=new CheckBox(this);c.setText(t);c.setChecked(v);return c;}
    private EditText field(String h,String v,boolean n){EditText e=new EditText(this);e.setHint(h);e.setText(v);e.setSingleLine(true);if(n)e.setInputType(InputType.TYPE_CLASS_NUMBER);return e;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private int parseInt(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}
    private long parseLong(String s,long f){try{return Long.parseLong(s.trim());}catch(Exception e){return f;}}
    private int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    @Override protected void onDestroy(){if(webView!=null)webView.removeJavascriptInterface("IdleSell");super.onDestroy();}

    public class SellBridge{
        @JavascriptInterface public void beginAudit(){runOnUiThread(()->{synchronized(candidates){candidates.clear();approvedById.clear();}soldThisCycle=0;refreshHeader();});}
        @JavascriptInterface public boolean isEmergencyStop(){return rules.emergencyStop();}
        @JavascriptInterface public String evaluateSale(String json,boolean consistent,boolean teamScanComplete,int speciesCount,int rank){try{return rules.evaluate(new JSONObject(json),consistent,teamScanComplete,speciesCount,rank).toString();}catch(Exception e){return "{\"action\":\"PROTECT\",\"hardReasons\":[\"erro de avaliação\"]}";}}
        @JavascriptInterface public void onAuditItem(String json){try{JSONObject item=new JSONObject(json),d=item.optJSONObject("decision"),p=item.optJSONObject("pokemon");if(d!=null&&p!=null&&"SELL_CANDIDATE".equals(d.optString("action"))){String id=p.optString("id","");if(!id.isEmpty())synchronized(candidates){if(!approvedById.containsKey(id)){candidates.add(item);approvedById.put(id,item);}}}refreshHeader();}catch(Exception ignored){}}
        @JavascriptInterface public void onAuditComplete(String json){try{JSONObject o=new JSONObject(json);setStatus("Auditoria concluída · "+o.optInt("total")+" analisados · "+candidates.size()+" candidatos · IDs protegidos em times: "+o.optInt("teamProtected"));}catch(Exception e){setStatus("Auditoria concluída · "+candidates.size()+" candidatos");}refreshHeader();}
        @JavascriptInterface public boolean canSell(String json,boolean consistent,boolean teamScanComplete,int speciesCount,int rank){try{JSONObject pkm=new JSONObject(json);String id=pkm.optString("id","");JSONObject approved=approvedById.get(id);if(approved==null)return false;JSONObject original=approved.optJSONObject("pokemon");if(original==null)return false;if(!original.optString("species","").equals(pkm.optString("species","")))return false;if(original.optInt("iv",-999)!=pkm.optInt("iv",-998))return false;if(original.optInt("stars",-999)!=pkm.optInt("stars",-998))return false;JSONObject gate=rules.finalGate(pkm,consistent,teamScanComplete,speciesCount,rank,soldThisCycle);return gate.optBoolean("authorized",false);}catch(Exception e){return false;}}
        @JavascriptInterface public void onSold(String json){soldThisCycle++;setStatus("Venda segura confirmada · "+soldThisCycle+"/"+rules.maxSalesPerCycle());refreshHeader();}
        @JavascriptInterface public void pushStatus(String msg){setStatus(msg);}
    }
}
