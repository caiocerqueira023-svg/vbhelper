// online.battleterminal.vital.BtCom#parar
// dex: ()V  overload: 0  body_status: available  fell_back: False

    /* JADX INFO: Access modifiers changed from: private */
    public final void parar() {
        BluetoothLeScanner bluetoothLeScanner;
        if (this.buscando) {
            this.buscando = false;
            try {
                BluetoothAdapter adapter = this.bm.getAdapter();
                if (adapter == null || (bluetoothLeScanner = adapter.getBluetoothLeScanner()) == null) {
                    return;
                }
                bluetoothLeScanner.stopScan(this.scan);
            } catch (Exception unused) {
            }
        }
    }