// online.battleterminal.vital.AcomUsb#enviar
// dex: (Ljava/lang/String;)V  overload: 0  body_status: available  fell_back: False

    @Override // online.battleterminal.vital.VpetLink
    public void enviar(final String t) {
        Intrinsics.checkNotNullParameter(t, "t");
        this.hilo.execute(new Runnable() { // from class: online.battleterminal.vital.AcomUsb$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                AcomUsb.enviar$lambda$8(this.f$0, t);
            }
        });
    }