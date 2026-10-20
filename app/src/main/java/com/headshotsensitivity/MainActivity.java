
package com.headshotsensitivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.view.Choreographer;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;

    private long ultimoCpuMs = -1;
    private long ultimoTempoMs = -1;

    private volatile int fpsApp = -1;
    private long inicioJanelaFps = 0;
    private int quadros = 0;
    private boolean medindoFps = false;

    private final Choreographer.FrameCallback callbackFps =
        new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long tempoFrame) {
                if (!medindoFps) return;

                if (inicioJanelaFps == 0) {
                    inicioJanelaFps = tempoFrame;
                }

                quadros++;

                long decorrido = tempoFrame - inicioJanelaFps;

                if (decorrido >= 1_000_000_000L) {
                    fpsApp = Math.round(
                        quadros * 1_000_000_000.0 / decorrido
                    );

                    quadros = 0;
                    inicioJanelaFps = tempoFrame;
                }

                Choreographer.getInstance()
                    .postFrameCallback(this);
            }
        };

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);

        getWindow().setStatusBarColor(
            android.graphics.Color.rgb(8, 11, 20)
        );

        getWindow().setNavigationBarColor(
            android.graphics.Color.rgb(8, 11, 20)
        );

        webView = new WebView(this);

        WebSettings configuracoes = webView.getSettings();
        configuracoes.setJavaScriptEnabled(true);
        configuracoes.setDomStorageEnabled(true);

        webView.addJavascriptInterface(
            new DiagnosticoAndroid(),
            "AndroidDiagnostics"
        );

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();

        medindoFps = true;
        inicioJanelaFps = 0;
        quadros = 0;

        Choreographer.getInstance()
            .postFrameCallback(callbackFps);
    }

    @Override
    protected void onPause() {
        medindoFps = false;

        Choreographer.getInstance()
            .removeFrameCallback(callbackFps);

        super.onPause();
    }

    public class DiagnosticoAndroid {

        @JavascriptInterface
        public synchronized String getDiagnostics() {
            JSONObject dados = new JSONObject();

            try {
                ActivityManager gerenciador =
                    (ActivityManager) getSystemService(
                        ACTIVITY_SERVICE
                    );

                ActivityManager.MemoryInfo memoria =
                    new ActivityManager.MemoryInfo();

                gerenciador.getMemoryInfo(memoria);

                long total = memoria.totalMem;
                long disponivel = memoria.availMem;
                long usada = Math.max(0, total - disponivel);

                dados.put("ramTotalMb", total / 1048576.0);
                dados.put("ramAvailableMb", disponivel / 1048576.0);
                dados.put("ramUsedMb", usada / 1048576.0);

                dados.put(
                    "ramUsedPercent",
                    total > 0 ? usada * 100.0 / total : 0
                );

                dados.put("lowMemory", memoria.lowMemory);

                Debug.MemoryInfo memoriaApp =
                    new Debug.MemoryInfo();

                Debug.getMemoryInfo(memoriaApp);

                dados.put(
                    "appPssMb",
                    memoriaApp.getTotalPss() / 1024.0
                );

                long cpuAtual = Process.getElapsedCpuTime();
                long tempoAtual = SystemClock.elapsedRealtime();

                int nucleos = Math.max(
                    1,
                    Runtime.getRuntime().availableProcessors()
                );

                dados.put("cpuCores", nucleos);

                double cpuPercentual = -1;

                if (ultimoCpuMs >= 0 && ultimoTempoMs >= 0) {
                    long deltaCpu = cpuAtual - ultimoCpuMs;
                    long deltaTempo = tempoAtual - ultimoTempoMs;

                    if (deltaTempo > 0) {
                        cpuPercentual =
                            (deltaCpu * 100.0) /
                            (deltaTempo * nucleos);

                        cpuPercentual = Math.max(
                            0,
                            Math.min(100, cpuPercentual)
                        );
                    }
                }

                ultimoCpuMs = cpuAtual;
                ultimoTempoMs = tempoAtual;

                dados.put("cpuAppPercent", cpuPercentual);
                dados.put("fpsApp", fpsApp);

            } catch (Exception erro) {
                try {
                    dados.put("erro", erro.getMessage());
                } catch (Exception ignorado) {
                    // Mantém a resposta JSON válida.
                }
            }

            return dados.toString();
        }
    }
}
