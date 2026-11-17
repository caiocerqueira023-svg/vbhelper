// online.battleterminal.vital.VpetActivity#conectarBt$lambda$24$lambda$23
// dex: (Lonline/battleterminal/vital/VpetActivity;Lonline/battleterminal/vital/VpetActivity$Disp;)V  overload: 0  body_status: available  fell_back: False

    /* JADX INFO: Access modifiers changed from: private */
    public static final void conectarBt$lambda$24$lambda$23(VpetActivity this$0, Disp x) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(x, "$x");
        this$0.linea("[serie no respondiÃ³: pruebo BLE]");
        this$0.link = new BtCom(this$0, x.getD());
    }