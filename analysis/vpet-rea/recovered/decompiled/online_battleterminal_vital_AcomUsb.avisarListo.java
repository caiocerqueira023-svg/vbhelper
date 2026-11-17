// online.battleterminal.vital.AcomUsb#avisarListo
// dex: ()V  overload: 0  body_status: available  fell_back: False

    private final void avisarListo() {
        if (this.listo || this.port == null) {
            return;
        }
        this.listo = true;
        this.a.estado(true, "A-Com conectado. En tu vpet entra a BATTLE y conecta.");
    }