package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int CREATE_MAP_FILE = 7001;
    private WebView webView;
    private TextView status;
    private final List<String> snapshots = new ArrayList<>();
    private String exportPayload = "{}";
    private String lastFingerprint = "";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(12, 8, 12, 8);

        status = new TextView(this);
        status.setText("Faça login no idleDEX. Depois o mapeamento profundo começa sozinho.");
        status.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button mapAll = new Button(this);
        mapAll.setText("Mapear tudo");
        mapAll.setOnClickListener(v -> injectMapper());

        Button export = new Button(this);
        export.setText("Exportar");
        export.setOnClickListener(v -> exportMap());

        bar.addView(status);
        bar.addView(mapAll);
        bar.addView(export);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " IdleDexMapper/0.3");

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new MapperBridge(), "AndroidMapper");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && (url.equals("https://idledex.com") || url.startsWith("https://idledex.com/"))) {
                    status.setText("idleDEX aberto. Varredura automática ativa.");
                    injectMapper();
                } else {
                    status.setText("Login externo aberto. Esta página não é mapeada.");
                }
            }
        });

        root.addView(bar);
        root.addView(webView);
        setContentView(root);
        webView.loadUrl("https://idledex.com/play");
    }

    private void injectMapper() {
        if (webView == null) return;
        String js = """
            (() => {
              if (location.hostname !== 'idledex.com' && !location.hostname.endsWith('.idledex.com')) return;
              if (window.__IDMAPPER_V3) { window.__IDMAPPER_V3.kick(); return; }

              const M = window.__IDMAPPER_V3 = {
                clicked: Object.create(null),
                repeats: Object.create(null),
                clicks: 0,
                idleRounds: 0,
                maxClicks: 140,
                timer: null,
                lastAction: 'start'
              };

              const clean = (s,n=180) => String(s || '').replace(/\\s+/g,' ').trim().slice(0,n);
              const norm = s => clean(s,220).toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\d+/g,'#');
              const visible = el => {
                try { const r=el.getBoundingClientRect(); const cs=getComputedStyle(el); return r.width>1 && r.height>1 && cs.visibility!=='hidden' && cs.display!=='none'; }
                catch(e){ return false; }
              };
              const label = el => clean((el.getAttribute && (el.getAttribute('aria-label') || el.getAttribute('title'))) || el.innerText || el.textContent,220);

              const dangerRx = /(comprar|purchase|\\bbuy\\b|vender|\\bsell\\b|soltar|release|excluir|delete|apagar|descartar|discard|\\busar\\b|\\buse\\b|equipar|\\bequip\\b|forjar|forge|mint|\\btrocar\\b|\\btrade\\b|transfer|enviar|\\bsend\\b|doar|donat|coletar|recolher|\\bclaim\\b|captur|pok[eé]?ball|\\bbola\\b|curar|\\bheal\\b|continuar jornada|assumir esta luta|\\bmanual\\b|auto on|auto off|desativar modo|ativar modo|confirmar|\\bconfirm\\b|pagar|\\bpay\\b)/i;
              const safeRx = /(abrir menu|abrir invent[aá]rio|invent[aá]rio|miss[oõ]es|abrir equipe|\\bparty\\b|passe de batalha|battle pass|pok[eé]dex|\\bdex\\b|marketplace|\\bmarket\\b|mercado|\\bbox\\b|caixa|\\bitens\\b|\\bitems\\b|detalh|\\bdetail|\\binfo\\b|premium|pok[eé]tv|trocar de mapa|\\bmapa\\b|ranking|leaderboard|perfil|profile|cole[cç][aã]o|collection|conquista|achievement|amigos|friends|cl[aã]|guild|n[ií]vel\\s*\\d+|level\\s*\\d+|lv\\s*\\d+)/i;
              const repeatRx = /(abrir menu|abrir invent[aá]rio|miss[oõ]es|abrir equipe|passe de batalha|battle pass|premium|trocar de mapa)/i;

              function elementSig(el) {
                return norm(label(el)) + '|' + norm(el.getAttribute && el.getAttribute('aria-label')) + '|' + clean(el.id,80) + '|' + clean(el.className,120);
              }

              function isSafe(el) {
                if (!visible(el) || el.disabled) return false;
                const s = label(el);
                if (!s || dangerRx.test(s)) return false;
                const href = el.href || (el.getAttribute && el.getAttribute('href')) || '';
                if (href) {
                  try { const u=new URL(href,location.href); if (u.hostname !== location.hostname) return false; }
                  catch(e) { return false; }
                }
                return safeRx.test(s);
              }

              function snapshot(reason) {
                try {
                  const nodes=[].slice.call(document.querySelectorAll('*'),0,2200);
                  const buttons=[].slice.call(document.querySelectorAll('button,[role="button"],a[href]')).filter(visible);
                  const buttonLabels=buttons.map(label).filter(Boolean).map(norm).sort().slice(0,120);
                  const fingerprint=location.pathname+'|'+location.search+'|'+nodes.length+'|'+buttonLabels.join('~');
                  const elements=nodes.map((el,i) => {
                    let r=null; try { r=el.getBoundingClientRect(); } catch(e) {}
                    const p=el.parentElement;
                    const s=label(el);
                    return {
                      i,
                      tag:(el.tagName||'').toLowerCase(),
                      id:clean(el.id,120),
                      className:clean(el.className,180),
                      role:clean(el.getAttribute&&el.getAttribute('role'),80),
                      name:clean(el.getAttribute&&el.getAttribute('name'),100),
                      type:clean(el.getAttribute&&el.getAttribute('type'),60),
                      placeholder:clean(el.getAttribute&&el.getAttribute('placeholder'),140),
                      ariaLabel:clean(el.getAttribute&&el.getAttribute('aria-label'),180),
                      title:clean(el.getAttribute&&el.getAttribute('title'),180),
                      href:el.href?clean(el.href,260):'',
                      text:clean(el.innerText||el.textContent,200),
                      visible:!!(r&&r.width>1&&r.height>1),
                      clickable:el.matches&&el.matches('button,[role="button"],a[href]'),
                      safeToOpen:(el.matches&&el.matches('button,[role="button"],a[href]'))?isSafe(el):false,
                      blockedAction:!!(s&&dangerRx.test(s)),
                      rect:r?{x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}:null,
                      parent:p?{tag:(p.tagName||'').toLowerCase(),id:clean(p.id,100),className:clean(p.className,120)}:null
                    };
                  });
                  AndroidMapper.pushSnapshot(JSON.stringify({
                    version:3,
                    ts:new Date().toISOString(),
                    url:location.href,
                    title:document.title,
                    reason,
                    fingerprint,
                    explorer:{clicks:M.clicks,lastAction:M.lastAction},
                    elementCount:nodes.length,
                    elements
                  }));
                } catch(e) { AndroidMapper.pushError('snapshot: '+String(e)); }
              }

              function pickCandidate() {
                const all=[].slice.call(document.querySelectorAll('button,[role="button"],a[href]')).filter(isSafe);
                all.sort((a,b) => {
                  const A=norm(label(a)), B=norm(label(b));
                  const score=s => /abrir menu/.test(s)?100:/pokedex|marketplace|mercado|box|inventario|missoes|equipe|party/.test(s)?80:/detalh|nivel|level|lv#/.test(s)?60:30;
                  return score(B)-score(A);
                });
                for (const el of all) {
                  const sig=elementSig(el), text=label(el);
                  if (repeatRx.test(text)) {
                    const n=M.repeats[sig]||0;
                    const limit=/abrir menu/i.test(text)?14:5;
                    if (n<limit) return {el,sig,repeat:true};
                  } else if (!M.clicked[sig]) {
                    return {el,sig,repeat:false};
                  }
                }
                return null;
              }

              function closeOne() {
                const closers=[].slice.call(document.querySelectorAll('button,[role="button"]')).filter(visible).filter(el => /^(fechar|close|×)$/i.test(label(el)) || /fechar|close/i.test(el.getAttribute('aria-label')||''));
                if (!closers.length) return false;
                const el=closers[closers.length-1];
                M.lastAction='close:'+label(el);
                try { el.click(); return true; } catch(e) { return false; }
              }

              function tick() {
                clearTimeout(M.timer);
                if (M.clicks>=M.maxClicks) { snapshot('limit-reached'); AndroidMapper.pushStatus('Varredura chegou ao limite seguro. Exporte o mapa.'); return; }
                snapshot('auto');
                const c=pickCandidate();
                if (c) {
                  M.idleRounds=0;
                  M.clicks++;
                  if (c.repeat) M.repeats[c.sig]=(M.repeats[c.sig]||0)+1; else M.clicked[c.sig]=true;
                  M.lastAction='open:'+label(c.el);
                  AndroidMapper.pushStatus('Mapeando: '+label(c.el)+' · '+M.clicks+'/'+M.maxClicks);
                  try { c.el.scrollIntoView({block:'center'}); c.el.click(); } catch(e) { AndroidMapper.pushError('click: '+String(e)); }
                  M.timer=setTimeout(tick,1600);
                  return;
                }
                M.idleRounds++;
                if (closeOne()) { M.timer=setTimeout(tick,1200); return; }
                if (M.idleRounds<4) {
                  try { window.scrollBy(0,Math.max(300,window.innerHeight*0.7)); } catch(e) {}
                  M.timer=setTimeout(tick,1200);
                  return;
                }
                snapshot('complete');
                AndroidMapper.pushStatus('Varredura automática concluída. Exporte o mapa.');
              }

              let mutationTimer=null;
              const obs=new MutationObserver(() => {
                clearTimeout(mutationTimer);
                mutationTimer=setTimeout(() => snapshot('mutation'),700);
              });
              try { obs.observe(document.documentElement||document,{subtree:true,childList:true,attributes:true,attributeFilter:['class','id','role','aria-label','href','style']}); } catch(e) {}
              window.addEventListener('hashchange',() => setTimeout(tick,500));
              window.addEventListener('popstate',() => setTimeout(tick,500));
              M.kick=() => { M.idleRounds=0; clearTimeout(M.timer); M.timer=setTimeout(tick,300); };
              M.kick();
            })();
            """;
        webView.evaluateJavascript(js, null);
    }

    private void preparePayload() {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "IdleDex Mapper");
            root.put("version", "0.3.0");
            root.put("note", "Deep read-only UI map. Navigation is opened automatically, but purchase, sell, release, use, trade, capture, heal and other account-changing actions are only recorded and never activated.");
            JSONArray arr = new JSONArray();
            synchronized (snapshots) {
                for (String s : snapshots) {
                    try { arr.put(new JSONObject(s)); } catch (JSONException ignored) {}
                }
            }
            root.put("snapshots", arr);
            exportPayload = root.toString(2);
        } catch (JSONException e) {
            exportPayload = "{\"error\":\"Could not prepare export\"}";
        }
    }

    private void exportMap() {
        preparePayload();
        byte[] bytes = exportPayload.getBytes(StandardCharsets.UTF_8);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, "idledex-ui-map-v0.3.json");
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IdleDexMapper");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Não foi possível criar o arquivo em Downloads");

                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IllegalStateException("Não foi possível abrir o arquivo para gravação");
                    out.write(bytes);
                    out.flush();
                }

                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);

                long kb = Math.max(1, Math.round(bytes.length / 1024.0));
                status.setText("Mapa salvo em Downloads/IdleDexMapper (" + kb + " KB). Escolha Google Drive para enviar.");

                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("application/json");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Enviar mapa para..."));
                return;
            } catch (Exception e) {
                status.setText("Falha ao salvar em Downloads: " + e.getMessage() + ". Escolha onde salvar.");
            }
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "idledex-ui-map-v0.3.json");
        startActivityForResult(intent, CREATE_MAP_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_MAP_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out != null) {
                    byte[] bytes = exportPayload.getBytes(StandardCharsets.UTF_8);
                    out.write(bytes);
                    out.flush();
                    long kb = Math.max(1, Math.round(bytes.length / 1024.0));
                    status.setText("Mapa exportado com " + kb + " KB.");
                }
            } catch (Exception e) {
                status.setText("Falha ao exportar: " + e.getMessage());
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public class MapperBridge {
        @JavascriptInterface
        public void pushSnapshot(String json) {
            if (json == null || json.length() > 4_000_000) return;
            try {
                JSONObject obj = new JSONObject(json);
                String fp = obj.optString("fingerprint", "");
                synchronized (snapshots) {
                    if (!fp.isEmpty() && fp.equals(lastFingerprint)) return;
                    if (!fp.isEmpty()) lastFingerprint = fp;
                    snapshots.add(json);
                    if (snapshots.size() > 180) snapshots.remove(0);
                }
            } catch (JSONException ignored) { return; }
            runOnUiThread(() -> status.setText("Mapeamento profundo ativo. Telas: " + snapshots.size()));
        }

        @JavascriptInterface
        public void pushStatus(String message) {
            runOnUiThread(() -> status.setText(message));
        }

        @JavascriptInterface
        public void pushError(String error) {
            runOnUiThread(() -> status.setText("Mapeador: " + error));
        }
    }
}
