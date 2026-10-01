package com.fillipe.webmapper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdater {
    public interface Listener { void onStatus(String message); }

    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/fillipe91/testecontrole/idledex-companion-v1/releases/idledex-companion-update.json";
    private final Context context;
    private final Listener listener;

    public AppUpdater(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void checkForUpdate(boolean userInitiated) {
        if (userInitiated) status("Procurando atualização…");
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(12000);
                con.setRequestProperty("Cache-Control", "no-cache");
                int code = con.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("servidor respondeu " + code);

                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONObject m = new JSONObject(sb.toString());

                int latestCode = m.getInt("versionCode");
                String latestName = m.optString("versionName", String.valueOf(latestCode));
                if (latestCode <= BuildConfig.VERSION_CODE) {
                    if (userInitiated) status("Você já está na versão mais nova (" + BuildConfig.VERSION_NAME + ").");
                    return;
                }

                String apkUrl = m.getString("apkUrl");
                String notes = m.optString("notes", "");
                status("Nova versão " + latestName + " encontrada" + (notes.isEmpty() ? "." : ": " + notes));

                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                status("Atualização aberta. Confirme a instalação quando o Android solicitar.");
            } catch (Exception e) {
                if (userInitiated) status("Não consegui verificar atualizações: " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    public void resumePendingInstall() { }
    public void destroy() { }

    private void status(String message) {
        if (listener != null) listener.onStatus(message);
    }
}
