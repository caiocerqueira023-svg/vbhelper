// online.battleterminal.vital.BtSerie#recibir
// dex: (Ljava/lang/String;)V  overload: 0  body_status: available  fell_back: False

    private final void recibir(String t) {
        synchronized (this.buf) {
            this.buf.append(t);
            StringBuilder sb = this.buf;
            String str = "\n";
            while (true) {
                int iIndexOf = sb.indexOf(str);
                if (iIndexOf >= 0) {
                    String strSubstring = this.buf.substring(0, iIndexOf);
                    Intrinsics.checkNotNullExpressionValue(strSubstring, "substring(...)");
                    String strTrim = StringsKt.trim(strSubstring, '\r', ' ');
                    this.buf.delete(0, iIndexOf + 1);
                    if (strTrim.length() > 0) {
                        this.a.linea(strTrim);
                    }
                    sb = this.buf;
                    str = "\n";
                } else {
                    Unit unit = Unit.INSTANCE;
                }
            }
        }
    }