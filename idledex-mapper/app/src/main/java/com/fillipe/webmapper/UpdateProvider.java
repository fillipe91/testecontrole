package com.fillipe.webmapper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;

/** Grants the Android installer read-only access to exactly one private APK. */
public final class UpdateProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private void check(Uri uri){if(!"/companion.apk".equals(uri.getPath())||uri.getQuery()!=null)throw new IllegalArgumentException("Unknown update file");}
    public String getType(Uri uri){check(uri);return "application/vnd.android.package-archive";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{check(uri);if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(AppUpdater.apk(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){check(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="IdleDex-Companion.apk";else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=AppUpdater.apk(getContext()).length();}c.addRow(row);return c;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
