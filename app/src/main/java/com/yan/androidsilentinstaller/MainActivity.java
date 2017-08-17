package com.yan.androidsilentinstaller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private TextView tvTitle;
    private Button btnRoot, btnAccess, btnSelectApk, btnOpenAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvTitle = (TextView) findViewById(R.id.title);
        btnRoot = (Button) findViewById(R.id.btn_root);
        btnAccess = (Button) findViewById(R.id.btn_access);
        btnSelectApk = (Button) findViewById(R.id.btn_selectapk);
        btnOpenAccess = (Button) findViewById(R.id.btn_openaccess);

        btnRoot.setOnClickListener(this);
        btnAccess.setOnClickListener(this);
        btnSelectApk.setOnClickListener(this);
        btnOpenAccess.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        List<String> permissionsNeeded = getRequiredPermissions(MainActivity.this);
        if(permissionsNeeded.size()>0){
            Toast.makeText(MainActivity.this,"Permissions are required",Toast.LENGTH_SHORT).show();
            // now request permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    permissionsNeeded.toArray(new String[permissionsNeeded.size()]),
                    0);
        }else{
            if (v == btnRoot) {
                rootInstall();
            }
            if (v == btnAccess) {
                accessInstall();
            }
            if (v == btnSelectApk) {
                selectApk();
            }
            if (v == btnOpenAccess) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            }
        }
    }
    private void accessInstall() {
        final String strPath = tvTitle.getText().toString();
        if (TextUtils.isEmpty(strPath)) {
            return;
        }
        Uri uri = Uri.fromFile(new File(strPath));
        Intent localIntent = new Intent(Intent.ACTION_VIEW);
        localIntent.setDataAndType(uri, "application/vnd.android.package-archive");
        startActivity(localIntent);
    }
    private static final int FILE_SELECT = 0;
    private void rootInstall() {
        final String strPath = tvTitle.getText().toString();
        if (TextUtils.isEmpty(strPath)) {
            return;
        }
        boolean isRoot = new File("/system/bin/su").exists() || new File("/system/xbin/su").exists();
        if (!isRoot) {
            Toast.makeText(this, "The phone does not have a root", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(){
            @Override
            public void run() {
                RootInstaller installer = new RootInstaller();
                final boolean result = installer.install(strPath);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result) {
                            Toast.makeText(MainActivity.this, "Successful installation", Toast.LENGTH_SHORT).show();
                        }else {
                            Toast.makeText(MainActivity.this, "Installation failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }.start();
    }
    private void selectApk() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select an APK"), FILE_SELECT);
        }catch (ActivityNotFoundException e) {
            Toast.makeText(this, "File browser not found", Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == FILE_SELECT && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            try {
                File file = InputStreamToFileApp(uri);
                if(file.exists()){
                    String path = file.getAbsolutePath(); //getFilePath(this, uri);
                    tvTitle.setText(path);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "An error occurred", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
   /* public static String getPath(Context context, Uri uri) throws URISyntaxException {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor = null;

            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                // Eat it
            }
        }
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }*/
   @SuppressLint("NewApi")
   public static String getRealPathFromURI_API19(Context context, Uri uri){
       String filePath = "";
       String wholeID = DocumentsContract.getDocumentId(uri);

       // Split at colon, use second item in the array
       String id = wholeID.split(":")[1];

       String[] column = { MediaStore.Images.Media.DATA };

       // where id is equal to
       String sel = MediaStore.Images.Media._ID + "=?";

       Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
               column, sel, new String[]{ id }, null);

       int columnIndex = cursor.getColumnIndex(column[0]);

       if (cursor.moveToFirst()) {
           filePath = cursor.getString(columnIndex);
       }
       cursor.close();
       return filePath;
   }

    public File InputStreamToFileApp(Uri uri) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            String[] pathArray = uri.getPath().split("/");
            if(pathArray.length>0){
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/"
                        +"test_"+pathArray[pathArray.length-1]);
                try {
                    // read this file into InputStream
                    inputStream = getContentResolver().openInputStream(uri);

                    if (!file.exists()) {
                        file.createNewFile();
                    }

                    // write the inputStream to a FileOutputStream
                    outputStream =
                            new FileOutputStream(file);


                    int read = 0;
                    byte[] bytes = new byte[65536];

                    while ((read = inputStream.read(bytes)) != -1) {
                        outputStream.write(bytes, 0, read);
                    }

                    System.out.println("Done!");

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (outputStream != null) {
                        try {
                            // outputStream.flush();
                            outputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    return file;
                }
            }else{
                return null;
            }
    }


        public static List<String> getRequiredPermissions(Context context) {
            final List<String> permissionsList = new ArrayList<>();
            if (!addPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return permissionsList;
        }
    private static boolean addPermission(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
//            permissionsList.add(permission);
            // Check for Rationale Option
            if (!ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, permission))
                return false;
        }
        return true;
    }
}