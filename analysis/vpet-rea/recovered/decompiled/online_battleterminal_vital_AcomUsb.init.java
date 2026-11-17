// online.battleterminal.vital.AcomUsb#<init>
// dex: (Lonline/battleterminal/vital/VpetActivity;)V  overload: 0

    public AcomUsb(VpetActivity a) throws JSONException {
        Intrinsics.checkNotNullParameter(a, "a");
        this.a = a;
        this.buf = new StringBuilder();
        this.hilo = Executors.newSingleThreadExecutor();
        abrir();
    }