package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
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
import android.widget.TextView;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int CREATE_LOG_FILE = 7002;
    private static final String PREFS = "idledex_companion";
    private static final String WIKI_URL = "https://wiki.idledex.com/";

    private WebView webView;
    private TextView status;
    private SharedPreferences prefs;
    private final List<JSONObject> logs = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastAutoScanAt = 0L;
    private boolean pageReady = false;

    private final Runnable scheduler = new Runnable() {
        @Override public void run() {
            try {
                if (pageReady && prefs.getBoolean("autoScan", false)) {
                    int minutes = clamp(parseInt(prefs.getString("intervalMinutes", "10"), 10), 2, 120);
                    long due = minutes * 60_000L;
                    if (System.currentTimeMillis() - lastAutoScanAt >= due) {
                        lastAutoScanAt = System.currentTimeMillis();
                        startBoxScan("agendado");
                    }
                }
            } finally {
                handler.postDelayed(this, 30_000L);
            }
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        ensureDefaults();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(8, 6, 8, 6);

        status = new TextView(this);
        status.setText("Abra o idleDEX e faça login normalmente.");
        status.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button scan = new Button(this);
        scan.setText("Box");
        scan.setOnClickListener(v -> startBoxScan("manual"));

        Button rules = new Button(this);
        rules.setText("Regras");
        rules.setOnClickListener(v -> showRulesDialog());

        Button more = new Button(this);
        more.setText("⋮");
        more.setOnClickListener(v -> showMoreDialog());

        bar.addView(status);
        bar.addView(scan);
        bar.addView(rules);
        bar.addView(more);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " IdleDexCompanion/1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new CompanionBridge(), "IdleAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isIdleDex(url)) {
                    pageReady = true;
                    setStatus("idleDEX pronto. Box = analisar agora.");
                    injectCompanion();
                } else {
                    pageReady = false;
                    setStatus("Login externo aberto. Nenhuma credencial é coletada.");
                }
            }
        });

        root.addView(bar);
        root.addView(webView);
        setContentView(root);
        webView.loadUrl("https://idledex.com/play");
        handler.postDelayed(scheduler, 30_000L);
    }

    private void ensureDefaults() {
        if (!prefs.contains("initialized")) {
            prefs.edit()
                    .putBoolean("initialized", true)
                    .putBoolean("keepShiny", true)
                    .putString("minIv", "145")
                    .putString("keepKeywords", "Excepcional,Excelente")
                    .putString("keepSpecies", "Scyther,Scizor")
                    .putBoolean("autoRelease", false)
                    .putBoolean("autoScan", false)
                    .putString("intervalMinutes", "10")
                    .apply();
        }
    }

    private boolean isIdleDex(String url) {
        if (url == null) return false;
        return url.startsWith("https://idledex.com/") || url.equals("https://idledex.com");
    }

    private void setStatus(String text) {
        runOnUiThread(() -> status.setText(text));
    }

    private void startBoxScan(String source) {
        if (!pageReady || webView == null) {
            setStatus("Entre no idleDEX primeiro.");
            return;
        }
        addLog("scan_requested", source, null);
        webView.evaluateJavascript("window.IdleCompanion && window.IdleCompanion.startBoxScan();", null);
    }

    private void injectCompanion() {
        String js = """
(() => {
  if (location.hostname !== 'idledex.com' && !location.hostname.endsWith('.idledex.com')) return;
  if (window.IdleCompanion) { IdleAndroid.pushStatus('Companion ativo.'); return; }

  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const clean = (s,n=7000) => String(s || '').replace(/\\s+/g,' ').trim().slice(0,n);
  const norm = s => clean(s,500).toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');
  const visible = el => {
    try { const r=el.getBoundingClientRect(), cs=getComputedStyle(el); return r.width>1 && r.height>1 && cs.visibility!=='hidden' && cs.display!=='none' && cs.opacity!=='0'; }
    catch(e){ return false; }
  };
  const txt = el => clean((el && (el.getAttribute?.('aria-label') || el.getAttribute?.('title') || el.innerText || el.textContent)) || '',500);
  const interactive = () => [...document.querySelectorAll('button,[role="button"],a[href],input[type="button"],input[type="submit"]')].filter(visible);
  const exact = name => interactive().find(el => norm(txt(el)) === norm(name));
  const matches = rx => interactive().filter(el => rx.test(txt(el)));

  function closeTop() {
    const c = interactive().filter(el => /^(fechar|close|×|voltar)$/i.test(txt(el)) || /fechar|close/i.test(el.getAttribute?.('aria-label')||''));
    if (!c.length) return false;
    try { c[c.length-1].click(); return true; } catch(e) { return false; }
  }

  function modalRoot() {
    const roots=[...document.querySelectorAll('[role="dialog"],dialog,[aria-modal="true"],.modal,.drawer,.sheet')].filter(visible);
    if (roots.length) return roots[roots.length-1];
    const detail = matches(/^(Detalhes|Golpes)$/i);
    if (detail.length) {
      let p=detail[detail.length-1].parentElement;
      for(let i=0;i<5 && p;i++,p=p.parentElement){ if ((p.innerText||'').length>120) return p; }
    }
    return document.body;
  }

  function parsePokemon(root) {
    const text = clean(root?.innerText || root?.textContent || '', 12000);
    const headings=[...root.querySelectorAll?.('h1,h2,h3,h4,[role="heading"]')||[]].filter(visible).map(txt).filter(Boolean);
    const quality=(text.match(/\b(EXCEPCIONAL|EXCELENTE|ÓTIMO|OTIMO|MUITO BOM|BOM|REGULAR|RUIM)\b/i)||[])[1]||'';
    const ivm=text.match(/\bIV(?:s| total)?\b[^0-9]{0,30}(\d{1,3})\s*\/\s*186/i) || text.match(/(\d{1,3})\s*\/\s*186/);
    const nature=(text.match(/Nature\s*[:\-]?\s*([A-Za-zÀ-ÿ]+)/i)||[])[1]||'';
    const level=(text.match(/(?:Nível|Nivel|Lv\.?|Level)\s*[:\-]?\s*(\d{1,3})/i)||[])[1]||'';
    const locked=interactive().some(el => /^Destravar$/i.test(txt(el)));
    const shiny=/\bshiny\b/i.test(text);
    const generic=/^(Detalhes|Golpes|Equipe|Box|Pokémon|Pokemon|Informações|Informacoes)$/i;
    let species=headings.find(h => !generic.test(h) && h.length<60) || '';
    if (!species) {
      const m=text.match(/^([A-Za-zÀ-ÿ0-9♀♂ .'-]{2,40})\s+(?:Lv\.?|Nível|Nivel|Level)\s*\d+/i);
      if (m) species=clean(m[1],60);
    }
    return {species, quality, iv: ivm?parseInt(ivm[1],10):null, nature, level:level?parseInt(level,10):null, shiny, locked, text};
  }

  function getRules(){
    try { return JSON.parse(IdleAndroid.getRules()); }
    catch(e){ return {keepShiny:true,minIv:145,keepKeywords:['Excepcional','Excelente'],keepSpecies:['Scyther','Scizor'],autoRelease:false}; }
  }

  function decision(p) {
    const r=getRules();
    const reasons=[];
    if (r.keepShiny && p.shiny) reasons.push('shiny');
    if (Number.isFinite(p.iv) && p.iv >= Number(r.minIv||999)) reasons.push('IV '+p.iv);
    for(const k of (r.keepKeywords||[])) if(k && norm(p.text).includes(norm(k))) reasons.push('palavra '+k);
    for(const s of (r.keepSpecies||[])) if(s && (norm(p.species)===norm(s) || norm(p.text).includes(norm(s)))) reasons.push('espécie '+s);
    if (p.locked) reasons.push('travado');
    return {keep:reasons.length>0,reasons,rules:r};
  }

  async function releaseCurrent(p) {
    const r=getRules();
    if (!r.autoRelease) return {released:false,reason:'autoRelease desligado'};
    const release=interactive().find(el => /^Soltar$/i.test(txt(el)));
    if (!release) return {released:false,reason:'botão Soltar não encontrado'};
    if (!IdleAndroid.releaseAllowed(JSON.stringify({species:p.species,iv:p.iv,quality:p.quality,shiny:p.shiny}))) return {released:false,reason:'bloqueado pelo Android'};
    IdleAndroid.pushStatus('Soltando '+(p.species||'Pokémon')+'…');
    release.click();
    await sleep(650);
    const confirm=interactive().find(el => /^(Confirmar|Sim|Soltar|Liberar|Release)$/i.test(txt(el)));
    if (confirm) { confirm.click(); await sleep(850); }
    IdleAndroid.pushEvent('released', JSON.stringify(p));
    return {released:true};
  }

  function pageFingerprint() {
    const cards=matches(/^Detalhes$/i);
    return cards.slice(0,10).map(el => clean(el.parentElement?.innerText||'',250)).join('|')+'#'+cards.length;
  }

  async function openBox(){
    let b=exact('Box');
    if (!b) {
      const menu=matches(/Abrir menu|Menu principal|^Menu$/i)[0];
      if (menu) { menu.click(); await sleep(500); b=exact('Box'); }
    }
    if (!b) throw new Error('Botão Box não encontrado. Abra o menu do jogo e tente novamente.');
    b.click(); await sleep(1000);
  }

  async function scanPage(processed) {
    let guard=0;
    while(guard++<80) {
      const buttons=matches(/^Detalhes$/i).filter(el => !processed.has(clean(el.parentElement?.innerText||el.outerHTML,350)));
      if(!buttons.length) break;
      const btn=buttons[0];
      const sig=clean(btn.parentElement?.innerText||btn.outerHTML,350);
      processed.add(sig);
      try { btn.scrollIntoView({block:'center'}); } catch(e){}
      btn.click(); await sleep(700);
      const root=modalRoot();
      const p=parsePokemon(root);
      const d=decision(p);
      IdleAndroid.onPokemon(JSON.stringify({species:p.species,quality:p.quality,iv:p.iv,nature:p.nature,level:p.level,shiny:p.shiny,locked:p.locked,keep:d.keep,reasons:d.reasons}));
      if (!d.keep) await releaseCurrent(p);
      await sleep(250);
      if(!closeTop()) {
        const back=interactive().find(el => /^Voltar$/i.test(txt(el)));
        if(back) back.click();
      }
      await sleep(600);
    }
  }

  async function nextPage(seen) {
    const candidates=interactive().filter(el => /^(Próxima|Proxima|Próximo|Proximo|Next|›|»|>)$/i.test(txt(el)) || /próxima|proxima|next/i.test(el.getAttribute?.('aria-label')||''));
    const next=candidates.find(el => !el.disabled && el.getAttribute('aria-disabled')!=='true');
    if(!next) return false;
    const before=pageFingerprint();
    next.click(); await sleep(850);
    const after=pageFingerprint();
    if(!after || after===before || seen.has(after)) return false;
    seen.add(after); return true;
  }

  async function startBoxScan(){
    if(window.IdleCompanion.busy) { IdleAndroid.pushStatus('Já estou analisando a Box.'); return; }
    window.IdleCompanion.busy=true;
    const processed=new Set(), seenPages=new Set();
    let countBefore=0;
    try {
      IdleAndroid.pushStatus('Abrindo Box…');
      await openBox();
      let pages=0;
      while(pages++<30){
        const fp=pageFingerprint(); if(fp) seenPages.add(fp);
        countBefore=processed.size;
        IdleAndroid.pushStatus('Analisando Box · página '+pages+'…');
        await scanPage(processed);
        if(!(await nextPage(seenPages))) break;
      }
      IdleAndroid.pushEvent('scan_complete', JSON.stringify({processed:processed.size,pages:seenPages.size}));
      IdleAndroid.pushStatus('Box analisada: '+processed.size+' Pokémon vistos.');
    } catch(e) {
      IdleAndroid.pushEvent('scan_error', JSON.stringify({message:String(e)}));
      IdleAndroid.pushStatus('Falha: '+String(e).replace('Error: ',''));
    } finally {
      window.IdleCompanion.busy=false;
    }
  }

  window.IdleCompanion={busy:false,startBoxScan};
  IdleAndroid.pushStatus('IdleDex Companion 1.0 ativo.');
})();
""";
        webView.evaluateJavascript(js, null);
    }

    private void showRulesDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = 32;
        box.setPadding(pad, 8, pad, 0);

        CheckBox keepShiny = new CheckBox(this);
        keepShiny.setText("Sempre manter Shiny");
        keepShiny.setChecked(prefs.getBoolean("keepShiny", true));

        EditText minIv = new EditText(this);
        minIv.setHint("IV mínimo para manter (0–186)");
        minIv.setInputType(InputType.TYPE_CLASS_NUMBER);
        minIv.setText(prefs.getString("minIv", "145"));

        EditText keywords = new EditText(this);
        keywords.setHint("Qualidades/palavras para manter, separadas por vírgula");
        keywords.setText(prefs.getString("keepKeywords", "Excepcional,Excelente"));

        EditText species = new EditText(this);
        species.setHint("Espécies que sempre mantém, separadas por vírgula");
        species.setText(prefs.getString("keepSpecies", "Scyther,Scizor"));

        CheckBox autoRelease = new CheckBox(this);
        autoRelease.setText("Soltar automaticamente o que não passar nas regras");
        autoRelease.setChecked(prefs.getBoolean("autoRelease", false));

        CheckBox autoScan = new CheckBox(this);
        autoScan.setText("Analisar a Box automaticamente");
        autoScan.setChecked(prefs.getBoolean("autoScan", false));

        EditText interval = new EditText(this);
        interval.setHint("Intervalo em minutos (mínimo 2)");
        interval.setInputType(InputType.TYPE_CLASS_NUMBER);
        interval.setText(prefs.getString("intervalMinutes", "10"));

        box.addView(keepShiny);
        box.addView(minIv);
        box.addView(keywords);
        box.addView(species);
        box.addView(autoRelease);
        box.addView(autoScan);
        box.addView(interval);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Regras da Box")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int iv = clamp(parseInt(minIv.getText().toString(), 145), 0, 186);
            int mins = clamp(parseInt(interval.getText().toString(), 10), 2, 120);
            if (autoRelease.isChecked() && !prefs.getBoolean("releaseWarningAccepted", false)) {
                new AlertDialog.Builder(this)
                        .setTitle("Confirmar soltura automática")
                        .setMessage("Quando ativada, o app pode clicar em Soltar para Pokémon que não atendam às regras. Isso altera sua conta e pode ser irreversível. Ative somente se conferiu IV, palavras e espécies que deseja manter.")
                        .setNegativeButton("Não ativar", (d,w) -> autoRelease.setChecked(false))
                        .setPositiveButton("Eu entendi", (d,w) -> {
                            prefs.edit().putBoolean("releaseWarningAccepted", true).apply();
                            saveRules(keepShiny.isChecked(), iv, keywords.getText().toString(), species.getText().toString(), true, autoScan.isChecked(), mins);
                            dlg.dismiss();
                        }).show();
                return;
            }
            saveRules(keepShiny.isChecked(), iv, keywords.getText().toString(), species.getText().toString(), autoRelease.isChecked(), autoScan.isChecked(), mins);
            dlg.dismiss();
        }));
        dlg.show();
    }

    private void saveRules(boolean keepShiny, int minIv, String keywords, String species, boolean autoRelease, boolean autoScan, int mins) {
        prefs.edit()
                .putBoolean("keepShiny", keepShiny)
                .putString("minIv", String.valueOf(minIv))
                .putString("keepKeywords", keywords.trim())
                .putString("keepSpecies", species.trim())
                .putBoolean("autoRelease", autoRelease)
                .putBoolean("autoScan", autoScan)
                .putString("intervalMinutes", String.valueOf(mins))
                .apply();
        addLog("rules_saved", "android", null);
        setStatus("Regras salvas. IV mínimo: " + minIv + (autoRelease ? " · soltura automática ON" : " · somente análise"));
    }

    private void showMoreDialog() {
        String[] items = {"Abrir Wiki oficial", "Exportar log", "Recarregar idleDEX", "Limpar somente os logs do app"};
        new AlertDialog.Builder(this)
                .setTitle("IdleDex Companion")
                .setItems(items, (d, which) -> {
                    if (which == 0) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WIKI_URL)));
                    else if (which == 1) exportLogs();
                    else if (which == 2) webView.reload();
                    else if (which == 3) { synchronized (logs) { logs.clear(); } setStatus("Logs do Companion limpos."); }
                }).show();
    }

    private JSONObject rulesJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("keepShiny", prefs.getBoolean("keepShiny", true));
            o.put("minIv", clamp(parseInt(prefs.getString("minIv", "145"),145),0,186));
            o.put("keepKeywords", csvArray(prefs.getString("keepKeywords", "Excepcional,Excelente")));
            o.put("keepSpecies", csvArray(prefs.getString("keepSpecies", "Scyther,Scizor")));
            o.put("autoRelease", prefs.getBoolean("autoRelease", false));
        } catch (JSONException ignored) {}
        return o;
    }

    private JSONArray csvArray(String csv) {
        JSONArray a = new JSONArray();
        if (csv == null) return a;
        for (String p : csv.split(",")) {
            String v = p.trim();
            if (!v.isEmpty()) a.put(v);
        }
        return a;
    }

    private void addLog(String type, String source, JSONObject data) {
        try {
            JSONObject e = new JSONObject();
            e.put("ts", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));
            e.put("type", type);
            e.put("source", source == null ? "" : source);
            if (data != null) e.put("data", data);
            synchronized (logs) {
                logs.add(e);
                if (logs.size() > 1500) logs.remove(0);
            }
        } catch (JSONException ignored) {}
    }

    private void exportLogs() {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "IdleDex Companion");
            root.put("version", "1.0.0");
            root.put("rules", rulesJson());
            JSONArray a = new JSONArray();
            synchronized (logs) { for (JSONObject e : logs) a.put(e); }
            root.put("events", a);
            byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, "idledex-companion-log.json");
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IdleDexCompanion");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Não foi possível criar o arquivo");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) { if (out != null) out.write(bytes); }
                ContentValues done = new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING, 0); getContentResolver().update(uri, done, null, null);
                Intent share = new Intent(Intent.ACTION_SEND); share.setType("application/json"); share.putExtra(Intent.EXTRA_STREAM, uri); share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Enviar log para…"));
                return;
            }
        } catch (Exception e) { setStatus("Falha ao exportar log: " + e.getMessage()); }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "idledex-companion-log.json");
        startActivityForResult(intent, CREATE_LOG_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_LOG_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                JSONObject root = new JSONObject();
                root.put("app", "IdleDex Companion"); root.put("version", "1.0.0"); root.put("rules", rulesJson());
                JSONArray a = new JSONArray(); synchronized (logs) { for (JSONObject e : logs) a.put(e); } root.put("events", a);
                try (OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) { if (out != null) out.write(root.toString(2).getBytes(StandardCharsets.UTF_8)); }
                setStatus("Log exportado.");
            } catch (Exception e) { setStatus("Falha ao exportar: " + e.getMessage()); }
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(scheduler);
        if (webView != null) webView.removeJavascriptInterface("IdleAndroid");
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    private int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public class CompanionBridge {
        @JavascriptInterface public String getRules() { return rulesJson().toString(); }

        @JavascriptInterface public boolean releaseAllowed(String summary) {
            boolean ok = prefs.getBoolean("autoRelease", false) && prefs.getBoolean("releaseWarningAccepted", false);
            if (ok) {
                try { addLog("release_authorized", "bridge", new JSONObject(summary)); } catch (Exception ignored) {}
            }
            return ok;
        }

        @JavascriptInterface public void pushStatus(String message) { setStatus(message); }

        @JavascriptInterface public void onPokemon(String json) {
            try {
                JSONObject p = new JSONObject(json);
                addLog("pokemon_review", "web", p);
                String name = p.optString("species", "Pokémon");
                String iv = p.isNull("iv") ? "?" : String.valueOf(p.optInt("iv"));
                String action = p.optBoolean("keep", true) ? "manter" : (prefs.getBoolean("autoRelease", false) ? "soltar" : "revisar");
                setStatus(name + " · IV " + iv + " · " + action);
            } catch (JSONException ignored) {}
        }

        @JavascriptInterface public void pushEvent(String type, String json) {
            try { addLog(type, "web", json == null || json.isEmpty() ? null : new JSONObject(json)); }
            catch (Exception e) { addLog(type, "web", null); }
        }
    }
}
