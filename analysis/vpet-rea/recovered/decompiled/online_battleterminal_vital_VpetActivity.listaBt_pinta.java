// online.battleterminal.vital.VpetActivity#listaBt$pinta
// dex: (Ljava/util/ArrayList;Ljava/util/ArrayList;Landroid/widget/ArrayAdapter;)V  overload: 0  body_status: available  fell_back: False

    private static final void listaBt$pinta(ArrayList<String> arrayList, ArrayList<Disp> arrayList2, ArrayAdapter<String> arrayAdapter) {
        arrayList.clear();
        final Comparator comparator = new Comparator() { // from class: online.battleterminal.vital.VpetActivity$listaBt$pinta$$inlined$compareByDescending$1
            /* JADX WARN: Multi-variable type inference failed */
            @Override // java.util.Comparator
            public final int compare(T t, T t2) {
                VpetActivity.Disp disp = (VpetActivity.Disp) t2;
                VpetActivity.Disp disp2 = (VpetActivity.Disp) t;
                return ComparisonsKt.compareValues(Boolean.valueOf(disp.getUart() || new Regex("btcom|a-?com|dmcomm|hc-0|esp32|digi", RegexOption.IGNORE_CASE).containsMatchIn(disp.getNombre())), Boolean.valueOf(disp2.getUart() || new Regex("btcom|a-?com|dmcomm|hc-0|esp32|digi", RegexOption.IGNORE_CASE).containsMatchIn(disp2.getNombre())));
            }
        };
        CollectionsKt.sortWith(arrayList2, new Comparator() { // from class: online.battleterminal.vital.VpetActivity$listaBt$pinta$$inlined$thenByDescending$1
            /* JADX WARN: Multi-variable type inference failed */
            @Override // java.util.Comparator
            public final int compare(T t, T t2) {
                int iCompare = comparator.compare(t, t2);
                return iCompare != 0 ? iCompare : ComparisonsKt.compareValues(Boolean.valueOf(((VpetActivity.Disp) t2).getVinculado()), Boolean.valueOf(((VpetActivity.Disp) t).getVinculado()));
            }
        });
        for (Disp disp : arrayList2) {
            String str = disp.getVinculado() ? "ðŸ”— " : "ðŸ“¶ ";
            String nombre = disp.getNombre();
            if (nombre.length() == 0) {
                nombre = disp.getD().getAddress();
            }
            arrayList.add(str + ((Object) nombre) + "\n   " + Idioma.INSTANCE.t(listaBt$tipo(disp)) + (disp.getVinculado() ? " Â· " + Idioma.INSTANCE.t("vinculado") : BuildConfig.FLAVOR));
        }
        arrayAdapter.notifyDataSetChanged();
    }