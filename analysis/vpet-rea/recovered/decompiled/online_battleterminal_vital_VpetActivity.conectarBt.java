// online.battleterminal.vital.VpetActivity#conectarBt
// dex: (Lonline/battleterminal/vital/VpetActivity$Disp;)V  overload: 0  body_status: available  fell_back: False

    private final void conectarBt(final Disp x) {
        String nombre = x.getNombre();
        if (nombre.length() == 0) {
            nombre = x.getD().getAddress();
        }
        linea("[elegido: " + ((Object) nombre) + " Â· tipo " + x.getD().getType() + (x.getUart() ? " Â· UART BLE" : BuildConfig.FLAVOR) + "]");
        this.link = (x.getUart() || x.getD().getType() == 2 || (x.getBle() && x.getD().getType() == 0)) ? new BtCom(this, x.getD()) : new BtSerie(this, x.getD(), new Function0() { // from class: online.battleterminal.vital.VpetActivity$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                return VpetActivity.conectarBt$lambda$24(x, this);
            }
        });
    }