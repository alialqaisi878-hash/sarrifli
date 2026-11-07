package com.alsaqi.sarrifli;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

/* A thin shell around the web app. It opens the online copy (REMOTE) so new versions arrive without reinstalling,
   and falls back to the built-in copy in assets/www when the phone is offline. The page talks to Android through window.Native. */
public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    static final String REMOTE = "https://alialqaisi878-hash.github.io/sarrifli/";
    static final String LOCAL = "file:///android_asset/www/index.html";
    private boolean remoteFailed, switched, loadingRemote;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final int REQ_FILE = 11, REQ_GEO = 12, REQ_STORE = 13, REQ_NOTIF = 14;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0E1424"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("file:") || url.startsWith(REMOTE)) return false;
                try {
                    Intent i = url.startsWith("intent:") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "ما كدرنا نفتح الرابط", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                loadingRemote = url != null && url.startsWith(REMOTE);
                if (loadingRemote) ui.postDelayed(new Runnable() {
                    public void run() {
                        if (loadingRemote) offline();
                    }
                }, 15000);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                loadingRemote = false;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame() && r.getUrl().toString().startsWith(REMOTE)) offline();
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse res) {
                if (r.isForMainFrame() && res.getStatusCode() >= 400 && r.getUrl().toString().startsWith(REMOTE)) offline();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    startActivityForResult(Intent.createChooser(i, "اختار الشعار"), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    geoCb = cb;
                    geoOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
                }
            }
        });
        if (b != null) web.restoreState(b);
        else web.loadUrl(getSharedPreferences("app", 0).getBoolean("moved", false) ? REMOTE : LOCAL);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            fileCb.onReceiveValue(u == null ? null : new Uri[]{u});
            fileCb = null;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_GEO && geoCb != null) {
            geoCb.invoke(geoOrigin, ok, false);
            geoCb = null;
        }
        if (req == REQ_STORE) {
            Toast.makeText(this, ok ? "هسة دوس مرة ثانية" : "نحتاج إذن الصور حتى نحفظ", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.onNativeBack&&window.onNativeBack())?1:0", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!"1".equals(v)) MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    /* The online copy could not be reached: show the built-in one for the rest of this run. */
    private void offline() {
        loadingRemote = false;
        if (remoteFailed) return;
        remoteFailed = true;
        web.stopLoading();
        web.loadUrl(LOCAL);
    }

    /* Saves a PNG (data URL) into the gallery under Pictures/Sarrifli and returns its content Uri. */
    private Uri savePng(String dataUrl) {
        try {
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORE);
                return null;
            }
            byte[] png = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, "asaar-" + System.currentTimeMillis() + ".png");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) v.put("relative_path", "Pictures/Sarrifli");
            Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            OutputStream o = getContentResolver().openOutputStream(u);
            o.write(png);
            o.close();
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    private class Bridge {
        /* The page hands over its saved data (prices, account) so both copies of the app share it. */
        @JavascriptInterface
        public void keep(String json) {
            getSharedPreferences("app", 0).edit().putString("data", json).apply();
            runOnUiThread(new Runnable() {
                public void run() {
                    String u = web.getUrl();
                    if (switched || remoteFailed || u == null || !u.startsWith("file:")) return;
                    switched = true;
                    getSharedPreferences("app", 0).edit().putBoolean("moved", true).apply();
                    web.loadUrl(REMOTE);
                }
            });
        }

        @JavascriptInterface
        public String restore() {
            return getSharedPreferences("app", 0).getString("data", "");
        }

        @JavascriptInterface
        public void notice(String title, String body) {
            NotifJob.show(MainActivity.this, title, body, (int) (System.currentTimeMillis() % 100000));
        }

        @JavascriptInterface
        public boolean notifOn() {
            return Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void askNotif() {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }

        /* Called by the page whenever it knows the server config and the newest notice it has seen. */
        @JavascriptInterface
        public void sync(String pid, String key, double lastSeen, boolean paid, String role) {
            android.content.SharedPreferences sp = getSharedPreferences(NotifJob.PREFS, 0);
            long last = Math.max(sp.getLong("last", 0), (long) lastSeen);
            sp.edit().putString("pid", pid == null ? "" : pid).putString("key", key == null ? "" : key).putLong("last", last).putBoolean("paid", paid).putString("role", role == null ? "" : role).apply();
            if (pid == null || pid.length() == 0) return;
            NotifJob.schedule(MainActivity.this);
            if (Build.VERSION.SDK_INT >= 33 && !notifOn() && !sp.getBoolean("asked", false)) {
                sp.edit().putBoolean("asked", true).apply();
                askNotif();
            }
        }

        /* FCM token for instant notices; "" until Firebase gives one (the page asks again on the next open). */
        @JavascriptInterface
        public String fcm() {
            final android.content.SharedPreferences sp = getSharedPreferences(NotifJob.PREFS, 0);
            if (!App.ready()) return "";
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<String>() {
                    public void onSuccess(String t) {
                        sp.edit().putString("fcm", t).apply();
                    }
                });
            } catch (Exception ignored) {
            }
            return sp.getString("fcm", "");
        }

        @JavascriptInterface
        public void copy(String t) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("صرفلي", t));
        }

        @JavascriptInterface
        public boolean saveImage(String dataUrl) {
            return savePng(dataUrl) != null;
        }

        @JavascriptInterface
        public void shareImage(String dataUrl) {
            final Uri u = savePng(dataUrl);
            if (u == null) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("image/png");
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "مشاركة الأسعار"));
                }
            });
        }

        @JavascriptInterface
        public void shareText(final String t) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, t);
                    startActivity(Intent.createChooser(i, "مشاركة"));
                }
            });
        }

        /* TV/shop screen: hide system bars and keep the screen awake. */
        @JavascriptInterface
        public void board(final boolean on) {
            runOnUiThread(new Runnable() {
                public void run() {
                    View d = getWindow().getDecorView();
                    if (on) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                    } else {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        d.setSystemUiVisibility(0);
                    }
                }
            });
        }
    }
}
