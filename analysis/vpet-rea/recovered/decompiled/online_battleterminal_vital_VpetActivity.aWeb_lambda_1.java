// online.battleterminal.vital.VpetActivity#aWeb$lambda$1
// dex: (Lonline/battleterminal/vital/VpetActivity;Ljava/lang/String;)V  overload: 0

    /* JADX INFO: Access modifiers changed from: private */
    public static final void aWeb$lambda$1(VpetActivity this$0, String js) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(js, "$js");
        if (this$0.isFinishing()) {
            return;
        }
        WebView webView = this$0.web;
        if (webView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("web");
            webView = null;
        }
        webView.evaluateJavascript(js, null);
    }