// online.battleterminal.vital.VpetActivity#abrir
// dex: (Ljava/lang/String;)V  overload: 0  body_status: available  fell_back: False

    /* JADX INFO: Access modifiers changed from: private */
    public final void abrir(String modo) {
        VpetLink vpetLink = this.link;
        if (vpetLink != null) {
            vpetLink.cerrar();
        }
        this.link = null;
        if (!Intrinsics.areEqual(modo, "bt")) {
            this.link = new AcomUsb(this);
        } else if (permisosBt()) {
            listaBt();
        } else {
            this.pendiente = "bt";
        }
    }