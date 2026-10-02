package com.fillipe.webmapper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int CREATE_LOG_FILE = 7002;
    private static final int BG = Color.rgb(13, 17, 23);
    private static final int PANEL = Color.rgb(24, 30, 38);
    private static final int PANEL2 = Color.rgb(31, 39, 49);
    private static final int TEXT = Color.rgb(238, 244, 250);
    private static final int MUTED = Color.rgb(166, 180, 195);
    private static final int ACCENT = Color.rgb(68, 153, 255);
    private static final int SAFE = Color.rgb(55, 196, 120);
    private static final int WARN = Color.rgb(255, 183, 77);
    private static final int DANGER = Color.rgb(255, 93, 93);

    private WebView webView;
    private FrameLayout content;
    private ScrollView dashboard;
    private TextView status;
    private TextView safetyBadge;
    private TextView queueBadge;
    private Button stopButton;
    private SafetyRules rules;
    private AppUpdater updater;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<JSONObject> logs = new ArrayList<>();
    private final List<JSONObject> queue = new ArrayList<>();
    private final Set<String> queueKeys = new HashSet<>();
    private boolean pageReady = false;
    private long lastAutoScanAt = 0L;
    private int releasedThisCycle = 0;

    private final Runnable scheduler = new Runnable() {
        @Override public void run() {
            try {
                updateHeader();
                if (pageReady && rules.autoScan() && !rules.emergencyStop()) {
                    long due = rules.intervalMinutes() * 60_000L;
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
        rules = new SafetyRules(this);
        updater = new AppUpdater(this, this::setStatus);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        root.addView(buildHeader());
        root.addView(buildNavigation());

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dashboard = buildDashboard();
        content.addView(dashboard);

        webView = new WebView(this);
        webView.setBackgroundColor(BG);
        webView.setVisibility(View.GONE);
        webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " IdleDexCompanion/2.0");
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new CompanionBridge(), "IdleAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isIdleDex(url)) {
                    pageReady = true;
                    setStatus("IdleDex conectado · Companion protegido ativo");
                    injectCompanion();
                } else {
                    pageReady = false;
                    setStatus("Login externo aberto · credenciais não são coletadas");
                }
            }
        });
        content.addView(webView);
        root.addView(content);
        setContentView(root);

        webView.loadUrl("https://idledex.com/play");
        updateHeader();
        handler.postDelayed(scheduler, 30_000L);
        handler.postDelayed(() -> updater.checkForUpdate(false), 5000L);
    }

    private View buildHeader() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(14), dp(10), dp(14), dp(8));
        wrap.setBackgroundColor(PANEL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("IdleDex Companion");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        stopButton = smallButton("PARAR", DANGER);
        stopButton.setOnClickListener(v -> toggleEmergencyStop());
        Button update = smallButton("Atualizar", ACCENT);
        update.setOnClickListener(v -> updater.checkForUpdate(true));
        top.addView(title);
        top.addView(stopButton);
        top.addView(space(dp(8), 1));
        top.addView(update);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(0, dp(8), 0, 0);
        safetyBadge = badge("SIMULAÇÃO", WARN);
        queueBadge = badge("Fila 0", ACCENT);
        queueBadge.setOnClickListener(v -> showQueue());
        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(12);
        status.setPadding(dp(10), 0, 0, 0);
        status.setText("Pronto");
        status.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(safetyBadge);
        info.addView(space(dp(6),1));
        info.addView(queueBadge);
        info.addView(status);

        wrap.addView(top);
        wrap.addView(info);
        return wrap;
    }

    private View buildNavigation() {
        HorizontalScrollView sc = new HorizontalScrollView(this);
        sc.setHorizontalScrollBarEnabled(false);
        sc.setBackgroundColor(PANEL2);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.addView(navButton("Início", v -> showDashboard()));
        row.addView(navButton("Jogar", v -> showWeb()));
        row.addView(navButton("AUTO", v -> openSection("AUTO", "")));
        row.addView(navButton("Captura", v -> openSection("AUTO", "Captura")));
        row.addView(navButton("Box", v -> openSection("Box", "")));
        row.addView(navButton("Pokédex", v -> openSection("Pokédex", "")));
        row.addView(navButton("Itens", v -> openSection("Inventário", "")));
        row.addView(navButton("Mercado", v -> openSection("Mercado", "")));
        row.addView(navButton("Segurança", v -> showSafetyDialog()));
        sc.addView(row);
        return sc;
    }

    private ScrollView buildDashboard() {
        ScrollView sc = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(30));

        LinearLayout hero = card("Seu painel", "Controle simples, com ações destrutivas bloqueadas por padrão.");
        Button play = primaryButton("Abrir o jogo");
        play.setOnClickListener(v -> showWeb());
        hero.addView(play);
        body.addView(hero);

        LinearLayout preset = card("Perfil pré-configurado", "Escolha um objetivo e o Companion ajusta as regras básicas.");
        TextView currentPreset = label("Atual: " + rules.preset(), TEXT, 15, true);
        currentPreset.setTag("presetLabel");
        preset.addView(currentPreset);
        Button choosePreset = secondaryButton("Escolher perfil");
        choosePreset.setOnClickListener(v -> showPresetDialog());
        preset.addView(choosePreset);
        body.addView(preset);

        LinearLayout boxCard = card("Box inteligente", "Analisa cada Pokémon, faz dupla leitura e coloca casos descartáveis em uma fila segura.");
        Button analyze = primaryButton("Analisar Box agora");
        analyze.setOnClickListener(v -> startBoxScan("manual"));
        Button queueBtn = secondaryButton("Revisar candidatos");
        queueBtn.setOnClickListener(v -> showQueue());
        boxCard.addView(analyze);
        boxCard.addView(queueBtn);
        body.addView(boxCard);

        LinearLayout safeCard = card("Segurança", "Shiny, travados, espécies protegidas, qualidades altas e IV mínimo são preservados. Se faltarem dados, nada é solto.");
        Button safe = secondaryButton("Abrir Central de Segurança");
        safe.setOnClickListener(v -> showSafetyDialog());
        safeCard.addView(safe);
        body.addView(safeCard);

        LinearLayout modules = card("Acesso rápido", "Abra qualquer módulo do IdleDex sem procurar nos menus.");
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(moduleButton("AUTO", "AUTO"));
        r1.addView(moduleButton("Box", "Box"));
        r1.addView(moduleButton("Dex", "Pokédex"));
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(moduleButton("Itens", "Inventário"));
        r2.addView(moduleButton("Mercado", "Mercado"));
        r2.addView(moduleButton("Cura", "Centro de Cura"));
        modules.addView(r1);
        modules.addView(r2);
        body.addView(modules);

        LinearLayout updateCard = card("Atualizações", "Depois desta versão, o botão Atualizar baixa a versão mais nova, confere a integridade e abre a instalação do Android.");
        Button check = secondaryButton("Verificar atualização");
        check.setOnClickListener(v -> updater.checkForUpdate(true));
        updateCard.addView(check);
        body.addView(updateCard);

        sc.addView(body);
        return sc;
    }

    private LinearLayout moduleButton(String text, String section) {
        Button b = secondaryButton(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> openSection(section, ""));
        LinearLayout w = new LinearLayout(this);
        w.setLayoutParams(lp);
        w.addView(b);
        return w;
    }

    private LinearLayout card(String title, String subtitle) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        c.setLayoutParams(lp);
        c.setBackground(rounded(PANEL, 16, Color.rgb(45,55,68)));
        c.addView(label(title, TEXT, 17, true));
        TextView sub = label(subtitle, MUTED, 13, false);
        sub.setPadding(0, dp(5), 0, dp(10));
        c.addView(sub);
        return c;
    }

    private void showPresetDialog() {
        String[] presets = {"Segurança máxima", "Farm forte", "Caçar Shiny", "Personalizado"};
        new AlertDialog.Builder(this)
                .setTitle("Escolha um perfil")
                .setItems(presets, (d, which) -> {
                    if (which == 3) showSafetyDialog();
                    else {
                        rules.applyPreset(presets[which]);
                        addLog("preset", presets[which], null);
                        rebuildDashboard();
                        updateHeader();
                        Toast.makeText(this, "Perfil " + presets[which] + " aplicado em modo seguro.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void showSafetyDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));

        CheckBox simulation = check("Modo simulação (recomendado)", rules.simulation());
        CheckBox autoRelease = check("Permitir soltura automática", rules.autoRelease());
        CheckBox autoScan = check("Analisar Box automaticamente", rules.autoScan());
        CheckBox keepShiny = check("Sempre proteger Shiny", rules.keepShiny());
        CheckBox protectLocked = check("Sempre proteger Pokémon travados", rules.protectLocked());
        CheckBox requireComplete = check("Bloquear ação se faltar qualquer dado", rules.requireCompleteData());

        EditText minIv = field("IV mínimo protegido (0–186)", String.valueOf(rules.minIv()), true);
        EditText qualities = field("Qualidades protegidas", rules.protectedQualitiesCsv(), false);
        EditText species = field("Espécies protegidas", rules.protectedSpeciesCsv(), false);
        EditText interval = field("Intervalo automático em minutos", String.valueOf(rules.intervalMinutes()), true);
        EditText maxRelease = field("Máximo de solturas por ciclo", String.valueOf(rules.maxReleasesPerCycle()), true);

        box.addView(label("Proteções", Color.BLACK, 16, true));
        box.addView(simulation); box.addView(autoRelease); box.addView(autoScan); box.addView(keepShiny);
        box.addView(protectLocked); box.addView(requireComplete);
        box.addView(minIv); box.addView(qualities); box.addView(species); box.addView(interval); box.addView(maxRelease);
        scroll.addView(box);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Central de Segurança")
                .setView(scroll)
                .setNeutralButton("Soltura indisponível", null)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", null)
                .create();
        dlg.setOnShowListener(x -> {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                showArmDialog(dlg);
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int iv = clamp(parseInt(minIv.getText().toString(), rules.minIv()), 0, 186);
                int mins = clamp(parseInt(interval.getText().toString(), rules.intervalMinutes()), 2, 120);
                int max = clamp(parseInt(maxRelease.getText().toString(), rules.maxReleasesPerCycle()), 1, 20);
                rules.save(simulation.isChecked(), autoRelease.isChecked(), autoScan.isChecked(), keepShiny.isChecked(),
                        protectLocked.isChecked(), requireComplete.isChecked(), iv,
                        qualities.getText().toString(), species.getText().toString(), mins, max);
                addLog("rules_saved", rules.preset(), rules.toJson());
                updateHeader(); rebuildDashboard(); dlg.dismiss();
            });
        });
        dlg.show();
    }

    private void showArmDialog(AlertDialog parent) {
        Toast.makeText(this, "Soltura automática bloqueada: identidade dos Pokémon ainda não foi confirmada entre Box, equipe e destinos.", Toast.LENGTH_LONG).show();
    }

    private void toggleEmergencyStop() {
        boolean on = !rules.emergencyStop();
        rules.setEmergencyStop(on);
        webView.evaluateJavascript("window.IdleCompanion && (window.IdleCompanion.busy=false);", null);
        setStatus(on ? "PARADA DE EMERGÊNCIA ativa · nenhuma automação destrutiva será executada" : "Parada liberada · simulação/proteções continuam valendo");
        addLog("emergency_stop", String.valueOf(on), null);
        updateHeader();
    }

    private void updateHeader() {
        runOnUiThread(() -> {
            if (rules.emergencyStop()) {
                safetyBadge.setText("STOP"); safetyBadge.setBackground(rounded(DANGER, 99, DANGER));
                stopButton.setText("LIBERAR");
            } else if (rules.isArmed()) {
                long min = Math.max(1, (rules.armedUntil() - System.currentTimeMillis() + 59999) / 60000);
                safetyBadge.setText("ARMADO " + min + "m"); safetyBadge.setBackground(rounded(DANGER, 99, DANGER));
                stopButton.setText("PARAR");
            } else if (rules.simulation()) {
                safetyBadge.setText("SIMULAÇÃO"); safetyBadge.setBackground(rounded(WARN, 99, WARN));
                stopButton.setText("PARAR");
            } else {
                safetyBadge.setText("PROTEGIDO"); safetyBadge.setBackground(rounded(SAFE, 99, SAFE));
                stopButton.setText("PARAR");
            }
            queueBadge.setText("Fila " + queue.size());
        });
    }

    private void showQueue() {
        if (queue.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Fila segura").setMessage("Nenhum candidato pendente. Pokémon protegidos nunca aparecem aqui como descarte automático.").setPositiveButton("OK", null).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (queue) {
            int i = 1;
            for (JSONObject p : queue) {
                if (i > 60) { sb.append("\n… e mais ").append(queue.size()-60); break; }
                sb.append(i++).append(". ").append(p.optString("species", "Pokémon"))
                        .append(" · IV ").append(p.isNull("iv") ? "?" : p.optInt("iv"))
                        .append(" · ").append(p.optString("quality", "?"))
                        .append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Candidatos para revisão · " + queue.size())
                .setMessage(sb.toString())
                .setNegativeButton("Fechar", null)
                .setNeutralButton("Limpar fila", (d,w) -> { synchronized (queue) { queue.clear(); queueKeys.clear(); } updateHeader(); })
                .setPositiveButton("Abrir Box", (d,w) -> openSection("Box", ""))
                .show();
    }

    private void showDashboard() {
        dashboard.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        rebuildDashboard();
    }

    private void showWeb() {
        dashboard.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void rebuildDashboard() {
        if (content == null) return;
        int idx = content.indexOfChild(dashboard);
        if (idx < 0) return;
        content.removeView(dashboard);
        dashboard = buildDashboard();
        content.addView(dashboard, idx);
        dashboard.setVisibility(webView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private boolean isIdleDex(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            return host != null && (host.equals("idledex.com") || host.endsWith(".idledex.com"));
        } catch (Exception e) { return false; }
    }

    private void openSection(String section, String sub) {
        showWeb();
        if (!pageReady) { setStatus("Faça login no IdleDex primeiro."); return; }
        String safeSection = JSONObject.quote(section);
        String safeSub = JSONObject.quote(sub == null ? "" : sub);
        webView.evaluateJavascript("window.IdleCompanion && window.IdleCompanion.openSection(" + safeSection + "," + safeSub + ");", null);
    }

    private void startBoxScan(String source) {
        showWeb();
        if (!pageReady) { setStatus("Faça login no IdleDex primeiro."); return; }
        if (rules.emergencyStop()) { setStatus("Automação bloqueada pela PARADA DE EMERGÊNCIA."); return; }
        releasedThisCycle = 0;
        addLog("scan_requested", source, rules.toJson());
        webView.evaluateJavascript("window.IdleCompanion && window.IdleCompanion.startBoxScan();", null);
    }

    private void injectCompanion() {
        String js = """
(() => {
  if (location.hostname !== 'idledex.com' && !location.hostname.endsWith('.idledex.com')) return;
  if (window.IdleCompanion) { IdleAndroid.pushStatus('Companion v2 ativo.'); return; }

  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const clean = (s,n=9000) => String(s || '').replace(/\\s+/g,' ').trim().slice(0,n);
  const low = s => clean(s,1000).toLowerCase();
  const visible = el => { try { const r=el.getBoundingClientRect(), cs=getComputedStyle(el); return r.width>1 && r.height>1 && cs.visibility!=='hidden' && cs.display!=='none' && cs.opacity!=='0'; } catch(e){ return false; } };
  const txt = el => clean((el && (el.getAttribute?.('aria-label') || el.getAttribute?.('title') || el.innerText || el.textContent)) || '',500);
  const buttons = (root=document) => [...root.querySelectorAll('button,[role="button"],a[href],input[type="button"],input[type="submit"]')].filter(visible);
  const exact = (name,root=document) => buttons(root).find(el => low(txt(el))===low(name));
  const contains = (name,root=document) => buttons(root).find(el => low(txt(el)).includes(low(name)));

  function closeTop(){ const c=buttons().filter(el => ['fechar','close','×','voltar'].includes(low(txt(el))) || low(el.getAttribute?.('aria-label')||'').includes('fechar')); if(!c.length)return false; c[c.length-1].click(); return true; }
  function modalRoot(){ const r=[...document.querySelectorAll('[role="dialog"],dialog,[aria-modal="true"],.modal,.drawer,.sheet')].filter(visible); return r.length?r[r.length-1]:document.body; }

  function parsePokemon(root){
    const text=clean(root?.innerText||root?.textContent||'',14000);
    const html=clean(root?.innerHTML||'',20000);
    const lower=low(text+' '+html);
    const qualities=['EXCEPCIONAL','EXCELENTE','ÓTIMO','OTIMO','MUITO BOM','BOM','REGULAR','RUIM'];
    const quality=qualities.find(q=>lower.includes(low(q)))||'';
    const ivm=text.match(/([0-9]{1,3}) *[/] *186/);
    const levelm=text.match(/(?:Nível|Nivel|Lv[.]?|Level) *[: -]? *([0-9]{1,3})/i);
    const headings=[...root.querySelectorAll?.('h1,h2,h3,h4,[role="heading"]')||[]].filter(visible).map(txt).filter(Boolean);
    const generic=['detalhes','golpes','equipe','box','pokémon','pokemon','informações','informacoes'];
    let species=headings.find(h=>!generic.includes(low(h)) && h.length<60)||'';
    if(!species){ const m=text.match(/^([A-Za-zÀ-ÿ0-9♀♂ .'-]{2,40}) +(Lv[.]?|Nível|Nivel|Level) *[0-9]+/i); if(m) species=clean(m[1],60); }
    const locked=buttons(root).some(el=>low(txt(el))==='destravar') || lower.includes('destravar');
    const shiny=lower.includes('shiny');
    return {species,quality,iv:ivm?parseInt(ivm[1],10):null,level:levelm?parseInt(levelm[1],10):null,shiny,locked};
  }

  function samePokemon(a,b){ return a.species===b.species && a.quality===b.quality && a.iv===b.iv && a.level===b.level && a.shiny===b.shiny && a.locked===b.locked; }

  async function openSection(section,sub){
    IdleAndroid.pushStatus('Abrindo '+section+'…');
    let b=exact(section) || contains(section);
    if(!b){ const menu=contains('Abrir menu')||exact('Menu'); if(menu){ menu.click(); await sleep(450); b=exact(section)||contains(section); } }
    if(section==='Inventário' && !b) b=contains('invent');
    if(section==='Mercado' && !b) b=contains('market');
    if(section==='Centro de Cura' && !b) b=contains('cura');
    if(!b){ IdleAndroid.pushStatus('Não encontrei '+section+' nesta tela.'); return false; }
    b.click(); await sleep(700);
    if(sub){ const s=exact(sub)||contains(sub); if(s){ s.click(); await sleep(500); } }
    IdleAndroid.pushStatus(section+(sub?' · '+sub:'')+' aberto.');
    return true;
  }

  function pageFingerprint(){ const d=buttons().filter(el=>low(txt(el))==='detalhes'); return d.slice(0,12).map(el=>clean(el.parentElement?.innerText||'',220)).join('|')+'#'+d.length; }

  async function scanPage(processed){
    let guard=0;
    while(guard++<100){
      if(IdleAndroid.isEmergencyStop()) throw new Error('PARADA DE EMERGÊNCIA');
      const details=buttons().filter(el=>low(txt(el))==='detalhes').filter(el=>!processed.has(clean(el.parentElement?.innerText||el.outerHTML,400)));
      if(!details.length) break;
      const btn=details[0], sig=clean(btn.parentElement?.innerText||btn.outerHTML,400); processed.add(sig);
      try{btn.scrollIntoView({block:'center'});}catch(e){}
      btn.click(); await sleep(650);
      let root=modalRoot();
      const p1=parsePokemon(root); await sleep(350); root=modalRoot(); const p2=parsePokemon(root);
      const consistent=samePokemon(p1,p2);
      let decision={action:'QUEUE',reasons:['falha ao avaliar']};
      try{ decision=JSON.parse(IdleAndroid.evaluatePokemon(JSON.stringify(p2),consistent)); }catch(e){}
      IdleAndroid.onPokemon(JSON.stringify(p2),JSON.stringify(decision));
      await sleep(250);
      closeTop(); await sleep(450);
    }
  }

  async function nextPage(seen){
    const c=buttons().filter(el=>['próxima','proxima','próximo','proximo','next','›','»','>'].includes(low(txt(el))) || low(el.getAttribute?.('aria-label')||'').includes('next'));
    const n=c.find(el=>!el.disabled && el.getAttribute('aria-disabled')!=='true'); if(!n)return false;
    const before=pageFingerprint(); n.click(); await sleep(800); const after=pageFingerprint();
    if(!after||after===before||seen.has(after))return false; seen.add(after); return true;
  }

  async function startBoxScan(){
    if(window.IdleCompanion.busy){IdleAndroid.pushStatus('A Box já está sendo analisada.');return;}
    window.IdleCompanion.busy=true; IdleAndroid.beginScan();
    const processed=new Set(), seen=new Set();
    try{
      if(!(await openSection('Box',''))) throw new Error('Box não encontrada');
      let pages=0;
      while(pages++<40){ if(IdleAndroid.isEmergencyStop()) throw new Error('PARADA DE EMERGÊNCIA'); const fp=pageFingerprint(); if(fp)seen.add(fp); IdleAndroid.pushStatus('Box · página '+pages+' · '+processed.size+' vistos'); await scanPage(processed); if(!(await nextPage(seen)))break; }
      IdleAndroid.pushEvent('scan_complete',JSON.stringify({processed:processed.size,pages:seen.size}));
      IdleAndroid.pushStatus('Box concluída · '+processed.size+' Pokémon analisados.');
    }catch(e){IdleAndroid.pushEvent('scan_error',JSON.stringify({message:String(e)}));IdleAndroid.pushStatus('Análise interrompida: '+String(e).replace('Error: ',''));}
    finally{window.IdleCompanion.busy=false;}
  }

  window.IdleCompanion={busy:false,startBoxScan,openSection};
  IdleAndroid.pushStatus('IdleDex Companion v2 ativo.');
})();
""";
        webView.evaluateJavascript(js, null);
    }

    private void addQueue(JSONObject p) {
        String key = p.optString("species", "?") + "|" + p.optInt("iv", -1) + "|" + p.optInt("level", -1) + "|" + p.optString("quality", "?");
        synchronized (queue) {
            if (queueKeys.add(key)) queue.add(p);
            if (queue.size() > 500) { JSONObject first = queue.remove(0); queueKeys.clear(); for (JSONObject q : queue) queueKeys.add(q.optString("species", "?") + "|" + q.optInt("iv", -1) + "|" + q.optInt("level", -1) + "|" + q.optString("quality", "?")); }
        }
        updateHeader();
    }

    private void addLog(String type, String source, JSONObject data) {
        try {
            JSONObject e = new JSONObject();
            e.put("ts", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));
            e.put("type", type); e.put("source", source == null ? "" : source); if (data != null) e.put("data", data);
            synchronized (logs) { logs.add(e); if (logs.size() > 2000) logs.remove(0); }
        } catch (JSONException ignored) {}
    }

    private void exportLogs() {
        try {
            JSONObject root = new JSONObject(); root.put("app", "IdleDex Companion"); root.put("version", BuildConfig.VERSION_NAME); root.put("rules", rules.toJson());
            JSONArray a = new JSONArray(); synchronized (logs) { for (JSONObject e : logs) a.put(e); } root.put("events", a);
            JSONArray q = new JSONArray(); synchronized (queue) { for (JSONObject e : queue) q.put(e); } root.put("queue", q);
            byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.DISPLAY_NAME, "idledex-companion-log.json"); values.put(MediaStore.Downloads.MIME_TYPE, "application/json"); values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/IdleDexCompanion"); values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values); if (uri == null) throw new IllegalStateException("não foi possível criar o arquivo");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) { if (out != null) out.write(bytes); }
                ContentValues done = new ContentValues(); done.put(MediaStore.Downloads.IS_PENDING, 0); getContentResolver().update(uri, done, null, null);
                Intent share = new Intent(Intent.ACTION_SEND); share.setType("application/json"); share.putExtra(Intent.EXTRA_STREAM, uri); share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(share, "Enviar log para…")); return;
            }
        } catch (Exception e) { setStatus("Falha ao exportar log: " + e.getMessage()); }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json"); intent.putExtra(Intent.EXTRA_TITLE, "idledex-companion-log.json"); startActivityForResult(intent, CREATE_LOG_FILE);
    }

    private View navButton(String text, View.OnClickListener l) { Button b = smallButton(text, PANEL2); b.setTextColor(TEXT); b.setOnClickListener(l); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)); lp.setMargins(dp(3),0,dp(3),0); b.setLayoutParams(lp); return b; }
    private Button primaryButton(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setBackground(rounded(ACCENT, 12, ACCENT)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)); lp.setMargins(0,dp(6),0,dp(4)); b.setLayoutParams(lp); return b; }
    private Button secondaryButton(String text) { Button b = new Button(this); b.setText(text); b.setTextColor(TEXT); b.setAllCaps(false); b.setBackground(rounded(PANEL2, 12, Color.rgb(62,75,91))); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)); lp.setMargins(0,dp(6),0,dp(4)); b.setLayoutParams(lp); return b; }
    private Button smallButton(String text, int color) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setTextSize(12); b.setBackground(rounded(color, 10, color)); b.setPadding(dp(10),0,dp(10),0); return b; }
    private TextView badge(String text, int color) { TextView t = label(text, Color.WHITE, 11, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(9),dp(4),dp(9),dp(4)); t.setBackground(rounded(color,99,color)); return t; }
    private TextView label(String text, int color, int size, boolean bold) { TextView t = new TextView(this); t.setText(text); t.setTextColor(color); t.setTextSize(size); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private CheckBox check(String text, boolean value) { CheckBox c = new CheckBox(this); c.setText(text); c.setChecked(value); c.setPadding(0,dp(4),0,dp(4)); return c; }
    private EditText field(String hint, String value, boolean numeric) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER); return e; }
    private View space(int w, int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(w,h)); return v; }
    private GradientDrawable rounded(int fill, int radiusDp, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radiusDp)); g.setStroke(dp(1), stroke); return g; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private void setStatus(String text) { runOnUiThread(() -> status.setText(text)); }

    @Override protected void onResume() { super.onResume(); if (updater != null) updater.resumePendingInstall(); updateHeader(); }
    @Override protected void onDestroy() { handler.removeCallbacks(scheduler); if (updater != null) updater.destroy(); if (webView != null) webView.removeJavascriptInterface("IdleAndroid"); super.onDestroy(); }
    @Override public void onBackPressed() { if (webView.getVisibility()==View.VISIBLE && webView.canGoBack()) webView.goBack(); else if (webView.getVisibility()==View.VISIBLE) showDashboard(); else super.onBackPressed(); }

    public class CompanionBridge {
        @JavascriptInterface public void beginScan() { releasedThisCycle = 0; }
        @JavascriptInterface public boolean isEmergencyStop() { return rules.emergencyStop(); }
        @JavascriptInterface public String evaluatePokemon(String json, boolean consistent) {
            try { return rules.evaluate(new JSONObject(json), consistent, releasedThisCycle).toString(); }
            catch (Exception e) { return "{\"action\":\"QUEUE\",\"reasons\":[\"erro de leitura\"]}"; }
        }
        @JavascriptInterface public boolean canRelease(String json, boolean consistent) {
            // Legacy release automation is unavailable while cross-surface identity is unproven.
            return false;
        }
        @JavascriptInterface public void onPokemon(String json, String decisionJson) {
            try {
                JSONObject p = new JSONObject(json); JSONObject d = new JSONObject(decisionJson);
                JSONObject data = new JSONObject(); data.put("pokemon", p); data.put("decision", d); addLog("pokemon_review", "web", data);
                String action = d.optString("action", "QUEUE");
                if ("QUEUE".equals(action)) addQueue(p);
                String iv = p.isNull("iv") ? "?" : String.valueOf(p.optInt("iv"));
                String human = "KEEP".equals(action) ? "PROTEGIDO" : ("RELEASE".equals(action) ? "SOLTURA AUTORIZADA" : "FILA SEGURA");
                setStatus(p.optString("species", "Pokémon") + " · IV " + iv + " · " + human);
            } catch (Exception ignored) {}
        }
        @JavascriptInterface public void onReleased(String json) {
            releasedThisCycle++;
            try { addLog("released", "web", new JSONObject(json)); } catch (Exception ignored) {}
            updateHeader();
        }
        @JavascriptInterface public void pushStatus(String message) { setStatus(message); }
        @JavascriptInterface public void pushEvent(String type, String json) { try { addLog(type, "web", json==null||json.isEmpty()?null:new JSONObject(json)); } catch(Exception e){ addLog(type,"web",null); } }
        @JavascriptInterface public void exportLog() { runOnUiThread(() -> exportLogs()); }
    }
}
