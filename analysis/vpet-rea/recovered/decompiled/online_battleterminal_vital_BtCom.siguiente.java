// online.battleterminal.vital.BtCom#siguiente
// dex: ()V  overload: 0  body_status: available  fell_back: False

    /* JADX INFO: Access modifiers changed from: private */
    public final void siguiente() {
        BluetoothGatt bluetoothGatt;
        BluetoothGattCharacteristic bluetoothGattCharacteristic;
        final byte[] bArrRemoveFirstOrNull;
        if (this.escribiendo || (bluetoothGatt = this.gatt) == null || (bluetoothGattCharacteristic = this.rx) == null || (bArrRemoveFirstOrNull = this.cola.removeFirstOrNull()) == null) {
            return;
        }
        boolean zWriteCharacteristic = true;
        this.escribiendo = true;
        if (Build.VERSION.SDK_INT < 33) {
            bluetoothGattCharacteristic.setValue(bArrRemoveFirstOrNull);
            bluetoothGattCharacteristic.setWriteType(2);
            zWriteCharacteristic = bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
        } else if (bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic, bArrRemoveFirstOrNull, 2) != 0) {
            zWriteCharacteristic = false;
        }
        if (zWriteCharacteristic) {
            return;
        }
        this.escribiendo = false;
        this.main.postDelayed(new Runnable() { // from class: online.battleterminal.vital.BtCom$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                BtCom.siguiente$lambda$5(this.f$0, bArrRemoveFirstOrNull);
            }
        }, 60L);
    }