// online.battleterminal.vital.VpetActivity#listaBt$tipo
// dex: (Lonline/battleterminal/vital/VpetActivity$Disp;)Ljava/lang/String;  overload: 0  body_status: available  fell_back: False

    private static final String listaBt$tipo(Disp disp) {
        if (disp.getUart()) {
            return "BTCom (BLE)";
        }
        if (disp.getD().getType() == 1) {
            return "Bluetooth clÃ¡sico";
        }
        if (disp.getD().getType() == 2 || disp.getBle()) {
            return "BLE";
        }
        return disp.getD().getType() == 3 ? "ClÃ¡sico + BLE" : "Bluetooth";
    }