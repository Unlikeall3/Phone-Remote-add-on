package com.unlikeall.phoneremote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final String ADDR_ACTION = "com.unlikeall.phoneremote.ADDR";
    private static final int REQ_FILE = 7;

    private SharedPreferences sp;
    private WebView web;
    private String base = "";
    private boolean ready = false;
    private final ArrayList<String> pending = new ArrayList<>();
    private ValueCallback<Uri[]> fileCb;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("pr", MODE_PRIVATE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F1115);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                    ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
            return ins.consumeSystemWindowInsets();
        });
        web = new WebView(this);
        web.setBackgroundColor(0xFF0F1115);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        setupWeb();
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS, "android.permission.ACCESS_LOCAL_NETWORK",
                    "android.permission.NEARBY_WIFI_DEVICES"}, 1);
        }
        base = sp.getString("url", "");
        handle(getIntent());
        if (base.isEmpty()) askAddress(false);
        else web.loadUrl(base);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handle(i);
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setAllowFileAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                Uri bu = Uri.parse(base);
                if (u.getHost() != null && bu.getHost() != null && u.getHost().equals(bu.getHost())
                        && u.getPort() == bu.getPort()) return false;
                if ("http".equals(u.getScheme()) || "https".equals(u.getScheme()) || "magnet".equals(u.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                ready = false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                ready = true;
                ui.postDelayed(() -> {
                    for (String js : new ArrayList<>(pending)) web.evaluateJavascript(js, null);
                    pending.clear();
                }, 700);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Нет связи с приставкой")
                            .setMessage("Не удалось открыть " + base + "\nПроверьте, что телефон и приставка в одной сети.")
                            .setPositiveButton("Повторить", (d, w) -> web.loadUrl(base))
                            .setNegativeButton("Сменить адрес", (d, w) -> askAddress(true))
                            .show();
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton("OK", (d, w) -> r.confirm()).setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, JsResult r) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg)
                        .setPositiveButton("OK", (d, w) -> r.confirm())
                        .setNegativeButton("Отмена", (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String msg, String def, JsPromptResult r) {
                EditText et = new EditText(MainActivity.this);
                et.setText(def == null ? "" : def);
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setView(et)
                        .setPositiveButton("OK", (d, w) -> r.confirm(et.getText().toString()))
                        .setNegativeButton("Отмена", (d, w) -> r.cancel())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            try {
                DownloadManager.Request q = new DownloadManager.Request(Uri.parse(url));
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) q.addRequestHeader("Cookie", ck);
                q.addRequestHeader("User-Agent", ua);
                String fn = URLUtil.guessFileName(url, cd, mime);
                q.setTitle(fn);
                q.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                q.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(q);
                Toast.makeText(this, "Скачивание: " + fn, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось скачать: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void askAddress(boolean cancelable) {
        EditText et = new EditText(this);
        et.setHint("http://192.168.50.196:8090");
        et.setText(base.isEmpty() ? "http://" : base);
        et.setSingleLine(true);
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle("Адрес Phone Remote на приставке")
                .setMessage("IP приставки и порт add-on (по умолчанию 8090)")
                .setView(et)
                .setPositiveButton("OK", (x, w) -> {
                    String u = et.getText().toString().trim();
                    if (!u.startsWith("http")) u = "http://" + u;
                    base = u;
                    sp.edit().putString("url", u).apply();
                    web.loadUrl(u);
                });
        if (cancelable) d.setNegativeButton("Отмена", null);
        else d.setCancelable(false);
        d.show();
    }

    private void run(String js) {
        if (ready) web.evaluateJavascript(js, null);
        else pending.add(js);
    }

    private void playUrl(String url) {
        run("(async()=>{try{await api('/api/play/url',{url:" + JSONObject.quote(url) + "});"
                + "toast('Запущено на ТВ')}catch(e){}})()");
    }

    private void handle(Intent i) {
        if (i == null) return;
        String a = i.getAction();
        if (ADDR_ACTION.equals(a)) {
            askAddress(true);
            return;
        }
        if (!Intent.ACTION_SEND.equals(a) && !Intent.ACTION_SEND_MULTIPLE.equals(a)) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(a)) {
            ArrayList<Uri> l = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        } else {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        }
        if (!uris.isEmpty()) {
            shareFiles(uris);
            return;
        }
        String t = i.getStringExtra(Intent.EXTRA_TEXT);
        if (t != null) {
            Matcher m = Pattern.compile("(https?://\\S+|magnet:\\?\\S+)").matcher(t);
            if (m.find()) playUrl(m.group(1));
            else Toast.makeText(this, "В тексте нет ссылки", Toast.LENGTH_LONG).show();
        }
    }

    private void shareFiles(ArrayList<Uri> uris) {
        try {
            FileServer fs = FileServer.get();
            startForegroundService(new Intent(this, KeepService.class));
            ArrayList<String> urls = new ArrayList<>();
            for (Uri u : uris) {
                String name = "video", mime = getContentResolver().getType(u);
                long size = -1;
                try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME), si = c.getColumnIndex(OpenableColumns.SIZE);
                        if (ni >= 0 && c.getString(ni) != null) name = c.getString(ni);
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                    }
                } catch (Exception ignored) {
                }
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(u, "r");
                if (pfd == null) continue;
                urls.add(fs.add(pfd, name, mime, size));
            }
            if (urls.isEmpty()) {
                Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Отдаю на ТВ: " + urls.size() + " шт. Не закрывайте приложение до конца просмотра", Toast.LENGTH_LONG).show();
            playUrl(urls.get(0));
            for (int k = 1; k < urls.size(); k++) {
                final String qu = urls.get(k);
                run("(async()=>{try{await api('/api/queue/add',{paths:[" + JSONObject.quote(qu) + "]})}catch(e){}})()");
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
