package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
        status.setText("Abra o idleDEX e faça login. O mapa é coletado automaticamente.");
        status.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button export = new Button(this);
        export.setText("Exportar mapa");
        export.setOnClickListener(v -> exportMap());

        bar.addView(status);
        bar.addView(export);

        webView = new WebView(this);
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " IdleDexMapper/0.1");

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new MapperBridge(), "AndroidMapper");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("idledex.com")) {
                    status.setText("Mapeamento ativo. Capturas: " + snapshots.size());
                    injectMapper();
                } else {
                    status.setText("Login externo aberto. Nenhum dado desta página será mapeado.");
                }
            }
        });

        root.addView(bar);
        root.addView(webView);
        setContentView(root);
        webView.loadUrl("https://idledex.com/play");
    }

    private void injectMapper() {
        String js = "(function(){" +
                "if(window.__IDMAPPER_ACTIVE){try{window.__IDMAPPER_SCAN&&window.__IDMAPPER_SCAN();}catch(e){} return;}" +
                "window.__IDMAPPER_ACTIVE=true;" +
                "function clean(s,n){return String(s||'').replace(/\\s+/g,' ').trim().slice(0,n||180);}" +
                "function cls(el){try{return clean(el.className,180);}catch(e){return '';}}" +
                "function scan(){try{" +
                "var nodes=[].slice.call(document.querySelectorAll('*'),0,1800);" +
                "var elements=nodes.map(function(el,i){" +
                "var r=null;try{r=el.getBoundingClientRect();}catch(e){}" +
                "var p=el.parentElement;" +
                "return {i:i,tag:(el.tagName||'').toLowerCase(),id:clean(el.id,120),className:cls(el),role:clean(el.getAttribute&&el.getAttribute('role'),80),name:clean(el.getAttribute&&el.getAttribute('name'),100),type:clean(el.getAttribute&&el.getAttribute('type'),60),placeholder:clean(el.getAttribute&&el.getAttribute('placeholder'),140),ariaLabel:clean(el.getAttribute&&el.getAttribute('aria-label'),140),title:clean(el.getAttribute&&el.getAttribute('title'),140),href:el.href?clean(el.href,240):'',text:clean(el.innerText||el.textContent,180),visible:!!(r&&r.width>0&&r.height>0),rect:r?{x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}:null,parent:p?{tag:(p.tagName||'').toLowerCase(),id:clean(p.id,100),className:cls(p)}:null};" +
                "});" +
                "var data={version:1,ts:new Date().toISOString(),url:location.href,title:document.title,elementCount:nodes.length,elements:elements};" +
                "AndroidMapper.pushSnapshot(JSON.stringify(data));" +
                "}catch(e){AndroidMapper.pushError(String(e));}}" +
                "window.__IDMAPPER_SCAN=scan;var timer=null;" +
                "var obs=new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(scan,900);});" +
                "obs.observe(document.documentElement||document,{subtree:true,childList:true,attributes:true,attributeFilter:['class','id','role','aria-label','href','style']});" +
                "window.addEventListener('hashchange',function(){setTimeout(scan,400);});" +
                "window.addEventListener('popstate',function(){setTimeout(scan,400);});" +
                "try{var ps=history.pushState;history.pushState=function(){var x=ps.apply(this,arguments);setTimeout(scan,400);return x;};" +
                "var rs=history.replaceState;history.replaceState=function(){var x=rs.apply(this,arguments);setTimeout(scan,400);return x;};}catch(e){}" +
                "setTimeout(scan,600);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void exportMap() {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "IdleDex Mapper");
            root.put("version", "0.1.0");
            root.put("note", "Read-only UI structure map. No passwords, cookies, localStorage or input values are exported.");
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

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "idledex-ui-map.json");
        startActivityForResult(intent, CREATE_MAP_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_MAP_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(exportPayload.getBytes(StandardCharsets.UTF_8));
                    status.setText("Mapa exportado. Envie o arquivo JSON aqui no ChatGPT.");
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
            if (json == null || json.length() > 3_000_000) return;
            synchronized (snapshots) {
                if (snapshots.isEmpty() || !snapshots.get(snapshots.size() - 1).equals(json)) {
                    snapshots.add(json);
                    if (snapshots.size() > 80) snapshots.remove(0);
                }
            }
            runOnUiThread(() -> status.setText("Mapeamento ativo. Capturas: " + snapshots.size()));
        }

        @JavascriptInterface
        public void pushError(String error) {
            runOnUiThread(() -> status.setText("Mapeador: " + error));
        }
    }
}
