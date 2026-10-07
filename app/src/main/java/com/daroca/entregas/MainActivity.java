package com.daroca.entregas;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;

    private String platform = "ifood";
    private String orderCode = "";

    private static final String IFOOD_URL =
            "https://confirmacao-entrega-propria.ifood.com.br/numero-pedido";

    private static final String LINK_HOST =
            "stunning-hamster-387b55.netlify.app";

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

                if ("ifood".equals(platform) && !orderCode.isEmpty()) {
                    injectIfoodCode(orderCode);
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

        Uri data = intent.getData();

        platform = "ifood";
        orderCode = "";

        if (data != null) {

            String scheme = data.getScheme();

            /*
             * FORMATO ANTIGO
             *
             * daroca://ifood/19840113
             */
            if ("daroca".equalsIgnoreCase(scheme)) {

                String host = data.getHost();

                if (host != null) {
                    platform = host.toLowerCase();
                }

                if (!data.getPathSegments().isEmpty()) {
                    orderCode =
                            digitsOnly(data.getPathSegments().get(0));
                }
            }

            /*
             * NOVO FORMATO HTTPS
             *
             * https://stunning-hamster-387b55.netlify.app/ifood/19840113
             *
             * Futuramente:
             *
             * https://stunning-hamster-387b55.netlify.app/99/XXXXXXXX
             */
            else if ("https".equalsIgnoreCase(scheme)
                    && LINK_HOST.equalsIgnoreCase(data.getHost())) {

                if (data.getPathSegments().size() >= 2) {

                    platform =
                            data.getPathSegments().get(0).toLowerCase();

                    orderCode =
                            digitsOnly(data.getPathSegments().get(1));
                }
            }
        }

        /*
         * Abriu pelo ícone:
         * abre normalmente a confirmação do iFood.
         */
        if ("ifood".equals(platform)) {

            webView.loadUrl(IFOOD_URL);

        } else {

            showUnsupportedPlatform();
        }
    }

    private String digitsOnly(String text) {

        if (text == null) {
            return "";
        }

        return text.replaceAll("\\D", "");
    }

    /*
     * Coloca automaticamente os 8 dígitos
     * no campo da página do iFood.
     */
    private void injectIfoodCode(String code) {

        String safe = code.replace("'", "");

        String js =
                "(function(){" +

                "var code='" + safe + "';" +

                "function fill(){" +

                "var els=[].slice.call(document.querySelectorAll('input'));" +

                "var el=els.find(function(e){" +
                "return e.type==='text'||" +
                "e.type==='tel'||" +
                "e.type==='number'||" +
                "!e.type;" +
                "});" +

                "if(!el)return false;" +

                "var setter=" +
                "Object.getOwnPropertyDescriptor(" +
                "window.HTMLInputElement.prototype,'value').set;" +

                "setter.call(el,code);" +

                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));" +

                "el.focus();" +

                "return true;" +
                "}" +

                "if(!fill()){" +

                "var n=0;" +

                "var t=setInterval(function(){" +

                "n++;" +

                "if(fill()||n>30){" +
                "clearInterval(t);" +
                "}" +

                "},250);" +

                "}" +

                "})();";

        webView.evaluateJavascript(js, null);
    }

    /*
     * Por enquanto só o iFood está ativo.
     * Aqui entraremos com a 99Food depois.
     */
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

                "<p>A integração da 99Food será adicionada " +
                "em uma atualização futura.</p>" +

                "</body>" +
                "</html>",

                "text/html",
                "UTF-8",
                null
        );
    }
}
