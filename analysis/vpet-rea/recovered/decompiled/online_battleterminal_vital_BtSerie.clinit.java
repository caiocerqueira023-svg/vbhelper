// online.battleterminal.vital.BtSerie#<clinit>
// dex: ()V  overload: 0

.method static constructor <clinit>()V
    .registers 2

    new-instance v0, Lonline/battleterminal/vital/BtSerie$Companion;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Lonline/battleterminal/vital/BtSerie$Companion;-><init>(Lkotlin/jvm/internal/DefaultConstructorMarker;)V

    sput-object v0, Lonline/battleterminal/vital/BtSerie;->Companion:Lonline/battleterminal/vital/BtSerie$Companion;

    .line 321
    const-string v0, "00001101-0000-1000-8000-00805f9b34fb"

    invoke-static {v0}, Ljava/util/UUID;->fromString(Ljava/lang/String;)Ljava/util/UUID;

    move-result-object v0

    const-string v1, "fromString(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lonline/battleterminal/vital/BtSerie;->SPP:Ljava/util/UUID;

    return-void
.end method