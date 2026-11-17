// online.battleterminal.vital.VpetActivity#linea
// dex: (Ljava/lang/String;)Z  overload: 0  body_status: available  fell_back: False

    public final boolean linea(String l) {
        Intrinsics.checkNotNullParameter(l, "l");
        return aWeb("window.vpetLinea&&window.vpetLinea(" + JSONObject.quote(l) + ")");
    }