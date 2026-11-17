// online.battleterminal.vital.BtCom#siguiente$lambda$5
// dex: (Lonline/battleterminal/vital/BtCom;[B)V  overload: 0

    /* JADX INFO: Access modifiers changed from: private */
    public static final void siguiente$lambda$5(BtCom this$0, byte[] b) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(b, "$b");
        this$0.cola.addFirst(b);
        this$0.siguiente();
    }