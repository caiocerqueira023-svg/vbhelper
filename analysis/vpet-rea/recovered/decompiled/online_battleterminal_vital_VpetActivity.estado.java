// online.battleterminal.vital.VpetActivity#estado
// dex: (ZLjava/lang/String;)Z  overload: 0  body_status: available  fell_back: False

    public final boolean estado(boolean ok, String msg) {
        Intrinsics.checkNotNullParameter(msg, "msg");
        return aWeb("window.vpetEstado&&window.vpetEstado(" + new JSONObject().put("ok", ok).put(NotificationCompat.CATEGORY_MESSAGE, msg) + ")");
    }