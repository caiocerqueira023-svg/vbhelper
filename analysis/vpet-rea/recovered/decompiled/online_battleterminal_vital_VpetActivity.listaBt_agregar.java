// online.battleterminal.vital.VpetActivity#listaBt$agregar
// dex: (Ljava/util/ArrayList;Ljava/util/ArrayList;Landroid/widget/ArrayAdapter;Landroid/bluetooth/BluetoothDevice;Ljava/lang/String;ZZ)V  overload: 0

    /* JADX INFO: Access modifiers changed from: private */
    public static final void listaBt$agregar(ArrayList<Disp> arrayList, ArrayList<String> arrayList2, ArrayAdapter<String> arrayAdapter, BluetoothDevice bluetoothDevice, String str, boolean z, boolean z2) {
        Object next;
        Iterator<T> it = arrayList.iterator();
        do {
            if (!it.hasNext()) {
                next = null;
                break;
            }
            next = it.next();
        } while (!Intrinsics.areEqual(((Disp) next).getD().getAddress(), bluetoothDevice.getAddress()));
        Disp disp = (Disp) next;
        boolean z3 = true;
        if (disp == null) {
            if (str == null && (str = bluetoothDevice.getName()) == null) {
                str = BuildConfig.FLAVOR;
            }
            arrayList.add(new Disp(bluetoothDevice, str, bluetoothDevice.getBondState() == 12, z, z2));
        } else {
            String str2 = str;
            if (str2 != null && str2.length() != 0) {
                disp.setNombre(str);
            }
            disp.setBle(disp.getBle() || z);
            if (!disp.getUart() && !z2) {
                z3 = false;
            }
            disp.setUart(z3);
        }
        listaBt$pinta(arrayList2, arrayList, arrayAdapter);
    }