// online.battleterminal.vital.VpetActivity#listaBt
// dex: ()V  overload: 0  body_status: available  fell_back: False

    /* JADX WARN: Type inference failed for: r10v0, types: [online.battleterminal.vital.VpetActivity$listaBt$rc$1] */
    private final void listaBt() {
        Object systemService = getSystemService("bluetooth");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.bluetooth.BluetoothManager");
        final BluetoothAdapter adapter = ((BluetoothManager) systemService).getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            estado(false, "Prende el Bluetooth del telÃ©fono y vuelve a tocar.");
            return;
        }
        final ArrayList arrayList = new ArrayList();
        final ArrayList arrayList2 = new ArrayList();
        VpetActivity vpetActivity = this;
        final ArrayAdapter arrayAdapter = new ArrayAdapter(vpetActivity, android.R.layout.simple_list_item_1, arrayList2);
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices != null) {
                for (BluetoothDevice bluetoothDevice : bondedDevices) {
                    Intrinsics.checkNotNull(bluetoothDevice);
                    listaBt$agregar$default(arrayList, arrayList2, arrayAdapter, bluetoothDevice, bluetoothDevice.getName(), false, false, 96, null);
                }
            }
        } catch (Exception unused) {
        }
        final VpetActivity$listaBt$sc$1 vpetActivity$listaBt$sc$1 = new VpetActivity$listaBt$sc$1(this, arrayList, arrayList2, arrayAdapter);
        final ?? r10 = new BroadcastReceiver() { // from class: online.battleterminal.vital.VpetActivity$listaBt$rc$1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context c, Intent i) {
                BluetoothDevice bluetoothDevice2;
                Intrinsics.checkNotNullParameter(c, "c");
                Intrinsics.checkNotNullParameter(i, "i");
                if (Intrinsics.areEqual(i.getAction(), "android.bluetooth.device.action.FOUND") && (bluetoothDevice2 = (BluetoothDevice) i.getParcelableExtra("android.bluetooth.device.extra.DEVICE")) != null) {
                    ArrayList<VpetActivity.Disp> arrayList3 = arrayList;
                    ArrayList<String> arrayList4 = arrayList2;
                    ArrayAdapter<String> arrayAdapter2 = arrayAdapter;
                    String stringExtra = i.getStringExtra("android.bluetooth.device.extra.NAME");
                    if (stringExtra == null) {
                        stringExtra = bluetoothDevice2.getName();
                    }
                    VpetActivity.listaBt$agregar$default(arrayList3, arrayList4, arrayAdapter2, bluetoothDevice2, stringExtra, false, false, 96, null);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver((BroadcastReceiver) r10, new IntentFilter("android.bluetooth.device.action.FOUND"), 2);
        } else {
            registerReceiver((BroadcastReceiver) r10, new IntentFilter("android.bluetooth.device.action.FOUND"));
        }
        try {
            BluetoothLeScanner bluetoothLeScanner = adapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.startScan((List<ScanFilter>) null, new ScanSettings.Builder().setScanMode(2).build(), vpetActivity$listaBt$sc$1);
            }
        } catch (Exception unused2) {
        }
        try {
            adapter.startDiscovery();
        } catch (Exception unused3) {
        }
        final Ref.BooleanRef booleanRef = new Ref.BooleanRef();
        estado(false, "Elige tu BTCom en la lista.");
        AlertDialog alertDialogCreate = new AlertDialog.Builder(vpetActivity, android.R.style.Theme.DeviceDefault.Dialog.Alert).setTitle(Idioma.INSTANCE.t("Elige tu BTCom")).setAdapter(arrayAdapter, new DialogInterface.OnClickListener() { // from class: online.battleterminal.vital.VpetActivity$$ExternalSyntheticLambda4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                VpetActivity.listaBt$lambda$15(arrayList, this, booleanRef, adapter, vpetActivity$listaBt$sc$1, r10, dialogInterface, i);
            }
        }).setNeutralButton(Idioma.INSTANCE.t("Ajustes de Bluetooth"), new DialogInterface.OnClickListener() { // from class: online.battleterminal.vital.VpetActivity$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                VpetActivity.listaBt$lambda$16(this.f$0, booleanRef, adapter, vpetActivity$listaBt$sc$1, r10, dialogInterface, i);
            }
        }).setNegativeButton(Idioma.INSTANCE.t("Cancelar"), new DialogInterface.OnClickListener() { // from class: online.battleterminal.vital.VpetActivity$$ExternalSyntheticLambda6
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                VpetActivity.listaBt$lambda$17(this.f$0, booleanRef, adapter, vpetActivity$listaBt$sc$1, r10, dialogInterface, i);
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() { // from class: online.battleterminal.vital.VpetActivity$$ExternalSyntheticLambda7
            @Override // android.content.DialogInterface.OnCancelListener
            public final void onCancel(DialogInterface dialogInterface) {
                VpetActivity.listaBt$lambda$18(booleanRef, adapter, vpetActivity$listaBt$sc$1, this, r10, dialogInterface);
            }
        }).create();
        alertDialogCreate.setMessage(null);
        alertDialogCreate.show();
        this.main.postDelayed(new Runnable() { // from class: online.battleterminal.vital.VpetActivity$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                VpetActivity.listaBt$lambda$20(booleanRef, adapter, vpetActivity$listaBt$sc$1, this, r10);
            }
        }, 25000L);
        if (arrayList.isEmpty()) {
            arrayList2.add(Idioma.INSTANCE.t("Buscandoâ€¦ (si no aparece, vincÃºlalo en Ajustes de Bluetooth)"));
            arrayAdapter.notifyDataSetChanged();
        }
    }