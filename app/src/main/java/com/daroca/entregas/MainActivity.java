
package com.daroca.entregas;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest cameraRequest;

    private static final int CAMERA_PERMISSION = 100;

    private static final String HOME_URL =
        "https://appassets.androidplatform.net/assets/index.html";

    private WebViewAssetLoader assetLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                new WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build();

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request) {

                return assetLoader.shouldInterceptRequest(
                    request.getUrl()
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                    PermissionRequest request) {

                runOnUiThread(() -> {

                    boolean solicitaCamera = false;

                    for (String resource : request.getResources()) {

                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(resource)) {

                            solicitaCamera = true;
                        }
                    }

                    // Autoriza somente a câmera da nossa tela.
                    if (!solicitaCamera ||
                        !HOME_URL.equals(
                            request.getOrigin().toString()
                                .replaceAll("/$", "") + "/index.html"
                        )) {

                        // A origem normalmente termina em /,
                        // por isso validamos também pelo host.
                        if (!solicitaCamera ||
                            !"appassets.androidplatform.net".equals(
                                request.getOrigin().getHost()
                            ) ||
                            !"https".equals(
                                request.getOrigin().getScheme()
                            )) {

                            request.deny();
                            return;
                        }
                    }

                    cameraRequest = request;

                    if (Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED) {

                        requestPermissions(
                            new String[]{
                                Manifest.permission.CAMERA
                            },
                            CAMERA_PERMISSION
                        );

                    } else {
                        liberarCamera();
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(
                    PermissionRequest request) {

                if (cameraRequest == request) {
                    cameraRequest = null;
                }
            }
        });

        webView.loadUrl(HOME_URL);
    }

    private void liberarCamera() {

        if (cameraRequest != null) {

            cameraRequest.grant(
                new String[]{
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE
                }
            );

            cameraRequest = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        );

        if (requestCode == CAMERA_PERMISSION) {

            if (grantResults.length > 0 &&
                grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {

                liberarCamera();

            } else {

                if (cameraRequest != null) {
                    cameraRequest.deny();
                    cameraRequest = null;
                }

                Toast.makeText(
                    this,
                    "Permissão da câmera negada",
                    Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    public void onBackPressed() {

        String url = webView.getUrl();

        if (url != null && !url.equals(HOME_URL)) {

            webView.loadUrl(HOME_URL);

        } else {

            webView.evaluateJavascript(
                "if(typeof voltarInicio === 'function')" +
                "{voltarInicio();}",
                null
            );
        }
    }

    @Override
    protected void onDestroy() {

        if (cameraRequest != null) {
            cameraRequest.deny();
            cameraRequest = null;
        }

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
