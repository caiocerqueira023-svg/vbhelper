// online.battleterminal.vital.BtSerie#enviar
// dex: (Ljava/lang/String;)V  overload: 0  body_status: available  fell_back: False

    @Override // online.battleterminal.vital.VpetLink
    public void enviar(final String t) {
        Intrinsics.checkNotNullParameter(t, "t");
        this.hilo.execute(new Runnable() { // from class: online.battleterminal.vital.BtSerie$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                BtSerie.enviar$lambda$7(this.f$0, t);
            }
        });
    }