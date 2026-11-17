// online.battleterminal.vital.VpetActivity#escucharEnchufe
// dex: ()V  overload: 0  body_status: available  fell_back: False

    private final void escucharEnchufe() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        intentFilter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.enchufe, intentFilter, 2);
        } else {
            registerReceiver(this.enchufe, intentFilter);
        }
    }