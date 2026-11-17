// online.battleterminal.vital.VpetActivity#onCreate
// dex: (Landroid/os/Bundle;)V  overload: 0  body_status: available  fell_back: False

    @Override // android.app.Activity
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        VpetActivity vpetActivity = this;
        Sonido.INSTANCE.iniciar(vpetActivity);
        Sonido.INSTANCE.pausar();
        Idioma.INSTANCE.iniciar(vpetActivity);
        escucharEnchufe();
        getWindow().addFlags(128);
        getWindow().setStatusBarColor(Color.parseColor("#06101c"));
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#06101c")));
        if ((getApplicationInfo().flags & 2) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        WebView webView = new WebView(vpetActivity);
        webView.setOverScrollMode(2);
        webView.setBackgroundColor(Color.parseColor("#06101c"));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.setWebViewClient(new WebViewClient() { // from class: online.battleterminal.vital.VpetActivity$onCreate$1$1
            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                Intrinsics.checkNotNullParameter(v, "v");
                Intrinsics.checkNotNullParameter(url, "url");
                return !StringsKt.startsWith$default(url, "file:///android_asset/", false, 2, (Object) null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Puente(), "Android");
        this.web = webView;
        setContentView(webView);
        WebView webView2 = this.web;
        if (webView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("web");
            webView2 = null;
        }
        String stringExtra = getIntent().getStringExtra("pagina");
        if (stringExtra == null) {
            stringExtra = "inicio.html";
        }
        webView2.loadUrl("file:///android_asset/vpet/" + stringExtra);
    }