// online.battleterminal.vital.AcomUsb#abrir
// dex: ()V  overload: 0

    private final void abrir() throws JSONException {
        Object systemService = this.a.getSystemService("usb");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.hardware.usb.UsbManager");
        UsbManager usbManager = (UsbManager) systemService;
        ArrayList arrayListFindAllDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        Intrinsics.checkNotNullExpressionValue(arrayListFindAllDrivers, "findAllDrivers(...)");
        if (arrayListFindAllDrivers.isEmpty()) {
            Collection<UsbDevice> collectionValues = usbManager.getDeviceList().values();
            Intrinsics.checkNotNullExpressionValue(collectionValues, "<get-values>(...)");
            Collection<UsbDevice> collection = collectionValues;
            ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(collection, 10));
            Iterator<T> it = collection.iterator();
            while (it.hasNext()) {
                arrayList.add(new CdcAcmSerialDriver((UsbDevice) it.next()));
            }
            ArrayList arrayList2 = new ArrayList();
            for (Object obj : arrayList) {
                List<UsbSerialPort> ports = ((CdcAcmSerialDriver) obj).getPorts();
                Intrinsics.checkNotNullExpressionValue(ports, "getPorts(...)");
                if (!ports.isEmpty()) {
                    arrayList2.add(obj);
                }
            }
            arrayListFindAllDrivers = arrayList2;
        }
        UsbSerialDriver usbSerialDriver = (UsbSerialDriver) CollectionsKt.firstOrNull((List) arrayListFindAllDrivers);
        if (usbSerialDriver == null) {
            this.a.estado(false, "No encuentro el A-Com. ConÃ©ctalo con el cable OTG y vuelve a tocar.");
            return;
        }
        if (!usbManager.hasPermission(usbSerialDriver.getDevice())) {
            this.a.estado(false, "Acepta el aviso para usar el A-Comâ€¦");
            VpetActivity vpetActivity = this.a;
            UsbDevice device = usbSerialDriver.getDevice();
            Intrinsics.checkNotNullExpressionValue(device, "getDevice(...)");
            vpetActivity.pedirPermisoUsb(device);
            return;
        }
        UsbDeviceConnection usbDeviceConnectionOpenDevice = usbManager.openDevice(usbSerialDriver.getDevice());
        if (usbDeviceConnectionOpenDevice == null) {
            this.a.estado(false, "No pude abrir el A-Com.");
            return;
        }
        try {
            UsbSerialPort usbSerialPort = usbSerialDriver.getPorts().get(0);
            usbSerialPort.open(usbDeviceConnectionOpenDevice);
            usbSerialPort.setParameters(9600, 8, 1, 0);
            try {
                usbSerialPort.setDTR(true);
                usbSerialPort.setRTS(true);
            } catch (Exception unused) {
            }
            this.port = usbSerialPort;
            SerialInputOutputManager serialInputOutputManager = new SerialInputOutputManager(usbSerialPort, new SerialInputOutputManager.Listener() { // from class: online.battleterminal.vital.AcomUsb.abrir.3
                @Override // com.hoho.android.usbserial.util.SerialInputOutputManager.Listener
                public void onNewData(byte[] data) {
                    Intrinsics.checkNotNullParameter(data, "data");
                    AcomUsb.this.recibir(new String(data, Charsets.ISO_8859_1));
                }

                @Override // com.hoho.android.usbserial.util.SerialInputOutputManager.Listener
                public void onRunError(Exception e) throws JSONException {
                    Intrinsics.checkNotNullParameter(e, "e");
                    if (AcomUsb.this.port != null) {
                        AcomUsb.this.a.estado(false, "Se desconectÃ³ el A-Com.");
                    }
                }
            });
            serialInputOutputManager.start();
            this.io = serialInputOutputManager;
            VpetActivity vpetActivity2 = this.a;
            String simpleName = usbSerialDriver.getClass().getSimpleName();
            Intrinsics.checkNotNullExpressionValue(simpleName, "getSimpleName(...)");
            vpetActivity2.linea("[A-Com: " + StringsKt.replace$default(simpleName, "SerialDriver", BuildConfig.FLAVOR, false, 4, (Object) null) + " Â· 9600 baudios]");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { // from class: online.battleterminal.vital.AcomUsb$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() throws JSONException {
                    AcomUsb.abrir$lambda$5(this.f$0);
                }
            }, usbSerialDriver instanceof CdcAcmSerialDriver ? 350L : 1900L);
        } catch (Exception e) {
            VpetActivity vpetActivity3 = this.a;
            String message = e.getMessage();
            if (message == null) {
                message = BuildConfig.FLAVOR;
            }
            vpetActivity3.estado(false, "Error al abrir el A-Com: " + message);
            cerrar();
        }
    }