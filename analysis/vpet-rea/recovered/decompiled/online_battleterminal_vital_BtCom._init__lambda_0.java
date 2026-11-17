// online.battleterminal.vital.BtCom#_init_$lambda$0
// dex: (Lonline/battleterminal/vital/BtCom;)V  overload: 0

    /* JADX INFO: Access modifiers changed from: private */
    public static final void _init_$lambda$0(BtCom this$0) throws JSONException {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        if (this$0.buscando) {
            this$0.parar();
            this$0.a.estado(false, "No encontrÃ© el BTCom. Â¿EstÃ¡ prendido y cerca?");
        }
    }