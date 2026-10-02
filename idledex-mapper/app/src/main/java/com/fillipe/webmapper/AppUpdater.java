package com.fillipe.webmapper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads privately, verifies bytes and signer, then delegates installation to Android. */
public class AppUpdater {
    public interface Listener { void onStatus(String message); }
    private static final String MANIFEST_URL="https://raw.githubusercontent.com/fillipe91/testecontrole/idledex-companion-v1/releases/idledex-companion-update.json";
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean busy=new AtomicBoolean();
    private volatile boolean destroyed;
    private final SharedPreferences pending;
    public AppUpdater(Context context,Listener listener){this.context=context;this.listener=listener;pending=context.getSharedPreferences("pending_update",Context.MODE_PRIVATE);}
    static File apk(Context context){return new File(context.getFilesDir(),"updates/companion.apk");}
    private HttpURLConnection connect(String address)throws Exception{
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol()))throw new IOException("O endereço da atualização não é seguro.");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");
        if(c.getResponseCode()!=200){c.disconnect();throw new IOException("Servidor indisponível. Tente novamente.");}
        if(!"https".equals(c.getURL().getProtocol())){c.disconnect();throw new IOException("Redirecionamento não seguro.");}return c;
    }
    public void checkForUpdate(boolean userInitiated){
        if(!busy.compareAndSet(false,true)){if(userInitiated)status("Uma atualização já está sendo verificada. Aguarde.");return;}
        if(userInitiated)status("Procurando atualização…");
        new Thread(()->{try{
            JSONObject m;HttpURLConnection c=connect(MANIFEST_URL+"?check="+System.currentTimeMillis());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>65536)throw new IOException("Manifesto inválido.");}m=new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();}
            int version=m.getInt("versionCode");String name=m.getString("versionName");
            if(version<=BuildConfig.VERSION_CODE){if(userInitiated)status("Você está na versão atual: "+BuildConfig.VERSION_NAME+".");return;}
            if(!userInitiated){status("Versão "+name+" disponível. Toque em Verificar atualização.");return;}
            String hash=m.getString("sha256");if(!hash.matches("[a-fA-F0-9]{64}"))throw new IOException("Atualização sem verificação de integridade.");
            pending.edit().clear().commit();File target=apk(context);File folder=target.getParentFile();if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Sem espaço para baixar.");
            File partial=new File(folder,"download.tmp");status("Baixando versão "+name+"…");c=connect(m.getString("apkUrl"));
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] buf=new byte[32768];int n;long total=0;while((n=in.read(buf))!=-1){if(destroyed)throw new IOException("Download interrompido. Tente novamente.");total+=n;if(total>100*1024*1024)throw new IOException("Arquivo de atualização muito grande.");out.write(buf,0,n);}out.getFD().sync();}catch(Exception e){partial.delete();throw e;}finally{c.disconnect();}
            try{verify(partial,hash,version);if(target.exists()&&!target.delete())throw new IOException("Não foi possível substituir o download anterior.");if(!partial.renameTo(target))throw new IOException("Não foi possível salvar a atualização.");}catch(Exception e){partial.delete();throw e;}
            pending.edit().putString("hash",hash).putInt("version",version).putBoolean("waiting",true).commit();
            main.post(()->{if(!destroyed)install();});
        }catch(Exception e){if(userInitiated)status("Atualização não concluída: "+e.getMessage());}finally{busy.set(false);}},"app-update").start();
    }
    @SuppressWarnings("deprecation") private void verify(File file,String expected,int version)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!hash.toString().equalsIgnoreCase(expected))throw new IOException("Download incompleto ou corrompido. Baixe novamente.");
        PackageManager pm=context.getPackageManager();PackageInfo next=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES), current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        if(next==null||!context.getPackageName().equals(next.packageName)||next.versionCode!=version||version<=BuildConfig.VERSION_CODE)throw new IOException("APK incompatível com este aplicativo.");
        if(next.signatures==null||current.signatures==null||next.signatures.length!=current.signatures.length)throw new IOException("Assinatura da atualização não confere.");
        java.util.Set<Signature> a=new java.util.HashSet<>(java.util.Arrays.asList(next.signatures)),b=new java.util.HashSet<>(java.util.Arrays.asList(current.signatures));
        if(!a.equals(b))throw new IOException("A atualização não usa a chave original.");
    }
    private void install(){
        try{
            if(Build.VERSION.SDK_INT>=26&&!context.getPackageManager().canRequestPackageInstalls()){
                status("Permita atualizações deste app na tela do Android e volte para continuar.");
                context.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;
            }
            pending.edit().putBoolean("waiting",false).commit();
            Uri uri=Uri.parse("content://"+context.getPackageName()+".updates/companion.apk");
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClipData(android.content.ClipData.newRawUri("Atualização",uri));context.startActivity(intent);
            status("APK conferido. Confirme Atualizar na tela do Android.");
        }catch(Exception e){status("Não foi possível abrir o instalador: "+e.getMessage()+". Toque em Verificar atualização para tentar novamente.");}
    }
    public void resumePendingInstall(){
        if(!pending.getBoolean("waiting",false))return;
        if(Build.VERSION.SDK_INT>=26&&!context.getPackageManager().canRequestPackageInstalls()){status("Instalação pendente. Autorize este app em Instalar apps desconhecidos e tente novamente.");return;}
        if(!busy.compareAndSet(false,true))return;
        new Thread(()->{try{verify(apk(context),pending.getString("hash",""),pending.getInt("version",0));main.post(()->{if(!destroyed)install();});}catch(Exception e){pending.edit().clear().apply();status("Atualização pendente inválida. Toque em Verificar atualização.");}finally{busy.set(false);}},"update-verify").start();
    }
    public void destroy(){destroyed=true;main.removeCallbacksAndMessages(null);}
    private void status(String message){main.post(()->{if(!destroyed&&listener!=null)listener.onStatus(message);});}
}
