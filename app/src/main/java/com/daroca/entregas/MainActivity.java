
package com.daroca.entregas;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest cameraRequest;
    private TextRecognizer recognizer;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private static final int CAMERA_REQUEST_CODE = 100;

    private static final String HOME_URL =
            "https://appassets.androidplatform.net/assets/index.html";

    private static final String IFOOD_URL =
            "https://confirmacao-entrega-propria.ifood.com.br/numero-pedido";

    private static final String FOOD99_URL =
            "https://food-b-h5.99app.com/pt-BR/v2/confirmation-entrega";

    private String plataforma = "";
    private String orderCode = "";
    private boolean preenchido = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);

        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );

        WebViewAssetLoader assetLoader =
                new WebViewAssetLoader.Builder()
                        .addPathHandler(
                                "/assets/",
                                new WebViewAssetLoader.AssetsPathHandler(this)
                        )
                        .build();

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "DaRocaAndroid"
        );

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(
                        request.getUrl()
                );
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (orderCode.length() != 8) return;

                if ("ifood".equals(plataforma)
                        && url != null
                        && url.startsWith(
                        "https://confirmacao-entrega-propria.ifood.com.br/"
                )) {
                    iniciarTentativasPreenchimento(orderCode);
                }

                if ("99".equals(plataforma)
                        && url != null
                        && url.startsWith(
                        "https://food-b-h5.99app.com/"
                )) {
                    iniciarTentativasPreenchimento(orderCode);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {

                    if (!"https".equals(
                            request.getOrigin().getScheme())
                            || !"appassets.androidplatform.net".equals(
                            request.getOrigin().getHost())) {
                        request.deny();
                        return;
                    }

                    boolean querCamera = false;

                    for (String recurso : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(recurso)) {
                            querCamera = true;
                        } else {
                            request.deny();
                            return;
                        }
                    }

                    if (!querCamera) {
                        request.deny();
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= 23
                            && checkSelfPermission(Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {

                        cameraRequest = request;
                        requestPermissions(
                                new String[]{Manifest.permission.CAMERA},
                                CAMERA_REQUEST_CODE
                        );
                    } else {
                        request.grant(
                                new String[]{
                                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                }
                        );
                    }
                });
            }
        });

        webView.loadUrl(HOME_URL);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == CAMERA_REQUEST_CODE
                && cameraRequest != null) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

                cameraRequest.grant(
                        new String[]{
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                        }
                );
            } else {
                cameraRequest.deny();
            }

            cameraRequest = null;
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void reconhecerImagem(String base64) {
            runOnUiThread(() -> {
                if (!estaNaTelaInicial()) return;
                processarImagem(base64);
            });
        }

        @JavascriptInterface
        public void abrirConfirmacao(
                String tipo,
                String codigo
        ) {
            runOnUiThread(() -> {

                if (!estaNaTelaInicial()) return;

                String numero = digitsOnly(codigo);

                if (numero.length() != 8) return;

                if (!"ifood".equals(tipo)
                        && !"99".equals(tipo)) {
                    return;
                }

                plataforma = tipo;
                orderCode = numero;
                preenchido = false;

                handler.removeCallbacksAndMessages(null);

                webView.removeJavascriptInterface("DaRocaAndroid");

                if ("ifood".equals(tipo)) {
                    webView.loadUrl(IFOOD_URL);
                } else {
                    webView.loadUrl(FOOD99_URL);
                }
            });
        }
    }

    private boolean estaNaTelaInicial() {
        return webView != null
                && webView.getUrl() != null
                && webView.getUrl().startsWith(HOME_URL);
    }

    private void processarImagem(String base64) {
        try {
            String imagemLimpa = base64.replaceFirst(
                    "^data:image/[^;]+;base64,",
                    ""
            );

            byte[] bytes = Base64.decode(
                    imagemLimpa,
                    Base64.DEFAULT
            );

            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.length
            );

            if (bitmap == null) {
                enviarResultado(
                        "",
                        "Não foi possível decodificar a imagem."
                );
                return;
            }

            InputImage imagem =
                    InputImage.fromBitmap(bitmap, 0);

            recognizer.process(imagem)
                    .addOnSuccessListener(resultado -> {

                        String texto = resultado.getText();
                        String codigo = encontrarLocalizador(texto);

                        if (!codigo.isEmpty()) {
                            enviarResultado(codigo, "");
                        } else {
                            enviarResultado(
                                    "",
                                    texto == null
                                            || texto.trim().isEmpty()
                                            ? "Nenhum texto reconhecido."
                                            : texto
                            );
                        }
                    })
                    .addOnFailureListener(erro ->
                            enviarResultado(
                                    "",
                                    "Erro: " + erro.getMessage()
                            )
                    );

        } catch (Exception erro) {
            enviarResultado(
                    "",
                    "Erro ao processar: " + erro.getMessage()
            );
        }
    }

    private String encontrarLocalizador(String texto) {

        if (texto == null || texto.trim().isEmpty()) {
            return "";
        }

        String normalizado = texto
                .toUpperCase(Locale.ROOT)
                .replace("\r", "\n");

        String[] linhas = normalizado.split("\n");

        // Primeiro: leitura tradicional, preservando
        // o comportamento das comandas impressas.
        String codigo = buscaTradicional(linhas);

        if (!codigo.isEmpty()) {
            return codigo;
        }

        // Segundo: leitura tolerante a erros de OCR
        // na palavra LOCALIZADOR.
        return buscaFlexivel(linhas);
    }

    private String buscaTradicional(String[] linhas) {

        Pattern palavra = Pattern.compile(
                "LOCAL[I1L]ZADOR"
        );

        for (int i = 0; i < linhas.length; i++) {

            Matcher encontrado = palavra.matcher(linhas[i]);

            if (!encontrado.find()) continue;

            String codigo = procurarNumerosProximos(
                    linhas,
                    i,
                    encontrado.end(),
                    false
            );

            if (!codigo.isEmpty()) {
                return codigo;
            }
        }

        return "";
    }

    private String buscaFlexivel(String[] linhas) {

        for (int i = 0; i < linhas.length; i++) {

            String linha = linhas[i];

            // Normaliza confusões comuns de OCR.
            String comparacao = linha
                    .replace('0', 'O')
                    .replace('1', 'I')
                    .replace('5', 'S')
                    .replace('8', 'B')
                    .replaceAll("[^A-Z]", "");

            String alvo = "LOCALIZADOR";

            if (comparacao.contains(alvo)
                    || contemPalavraParecida(comparacao, alvo)) {

                String codigo = procurarNumerosProximos(
                        linhas,
                        i,
                        0,
                        true
                );

                if (!codigo.isEmpty()) {
                    return codigo;
                }
            }
        }

        return "";
    }

    private boolean contemPalavraParecida(
            String texto,
            String alvo
    ) {

        if (texto.length() < alvo.length() - 1) {
            return false;
        }

        int menor = alvo.length() - 1;
        int maior = alvo.length() + 1;

        for (int tamanho = menor;
             tamanho <= maior;
             tamanho++) {

            for (int inicio = 0;
                 inicio + tamanho <= texto.length();
                 inicio++) {

                String trecho = texto.substring(
                        inicio,
                        inicio + tamanho
                );

                if (distanciaEdicao(trecho, alvo) <= 1) {
                    return true;
                }
            }
        }

        return false;
    }

    private int distanciaEdicao(
            String a,
            String b
    ) {

        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {

                int custo =
                        a.charAt(i - 1) == b.charAt(j - 1)
                                ? 0
                                : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1
                        ),
                        dp[i - 1][j - 1] + custo
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    private String procurarNumerosProximos(
            String[] linhas,
            int linhaInicial,
            int posicaoInicial,
            boolean flexivel
    ) {

        List<String> candidatos = new ArrayList<>();

        String primeiraLinha = linhas[linhaInicial];

        if (!flexivel
                && posicaoInicial < primeiraLinha.length()) {

            candidatos.add(
                    primeiraLinha.substring(posicaoInicial)
            );

        } else if (flexivel) {

            // Na leitura flexível, só considera
            // a linha do título se houver separador.
            int separador = primeiraLinha.indexOf(':');

            if (separador >= 0) {
                candidatos.add(
                        primeiraLinha.substring(separador + 1)
                );
            }
        }

        for (int j = linhaInicial + 1;
             j <= linhaInicial + 2
                     && j < linhas.length;
             j++) {

            candidatos.add(linhas[j]);
        }

        StringBuilder acumulado = new StringBuilder();

        for (String trecho : candidatos) {

            String numeros = trecho.replaceAll("\\D", "");

            if (numeros.isEmpty()) continue;

            if (acumulado.length() + numeros.length() > 8) {
                break;
            }

            acumulado.append(numeros);

            if (acumulado.length() == 8) {
                return acumulado.toString();
            }
        }

        return "";
    }

    private void enviarResultado(
            String codigo,
            String diagnostico
    ) {

        runOnUiThread(() -> {

            if (!estaNaTelaInicial()) return;

            String javascript =
                    "window.receberResultadoOCR(" +
                            JSONObject.quote(codigo) +
                            "," +
                            JSONObject.quote(diagnostico) +
                            ");";

            webView.evaluateJavascript(javascript, null);
        });
    }

    private String digitsOnly(String valor) {
        if (valor == null) return "";
        return valor.replaceAll("\\D", "");
    }

    private void iniciarTentativasPreenchimento(
            String codigo
    ) {

        handler.removeCallbacksAndMessages(null);
        preenchido = false;

        for (int i = 0; i <= 20; i++) {

            handler.postDelayed(
                    () -> preencherCodigo(codigo),
                    i * 500L
            );
        }
    }

    private void preencherCodigo(String codigo) {

        if (preenchido
                || webView == null
                || codigo == null
                || codigo.length() != 8) {
            return;
        }

        String seletor;

        if ("ifood".equals(plataforma)) {
            seletor =
                    "[data-testid^=\"order-number-input-\"]";
        } else if ("99".equals(plataforma)) {
            seletor =
                    ".verification-code-input input";
        } else {
            return;
        }

        String javascript =
                "(function(){" +
                "var codigo=" + JSONObject.quote(codigo) + ";" +
                "var campos=document.querySelectorAll(" +
                JSONObject.quote(seletor) + ");" +
                "if(campos.length!==8)return 'AGUARDANDO';" +
                "var setter=Object.getOwnPropertyDescriptor(" +
                "window.HTMLInputElement.prototype,'value').set;" +
                "for(var i=0;i<8;i++){" +
                "setter.call(campos[i],codigo.charAt(i));" +
                "campos[i].dispatchEvent(" +
                "new Event('input',{bubbles:true}));" +
                "campos[i].dispatchEvent(" +
                "new Event('change',{bubbles:true}));" +
                "}" +
                "campos[7].focus();" +
                "return 'PREENCHIDO';" +
                "})();";

        webView.evaluateJavascript(
                javascript,
                resultado -> {

                    if (resultado != null
                            && resultado.contains("PREENCHIDO")) {

                        preenchido = true;

                        Toast.makeText(
                                MainActivity.this,
                                ("ifood".equals(plataforma)
                                        ? "iFood"
                                        : "99Food")
                                        + " preenchido: " + codigo,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    @Override
    public void onBackPressed() {

        handler.removeCallbacksAndMessages(null);

        plataforma = "";
        orderCode = "";
        preenchido = false;

        if (webView != null) {

            webView.addJavascriptInterface(
                    new AndroidBridge(),
                    "DaRocaAndroid"
            );

            webView.loadUrl(HOME_URL);

        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        if (recognizer != null) {
            recognizer.close();
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
