package com.daroca.entregas;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;
    private String platform = "ifood";
    private String orderCode = "";

    private static final String IFOOD_URL =
            "https://confirmacao-entrega-propria.ifood.com.br/numero-pedido";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if ("ifood".equals(platform) && !orderCode.isEmpty()) {

                    Toast.makeText(
                            MainActivity.this,
                            "Código recebido: " + orderCode,
                            Toast.LENGTH_LONG
                    ).show();

                    iniciarTentativas(orderCode);
                }
            }
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {

        platform = "ifood";
        orderCode = "";

        Uri data = intent.getData();

        if (data != null &&
                "daroca".equalsIgnoreCase(data.getScheme())) {

            String host = data.getHost();

            if (host != null) {
                platform = host.toLowerCase();
            }

            if (!data.getPathSegments().isEmpty()) {
                orderCode =
                        digitsOnly(data.getPathSegments().get(0));
            }
        }

        if ("ifood".equals(platform)) {

            webView.loadUrl(IFOOD_URL);

        } else {

            showUnsupportedPlatform();
        }
    }

    private String digitsOnly(String value) {

        if (value == null) {
            return "";
        }

        return value.replaceAll("\\D", "");
    }

    private void iniciarTentativas(final String code) {

        // O site pode criar o campo alguns segundos
        // depois de onPageFinished.
        for (int i = 0; i <= 20; i++) {

            final int tentativa = i;

            handler.postDelayed(
                    () -> injectIfoodCode(code, tentativa),
                    i * 500L
            );
        }
    }

    private void injectIfoodCode(
            String code,
            int tentativa
    ) {

        String safe =
                code.replace("\\", "")
                    .replace("'", "");

        String js =
                "(function(){" +

                "var code='" + safe + "';" +

                "function setValue(el){" +

                "try{" +

                "el.focus();" +

                "var proto=Object.getPrototypeOf(el);" +
                "var desc=Object.getOwnPropertyDescriptor(proto,'value');" +

                "if(desc && desc.set){" +
                "desc.set.call(el,code);" +
                "}else{" +
                "el.value=code;" +
                "}" +

                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                "el.dispatchEvent(new Event('keyup',{bubbles:true}));" +
                "el.dispatchEvent(new Event('blur',{bubbles:true}));" +

                "return true;" +

                "}catch(e){" +
                "return false;" +
                "}" +

                "}" +

                "var campos=document.querySelectorAll(" +
                "'input, textarea, [contenteditable=\"true\"]'" +
                ");" +

                "for(var i=0;i<campos.length;i++){" +

                "var el=campos[i];" +

                "if(setValue(el)){" +
                "return 'OK';" +
                "}" +

                "}" +

                "return 'NAO_ENCONTROU';" +

                "})();";

        webView.evaluateJavascript(
                js,
                result -> {

                    if ("\"OK\"".equals(result)) {

                        if (tentativa == 0) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Código preenchido!",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                }
        );
    }

    private void showUnsupportedPlatform() {

        webView.loadDataWithBaseURL(
                null,

                "<html>" +
                "<body style='" +
                "font-family:sans-serif;" +
                "background:#f3e7d3;" +
                "padding:32px'>" +

                "<h2 style='color:#6b4f35'>" +
                "Da Roça Entregas" +
                "</h2>" +

                "<p>Esta plataforma ainda não foi configurada.</p>" +

                "<p>A estrutura está preparada para " +
                "receber a 99Food futuramente.</p>" +

                "</body>" +
                "</html>",

                "text/html",
                "UTF-8",
                null
        );
    }
}
