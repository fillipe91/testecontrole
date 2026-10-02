from pathlib import Path
import re

ROOT = Path("winlator-src")

build_gradle = ROOT / "app/build.gradle"
s = build_gradle.read_text(encoding="utf-8")
s = s.replace("applicationId 'com.winlator'", "applicationId 'com.fillipe.operadesktop'")
s = s.replace('versionName "11.2"', 'versionName "1.1"')
if 'signingConfigs {' not in s:
    s = s.replace('android {\n', '''android {\n    signingConfigs {\n        debug {\n            v1SigningEnabled true\n            v2SigningEnabled true\n        }\n    }\n''', 1)
build_gradle.write_text(s, encoding="utf-8")

manifest = ROOT / "app/src/main/AndroidManifest.xml"
s = manifest.read_text(encoding="utf-8")
s = s.replace('android:authorities="com.winlator.FileProvider"', 'android:authorities="${applicationId}.FileProvider"')
old_activity = '''        <activity android:name="com.winlator.MainActivity"
            android:theme="@style/AppThemeDark"
            android:exported="true"
            android:screenOrientation="sensor"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>'''
new_activity = '''        <activity android:name="com.winlator.OperaMainActivity"
            android:theme="@style/AppThemeDark"
            android:exported="true"
            android:screenOrientation="sensor"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <activity android:name="com.winlator.MainActivity"
            android:theme="@style/AppThemeDark"
            android:exported="false"
            android:screenOrientation="sensor"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|density|navigation" />'''
if old_activity not in s:
    raise SystemExit("Could not locate MainActivity manifest block")
s = s.replace(old_activity, new_activity)
manifest.write_text(s, encoding="utf-8")

main_activity = ROOT / "app/src/main/java/com/winlator/MainActivity.java"
s = main_activity.read_text(encoding="utf-8")
pattern = re.compile(r'''    private boolean requestAppPermissions\(\) \{.*?\n    \}''', re.S)
s, n = pattern.subn('''    private boolean requestAppPermissions() {\n        return false;\n    }''', s, count=1)
if n != 1:
    raise SystemExit("Could not patch requestAppPermissions")
main_activity.write_text(s, encoding="utf-8")

xserver = ROOT / "app/src/main/java/com/winlator/XServerDisplayActivity.java"
s = xserver.read_text(encoding="utf-8")
needle = '''            if (intent.hasExtra("exec_path")) {
                execPath = WineUtils.unixToDOSPath(intent.getStringExtra("exec_path"), container);

                if (execPath.endsWith(".lnk")) {'''
replacement = '''            if (intent.hasExtra("exec_path")) {
                execPath = WineUtils.unixToDOSPath(intent.getStringExtra("exec_path"), container);
                String directExecArgs = intent.getStringExtra("exec_args");
                if (directExecArgs != null && !directExecArgs.isEmpty()) execArgs = " " + directExecArgs;

                if (execPath.endsWith(".lnk")) {'''
if needle not in s:
    raise SystemExit("Could not patch exec_args support")
s = s.replace(needle, replacement, 1)
xserver.write_text(s, encoding="utf-8")

for strings in (ROOT / "app/src/main/res").glob("values*/strings.xml"):
    t = strings.read_text(encoding="utf-8")
    t = t.replace('<string name="app_name">Winlator</string>', '<string name="app_name">Opera Desktop</string>')
    strings.write_text(t, encoding="utf-8")

opera_activity = ROOT / "app/src/main/java/com/winlator/OperaMainActivity.java"
opera_activity.write_text(r'''package com.winlator;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.xenvironment.RootFS;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class OperaMainActivity extends MainActivity {
    private static final String CONTAINER_NAME = "Opera Desktop";
    private static final String OPERA_URL = "https://get.geo.opera.com/pub/opera/desktop/136.0.6008.80/win/Opera_136.0.6008.80_Setup_x64.exe";
    private static final String INSTALL_ARGS = "/install /silent /launchopera=0 /setdefaultbrowser=0 /allusers=0 /desktopshortcut=0 /pintotaskbar=0";
    private static final String OPERA_ARGS = "--no-sandbox --disable-gpu";
    private volatile boolean flowStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startOperaFlow();
    }

    private void startOperaFlow() {
        if (flowStarted) return;
        flowStarted = true;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long deadline = System.currentTimeMillis() + 10L * 60L * 1000L;
                RootFS rootFS = RootFS.find(this);
                while (!rootFS.isValid() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(750);
                    rootFS = RootFS.find(this);
                }
                if (!rootFS.isValid()) {
                    showError("Não foi possível preparar o ambiente Windows.");
                    return;
                }
                runOnUiThread(this::ensureContainerAndLaunch);
            }
            catch (Throwable e) {
                showError("Falha ao preparar o Opera: " + e.getMessage());
            }
        });
    }

    private void ensureContainerAndLaunch() {
        try {
            ContainerManager manager = new ContainerManager(this);
            for (Container item : manager.getContainers()) {
                if (CONTAINER_NAME.equals(item.getName())) {
                    prepareAndLaunch(item);
                    return;
                }
            }

            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", "1280x720");
            data.put("graphicsDriver", "vortek,gladio");
            data.put("dxwrapper", "dxvk");
            data.put("audioDriver", "alsa");
            data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
            manager.createContainerAsync(data, container -> {
                if (container == null) showError("Não foi possível criar o ambiente do Opera.");
                else prepareAndLaunch(container);
            });
        }
        catch (Throwable e) {
            showError("Falha ao criar o ambiente: " + e.getMessage());
        }
    }

    private void prepareAndLaunch(Container container) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File launcher = findOperaLauncher(container);
                if (launcher != null) {
                    runOnUiThread(() -> launchWindowsFile(container, launcher, OPERA_ARGS));
                    return;
                }

                showInfo("Baixando o Opera pela primeira vez...");
                File installer = downloadInstaller(container);
                runOnUiThread(() -> launchWindowsFile(container, installer, INSTALL_ARGS));
            }
            catch (Throwable e) {
                showError("Falha ao baixar/preparar o Opera: " + e.getMessage());
            }
        });
    }

    private File findOperaLauncher(Container container) {
        File driveC = new File(container.getRootDir(), ".wine/drive_c");
        File[] candidates = new File[] {
            new File(driveC, "Opera/launcher.exe"),
            new File(driveC, "Opera/opera.exe"),
            new File(driveC, "users/" + RootFS.USER + "/AppData/Local/Programs/Opera/launcher.exe"),
            new File(driveC, "users/" + RootFS.USER + "/AppData/Local/Programs/Opera/opera.exe")
        };
        for (File f : candidates) if (f.isFile()) return f;
        return null;
    }

    private File downloadInstaller(Container container) throws Exception {
        File dst = new File(container.getRootDir(), ".wine/drive_c/OperaSetup.exe");
        if (dst.isFile() && dst.length() > 100_000_000L) return dst;
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();

        HttpURLConnection connection = (HttpURLConnection) new URL(OPERA_URL).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(true);
        connection.connect();
        int code = connection.getResponseCode();
        if (code < 200 || code >= 400) throw new IllegalStateException("HTTP " + code);

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        finally {
            connection.disconnect();
        }
        if (!dst.isFile() || dst.length() < 100_000_000L) throw new IllegalStateException("download incompleto");
        return dst;
    }

    private void launchWindowsFile(Container container, File exe, String args) {
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getAbsolutePath());
        intent.putExtra("exec_args", args);
        startActivity(intent);
    }

    private void showInfo(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
}
''', encoding="utf-8")

print("Winlator patched for slim Opera Desktop wrapper")
