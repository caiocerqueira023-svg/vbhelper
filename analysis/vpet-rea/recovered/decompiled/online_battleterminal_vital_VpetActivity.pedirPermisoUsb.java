// online.battleterminal.vital.VpetActivity#pedirPermisoUsb
// dex: (Landroid/hardware/usb/UsbDevice;)V  overload: 0  body_status: available  fell_back: False

    public final void pedirPermisoUsb(UsbDevice d) {
        Intrinsics.checkNotNullParameter(d, "d");
        if (!this.receptor) {
            this.receptor = true;
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(this.permisoUsb, new IntentFilter(this.ACCION_USB), 4);
            } else {
                registerReceiver(this.permisoUsb, new IntentFilter(this.ACCION_USB));
            }
        }
        PendingIntent broadcast = PendingIntent.getBroadcast(this, 0, new Intent(this.ACCION_USB).setPackage(getPackageName()), 167772160);
        Object systemService = getSystemService("usb");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.hardware.usb.UsbManager");
        ((UsbManager) systemService).requestPermission(d, broadcast);
    }