// online.battleterminal.vital.VpetActivity#cuadrosDmc
// dex: (Ljava/lang/String;)Ljava/util/List;  overload: 0  body_status: available  fell_back: False

    /* JADX INFO: Access modifiers changed from: private */
    public final List<Integer> cuadrosDmc(String k) {
        List list = VpetKt.dmcLista;
        if (list == null) {
            try {
                String[] list2 = getAssets().list("dmc");
                list = list2 != null ? ArraysKt.toList(list2) : null;
            } catch (Exception unused) {
            }
            if (list == null) {
                list = CollectionsKt.emptyList();
            }
            VpetKt.dmcLista = list;
        }
        ArrayList arrayList = new ArrayList();
        for (Object obj : list) {
            String str = (String) obj;
            if (StringsKt.startsWith$default(str, k + "_", false, 2, (Object) null) && StringsKt.endsWith$default(str, ".png", false, 2, (Object) null)) {
                arrayList.add(obj);
            }
        }
        ArrayList arrayList2 = new ArrayList();
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Integer intOrNull = StringsKt.toIntOrNull(StringsKt.removeSuffix(StringsKt.removePrefix((String) it.next(), (CharSequence) (k + "_")), (CharSequence) ".png"));
            if (intOrNull != null) {
                arrayList2.add(intOrNull);
            }
        }
        return CollectionsKt.sorted(arrayList2);
    }