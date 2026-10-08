
package com.daroca.entregas;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest cameraRequest;

    private static final int CAMERA_REQUEST_CODE = 100;

    private static final String HOME_URL =
        "https://appassets.androidplatform.net/assets/index.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

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

                    if (!"https".equals(
                            request.getOrigin().getScheme()
                        ) ||
                        !"appassets.androidplatform.net".equals(
                            request.getOrigin().getHost()
                        )) {

                        request.deny();
                        return;
                    }

                    boolean wantsCamera = false;

                    for (String resource :
                            request.getResources()) {

                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(resource)) {

                            wantsCamera = true;

                        } else {
                            request.deny();
                            return;
                        }
                    }

                    if (!wantsCamera) {
                        request.deny();
                        return;
                    }

                    if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED) {

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

        if (requestCode == CAMERA_REQUEST_CODE &&
            cameraRequest != null) {

            if (grantResults.length > 0 &&
                grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {

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
                        "Não foi possível ler a imagem."
                    );

                    return;
                }

                InputImage imagem = InputImage.fromBitmap(
                    bitmap,
                    0
                );

                // Google ML Kit
                // Correção do erro da linha 209

                TextRecognizer recognizer =
                    TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS
                    );

                recognizer.process(imagem)
                    .addOnSuccessListener(resultado -> {

                        String texto = resultado.getText();

                        String codigo =
                            encontrarLocalizador(texto);

                        if (!codigo.isEmpty()) {

                            enviarResultado(codigo, "");

                        } else {

                            enviarResultado(
                                "",
                                "Localizador não encontrado. " +
                                "Tente aproximar a câmera."
                            );
                        }

                        recognizer.close();
                    })
                    .addOnFailureListener(erro -> {

                        enviarResultado(
                            "",
                            "Erro ao reconhecer: " +
                            erro.getMessage()
                        );

                        recognizer.close();
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

        if (texto == null) {
            return "";
        }

        String textoMaiusculo =
            texto.toUpperCase(java.util.Locale.ROOT);

        Pattern padrao = Pattern.compile(
            "LOCALIZADOR[\\s:\\-]*([0-9\\s]{8,20})"
        );

        Matcher matcher =
            padrao.matcher(textoMaiusculo);

        if (matcher.find()) {

            String numeros = matcher.group(1)
                .replaceAll("\\D", "");

            if (numeros.length() >= 8) {
                return numeros.substring(0, 8);
            }
        }

        String[] linhas =
            textoMaiusculo.split("\\n");

        for (int i = 0; i < linhas.length; i++) {

            if (linhas[i].contains("LOCALIZADOR")) {

                for (int j = i;
                     j <= i + 2 && j < linhas.length;
                     j++) {

                    Matcher numeros =
                        Pattern.compile("\\d{8}")
                            .matcher(
                                linhas[j].replaceAll(
                                    "\\s",
                                    ""
                                )
                            );

                    if (numeros.find()) {
                        return numeros.group();
                    }
                }
            }
        }

        return "";
    }

    private void enviarResultado(
        String codigo,
        String erro
    ) {

        runOnUiThread(() -> {

            String js =
                "window.receberResultadoOCR(" +
                org.json.JSONObject.quote(codigo) + "," +
                org.json.JSONObject.quote(erro) +
                ");";

            webView.evaluateJavascript(js, null);
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

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
