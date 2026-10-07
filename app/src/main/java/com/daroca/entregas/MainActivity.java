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

    private final Handler handler =
            new Handler(Looper.getMainLooper());

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

                if ("ifood".equals(platform)
                        && orderCode.length() == 8) {

                    Toast.makeText(
                            MainActivity.this,
                            "Código recebido: " + orderCode,
                            Toast.LENGTH_SHORT
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

        if (data != null
                && "daroca".equalsIgnoreCase(data.getScheme())) {

            String host = data.getHost();

            if (host != null) {
                platform = host.toLowerCase();
            }

            if (!data.getPathSegments().isEmpty()) {

                orderCode = digitsOnly(
                        data.getPathSegments().get(0)
                );
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

    /*
     * O site do iFood possui 8 campos separados.
     *
     * Exemplo:
     *
     * order-number-input-0
     * order-number-input-1
     * order-number-input-2
     * ...
     * order-number-input-7
     *
     * Vamos esperar esses campos aparecerem
     * e colocar um número em cada campo.
     */

    private void iniciarTentativas(final String code) {

        // tenta durante aproximadamente 10 segundos

        for (int i = 0; i <= 20; i++) {

            final int tentativa = i;

            handler.postDelayed(
                    () -> preencherCamposIfood(
                            code,
                            tentativa
                    ),
                    i * 500L
            );
        }
    }

    private void preencherCamposIfood(
            String code,
            int tentativa
    ) {

        if (code == null || code.length() != 8) {
            return;
        }

        String safeCode =
                code.replace("\\", "")
                        .replace("'", "");

        String javascript =

                "(function(){" +

                "var codigo='" + safeCode + "';" +

                "var campos=document.querySelectorAll(" +
                "'[data-testid^=\"order-number-input-\"]'" +
                ");" +

                "if(campos.length < 8){" +
                "return 'AGUARDANDO:' + campos.length;" +
                "}" +

                "for(var i=0;i<8;i++){" +

                "var campo=campos[i];" +
                "var numero=codigo.charAt(i);" +

                "try{" +

                "campo.focus();" +

                /*
                 * Primeiro tentamos alterar o value
                 * usando o setter nativo do input.
                 * Isso é importante para páginas React.
                 */

                "var setter=" +
                "Object.getOwnPropertyDescriptor(" +
                "window.HTMLInputElement.prototype," +
                "'value').set;" +

                "setter.call(campo,numero);" +

                /*
                 * Avisamos o React/iFood que
                 * o conteúdo realmente mudou.
                 */

                "campo.dispatchEvent(" +
                "new Event('input',{" +
                "bubbles:true" +
                "})" +
                ");" +

                "campo.dispatchEvent(" +
                "new Event('change',{" +
                "bubbles:true" +
                "})" +
                ");" +

                "campo.dispatchEvent(" +
                "new KeyboardEvent('keyup',{" +
                "bubbles:true," +
                "key:numero" +
                "})" +
                ");" +

                "}catch(e){" +

                "campo.value=numero;" +

                "campo.dispatchEvent(" +
                "new Event('input',{" +
                "bubbles:true" +
                "})" +
                ");" +

                "}" +

                "}" +

                "campos[7].focus();" +

                "return 'PREENCHIDO:' + campos.length;" +

                "})();";

        webView.evaluateJavascript(
                javascript,

                result -> {

                    if (result != null
                            && result.contains("PREENCHIDO")) {

                        // Mostra apenas uma vez.
                        if (tentativa == 0) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Código enviado aos 8 campos!",
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

                "<p>" +
                "Esta plataforma ainda não foi configurada." +
                "</p>" +

                "<p>" +
                "A estrutura está preparada para " +
                "receber a 99Food futuramente." +
                "</p>" +

                "</body>" +

                "</html>",

                "text/html",
                "UTF-8",
                null
        );
    }
}
