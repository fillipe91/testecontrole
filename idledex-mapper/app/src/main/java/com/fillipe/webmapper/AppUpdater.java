package com.fillipe.webmapper;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public class AppUpdater {
    public interface Listener { void onStatus(String message); }

    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/fillipe91/testecontrole/idledex-companion-v1/releases/idledex-companion-update.json";
    private final Context context;
    private final Listener listener;
    private final SharedPreferences prefs;
    private long activeDownloadId = -1L;
    private String expectedSha256 = "";
    private Uri pendingInstallUri;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id != activeDownloadId) return;
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = dm.getUriForDownloadedFile(id);
            if (uri == null) { status("Falha ao localizar o APK baixado."); return; }
            new Thread(() -> {
                try {
                    String actual = sha256(uri);
                    if (!expectedSha256.isEmpty() && !expectedSha256.equalsIgnoreCase(actual)) {
                        status("Atualização bloqueada: verificação de integridade falhou.");
                        return;
                    }
                    pendingInstallUri = uri;
                    status("Download concluído. Abrindo instalador seguro…");
                    installIfAllowed();
                } catch (Exception e) {
                    status("Falha ao verificar atualização: " + e.getMessage());
                }
            }).start();
        }
    };

    public AppUpdater(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.prefs = context.getSharedPreferences("idledex_updater", Context.MODE_PRIVATE);
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, f);
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
                expectedSha256 = m.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
                String notes = m.optString("notes", "");
                status("Nova versão " + latestName + " encontrada" + (notes.isEmpty() ? "." : ": " + notes));
                startDownload(apkUrl, latestName);
            } catch (Exception e) {
                if (userInitiated) status("Não consegui verificar atualizações: " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }

    private void startDownload(String apkUrl, String versionName) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("IdleDex Companion " + versionName);
            req.setDescription("Baixando atualização verificada");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                    "IdleDex-Companion-" + versionName + "-" + System.currentTimeMillis() + ".apk");
            activeDownloadId = dm.enqueue(req);
            prefs.edit().putLong("lastDownloadId", activeDownloadId).apply();
            status("Atualização baixando… quando terminar, o Android abrirá a instalação.");
        } catch (Exception e) {
            status("Falha ao iniciar download: " + e.getMessage());
        }
    }

    private String sha256(Uri uri) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("arquivo não pôde ser aberto");
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }

    private void installIfAllowed() {
        if (pendingInstallUri == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            status("Autorize 'Instalar apps desconhecidos' para este app. Depois volte; a instalação continuará.");
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(pendingInstallUri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    public void resumePendingInstall() { installIfAllowed(); }

    private void status(String message) {
        if (listener != null) listener.onStatus(message);
    }

    public void destroy() {
        try { context.unregisterReceiver(receiver); } catch (Exception ignored) {}
    }
}
