// online.battleterminal.vital.BtCom#enviar
// dex: (Ljava/lang/String;)V  overload: 0  body_status: available  fell_back: False

    @Override // online.battleterminal.vital.VpetLink
    public void enviar(final String t) {
        Intrinsics.checkNotNullParameter(t, "t");
        this.main.post(new Runnable() { // from class: online.battleterminal.vital.BtCom$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                BtCom.enviar$lambda$3(t, this);
            }
        });
    }