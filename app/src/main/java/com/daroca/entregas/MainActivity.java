
package com.daroca.entregas;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest cameraRequest;

    private static final int CAMERA_REQUEST_CODE = 100;

    private static final String HOME_URL =
        "https://appassets.androidplatform.net/assets/index.html";

    private TextRecognizer recognizer;

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
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                PermissionRequest request
            ) {

                runOnUiThread(() -> {

                    if (
                        !"https".equals(
                            request.getOrigin().getScheme()
                        ) ||
                        !"appassets.androidplatform.net".equals(
                            request.getOrigin().getHost()
                        )
                    ) {
                        request.deny();
                        return;
                    }

                    boolean querCamera = false;

                    for (String recurso : request.getResources()) {

                        if (
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(recurso)
                        ) {
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

                    if (
                        Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        cameraRequest = request;

                        requestPermissions(
                            new String[]{
                                Manifest.permission.CAMERA
                            },
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

        if (
            requestCode == CAMERA_REQUEST_CODE &&
            cameraRequest != null
        ) {

            if (
                grantResults.length > 0 &&
                grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
            ) {

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

                InputImage imagem = InputImage.fromBitmap(
                    bitmap,
                    0
                );

                recognizer.process(imagem)

                    .addOnSuccessListener(resultado -> {

                        String texto = resultado.getText();

                        String codigo =
                            encontrarLocalizador(texto);

                        if (!codigo.isEmpty()) {

                            enviarResultado(codigo, "");

                        } else {

                            String diagnostico;

                            if (
                                texto == null ||
                                texto.trim().isEmpty()
                            ) {

                                diagnostico =
                                    "O Google ML Kit não reconheceu " +
                                    "nenhum texto nesta imagem.";

                            } else {

                                diagnostico =
                                    "Texto reconhecido pelo ML Kit:\n\n" +
                                    texto;
                            }

                            enviarResultado(
                                "",
                                diagnostico
                            );
                        }
                    })

                    .addOnFailureListener(erro -> {

                        enviarResultado(
                            "",
                            "Erro no reconhecimento: " +
                            erro.getMessage()
                        );
                    });

            } catch (Exception erro) {

                enviarResultado(
                    "",
                    "Erro ao processar imagem: " +
                    erro.getMessage()
                );
            }
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

        Pattern palavraLocalizador = Pattern.compile(
            "LOCAL[I1L]ZADOR"
        );

        Pattern numeros = Pattern.compile(
            "\\d+"
        );

        for (int i = 0; i < linhas.length; i++) {

            Matcher palavra = palavraLocalizador.matcher(
                linhas[i]
            );

            if (!palavra.find()) {
                continue;
            }

            StringBuilder acumulado =
                new StringBuilder();

            // Começa depois da palavra LOCALIZADOR.
            String restante = linhas[i].substring(
                palavra.end()
            );

            juntarNumeros(
                restante,
                acumulado,
                numeros
            );

            if (acumulado.length() == 8) {
                return acumulado.toString();
            }

            // Procura também nas duas linhas seguintes.
            for (
                int j = i + 1;
                j <= i + 2 && j < linhas.length;
                j++
            ) {

                juntarNumeros(
                    linhas[j],
                    acumulado,
                    numeros
                );

                if (acumulado.length() == 8) {
                    return acumulado.toString();
                }

                if (acumulado.length() > 8) {
                    break;
                }
            }
        }

        return "";
    }

    private void juntarNumeros(
        String trecho,
        StringBuilder acumulado,
        Pattern padrao
    ) {

        Matcher matcher = padrao.matcher(trecho);

        while (matcher.find()) {

            String grupo = matcher.group();

            if (
                acumulado.length() +
                grupo.length() > 8
            ) {
                return;
            }

            acumulado.append(grupo);

            if (acumulado.length() == 8) {
                return;
            }
        }
    }

    private void enviarResultado(
        String codigo,
        String diagnostico
    ) {

        runOnUiThread(() -> {

            if (webView == null) {
                return;
            }

            String javascript =
                "window.receberResultadoOCR(" +
                JSONObject.quote(codigo) +
                "," +
                JSONObject.quote(diagnostico) +
                ");";

            webView.evaluateJavascript(
                javascript,
                null
            );
        });
    }

    @Override
    public void onBackPressed() {

        if (webView != null) {

            webView.loadUrl(HOME_URL);

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

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
