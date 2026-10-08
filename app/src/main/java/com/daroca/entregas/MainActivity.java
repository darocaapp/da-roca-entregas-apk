
package com.daroca.entregas;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;
    private PermissionRequest cameraRequest;

    private static final String HOME_URL =
        "file:///android_asset/index.html";

    private static final int CAMERA_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                    PermissionRequest request) {

                runOnUiThread(() -> {

                    boolean cameraRequested = false;

                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(resource)) {
                            cameraRequested = true;
                        }
                    }

                    if (!cameraRequested) {
                        request.deny();
                        return;
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

        if (!webView.getUrl().equals(HOME_URL)) {

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
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
