package com.albasil.inventory;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 2201;
    private static final int CAMERA_REQUEST = 2202;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        request.deny();
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                // Force Android's document picker toward spreadsheet/document files,
                // instead of opening Gallery/Photos. The phone does NOT need Excel installed;
                // it only needs to select the XLSX/CSV file from Downloads or Files.
                intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "application/csv",
                        "text/comma-separated-values",
                        "text/tab-separated-values",
                        "text/plain",
                        "application/octet-stream"
                });
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    // Fallback for unusual Android builds: still use the system Documents picker.
                    Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    fallback.addCategory(Intent.CATEGORY_OPENABLE);
                    fallback.setType("application/octet-stream");
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(fallback, FILE_CHOOSER_REQUEST);
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new AppBridge(), "AndroidBridge");
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private void launchNativeScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            Toast.makeText(this, "اسمح باستخدام الكاميرا ثم اضغط مسح الباركود مرة أخرى", Toast.LENGTH_LONG).show();
            return;
        }

        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("وجّه الكاميرا إلى باركود الصنف");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void vibrateScanSuccess() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(90);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) {
            if (webView != null) {
                if (scanResult.getContents() != null) {
                    vibrateScanSuccess();
                    String js = "window.onNativeBarcodeScanned && window.onNativeBarcodeScanned(" +
                            JSONObject.quote(scanResult.getContents()) + ");";
                    webView.evaluateJavascript(js, null);
                } else {
                    webView.evaluateJavascript("window.onNativeBarcodeCancelled && window.onNativeBarcodeCancelled();", null);
                }
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "(function(){if(window.alBasilShowPage){var active=document.querySelector('.base-page.active,.section.active');if(active&&active.id!=='dashboard'){window.alBasilShowPage('dashboard','الرئيسية');return 'handled';}}return 'no';})()",
                    value -> {
                        if (!"\"handled\"".equals(value)) {
                            if (webView.canGoBack()) webView.goBack();
                            else MainActivity.super.onBackPressed();
                        }
                    }
            );
        } else {
            super.onBackPressed();
        }
    }

    public class AppBridge {
        @JavascriptInterface
        public void scanBarcode() {
            runOnUiThread(MainActivity.this::launchNativeScanner);
        }

        @JavascriptInterface
        public void saveTextFile(String name, String content, String mime) {
            runOnUiThread(() -> {
                try {
                    OutputStream os;
                    String savedAt;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, sanitizeName(name));
                        values.put(MediaStore.Downloads.MIME_TYPE, mime == null ? "text/plain" : mime.split(";")[0]);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AL BASIL");
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new Exception("تعذر إنشاء الملف");
                        os = getContentResolver().openOutputStream(uri);
                        savedAt = "Downloads/AL BASIL/" + sanitizeName(name);
                    } else {
                        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AL BASIL");
                        if (!dir.exists() && !dir.mkdirs()) throw new Exception("تعذر إنشاء المجلد");
                        File f = new File(dir, sanitizeName(name));
                        os = new FileOutputStream(f);
                        savedAt = f.getAbsolutePath();
                    }
                    if (os == null) throw new Exception("تعذر فتح الملف");
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                    Toast.makeText(MainActivity.this, "تم الحفظ: " + savedAt, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        private String sanitizeName(String name) {
            String n = (name == null || name.trim().isEmpty()) ? "AL_BASIL_export.txt" : name.trim();
            return n.replaceAll("[\\\\/:*?\"<>|]", "_");
        }
    }
}
