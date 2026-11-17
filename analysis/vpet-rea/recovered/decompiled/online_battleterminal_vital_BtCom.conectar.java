// online.battleterminal.vital.BtCom#conectar
// dex: (Landroid/bluetooth/BluetoothDevice;)V  overload: 0  body_status: available  fell_back: False

    /* JADX INFO: Access modifiers changed from: private */
    public final void conectar(BluetoothDevice d) {
        this.gatt = d.connectGatt(this.a, false, new AnonymousClass1(), 2);
    }