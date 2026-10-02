package com.fillipe.webmapper;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class CompanionHomeActivity extends Activity {
    private static final int BG = Color.rgb(13,17,23);
    private static final int PANEL = Color.rgb(24,30,38);
    private static final int TEXT = Color.rgb(238,244,250);
    private static final int MUTED = Color.rgb(166,180,195);
    private static final int ACCENT = Color.rgb(68,153,255);
    private static final int SAFE = Color.rgb(55,196,120);
    private static final int WARN = Color.rgb(255,183,77);

    private TextView updateStatus;
    private AppUpdater updater;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        updater = new AppUpdater(this, this::setUpdateStatus);

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(16),dp(16),dp(28));

        TextView title = text("IdleDex Companion", TEXT, 24, true);
        TextView version = text("v" + BuildConfig.VERSION_NAME + " · painel protegido", MUTED, 13, false);
        version.setPadding(0,dp(4),0,dp(16));
        body.addView(title); body.addView(version);

        LinearLayout play = card("Jogar e gerenciar Box", "Abre o Companion principal com AUTO, Box, Pokédex, itens, mercado e as proteções existentes.");
        Button playBtn = primary("Abrir Companion", ACCENT);
        playBtn.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        play.addView(playBtn); body.addView(play);

        LinearLayout sell = card("Venda Segura", "Novo modo para separar Pokémon fracos sem arriscar seus times ou Pokémon valiosos.");
        TextView hard = text("Proteção permanente: times ativos/salvos, Shiny, Lendário/Mítico, 4★/5★, evento, travados/favoritos, IV alto, únicos e melhores duplicatas.", Color.rgb(130,240,180), 13, true);
        hard.setPadding(0,0,0,dp(8));
        sell.addView(hard);
        Button sellBtn = primary("Abrir Venda Segura", SAFE);
        sellBtn.setOnClickListener(v -> startActivity(new Intent(this, SafeSellActivity.class)));
        sell.addView(sellBtn); body.addView(sell);

        LinearLayout safety = card("Como a Venda Segura funciona", "Primeiro ela audita a Box inteira e cria uma fila. A venda real só é liberada depois de revalidar o Pokémon e todos os seus times novamente. Se qualquer informação estiver faltando ou mudar, a operação é cancelada.");
        TextView safeInfo = text("Por padrão ela inicia em SIMULAÇÃO. Para vender de verdade, você precisa definir o preço, desligar a simulação e autorizar por 10 minutos.", Color.rgb(255,210,120), 13, true);
        safety.addView(safeInfo); body.addView(safety);

        LinearLayout updates = card("Atualizações", "Use este botão para procurar novas versões do Companion.");
        Button update = primary("Verificar atualização", WARN);
        update.setOnClickListener(v -> updater.checkForUpdate(true));
        updateStatus = text("", MUTED, 12, false);
        updateStatus.setPadding(0,dp(7),0,0);
        updates.addView(update); updates.addView(updateStatus); body.addView(updates);

        sc.addView(body);
        setContentView(sc);
        updater.checkForUpdate(false);
    }

    private LinearLayout card(String title,String subtitle){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,dp(12));c.setLayoutParams(lp);c.setBackground(rounded(PANEL,16));
        c.addView(text(title,TEXT,17,true));TextView s=text(subtitle,MUTED,13,false);s.setPadding(0,dp(5),0,dp(10));c.addView(s);return c;
    }
    private Button primary(String label,int color){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setBackground(rounded(color,12));b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));return b;}
    private TextView text(String t,int color,int size,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextColor(color);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void setUpdateStatus(String s){runOnUiThread(()->{if(updateStatus!=null)updateStatus.setText(s);});}
    @Override protected void onResume(){super.onResume();if(updater!=null)updater.resumePendingInstall();}
    @Override protected void onDestroy(){if(updater!=null)updater.destroy();super.onDestroy();}
}
