
package com.headshotsensitivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.view.Choreographer;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    private ActivityManager activityManager;

    private volatile int fpsApp = 0;
    private int frameCount = 0;
    private long lastFpsTime = 0;

    private long lastCpuTime = 0;
    private long lastCpuWallTime = 0;
    private double cpuPercent = 0.0;

    private final Choreographer.FrameCallback frameCallback =
            new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCount++;

            if (lastFpsTime == 0) {
                lastFpsTime = frameTimeNanos;
            }

            long elapsed = frameTimeNanos - lastFpsTime;

            if (elapsed >= 1_000_000_000L) {
                fpsApp = (int) Math.round(
                        frameCount * 1_000_000_000.0 / elapsed
                );

                frameCount = 0;
                lastFpsTime = frameTimeNanos;
            }

            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activityManager = (ActivityManager)
                getSystemService(Context.ACTIVITY_SERVICE);

        FrameLayout container = new FrameLayout(this);

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(
                new AndroidDiagnostics(), "AndroidDiagnostics"
        );

        container.addView(
                webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        setContentView(container);

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("file:///android_asset/index.html");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
        }

        lastFpsTime = 0;
        frameCount = 0;

        Choreographer.getInstance().removeFrameCallback(frameCallback);
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onPause() {
        Choreographer.getInstance().removeFrameCallback(frameCallback);

        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidDiagnostics");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private synchronized double getAppCpuPercent() {
        long currentCpuTime = Process.getElapsedCpuTime();
        long currentWallTime = android.os.SystemClock.elapsedRealtime();

        if (lastCpuWallTime == 0) {
            lastCpuTime = currentCpuTime;
            lastCpuWallTime = currentWallTime;
            return 0.0;
        }

        long cpuDelta = currentCpuTime - lastCpuTime;
        long wallDelta = currentWallTime - lastCpuWallTime;

        lastCpuTime = currentCpuTime;
        lastCpuWallTime = currentWallTime;

        if (wallDelta <= 0 || cpuDelta < 0) {
            return 0.0;
        }

        double percent = (cpuDelta * 100.0) / wallDelta;

        return Math.max(0.0, Math.min(percent, 100.0));
    }

    private JSONObject collectDiagnostics() {
        JSONObject result = new JSONObject();

        try {
            ActivityManager.MemoryInfo memoryInfo =
                    new ActivityManager.MemoryInfo();

            activityManager.getMemoryInfo(memoryInfo);

            Debug.MemoryInfo processMemory = new Debug.MemoryInfo();
            Debug.getMemoryInfo(processMemory);

            long totalRamMB = memoryInfo.totalMem / (1024L * 1024L);
            long availableRamMB = memoryInfo.availMem / (1024L * 1024L);
            long appMemoryMB = processMemory.getTotalPss() / 1024L;

            cpuPercent = getAppCpuPercent();

            result.put("totalRamMB", totalRamMB);
            result.put("availableRamMB", availableRamMB);
            result.put("usedRamMB", totalRamMB - availableRamMB);
            result.put("appMemoryMB", appMemoryMB);
            result.put("cpuPercent", Math.round(cpuPercent * 10.0) / 10.0);
            result.put("fpsApp", fpsApp);
            result.put("diagnosticsScope", "app_process");

        } catch (Exception e) {
            try {
                result.put("error", e.getMessage() == null
                        ? "Falha ao coletar diagnóstico"
                        : e.getMessage());
            } catch (Exception ignored) {
                // Mantém o retorno disponível.
            }
        }

        return result;
    }

    public class AndroidDiagnostics {

        @JavascriptInterface
        public String getDiagnostics() {
            return collectDiagnostics().toString();
        }
    }
}
