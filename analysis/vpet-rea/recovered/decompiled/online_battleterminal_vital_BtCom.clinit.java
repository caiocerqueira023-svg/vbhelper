// online.battleterminal.vital.BtCom#<clinit>
// dex: ()V  overload: 0

.method static constructor <clinit>()V
    .registers 2

    new-instance v0, Lonline/battleterminal/vital/BtCom$Companion;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Lonline/battleterminal/vital/BtCom$Companion;-><init>(Lkotlin/jvm/internal/DefaultConstructorMarker;)V

    sput-object v0, Lonline/battleterminal/vital/BtCom;->Companion:Lonline/battleterminal/vital/BtCom$Companion;

    .line 253
    const-string v0, "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

    invoke-static {v0}, Ljava/util/UUID;->fromString(Ljava/lang/String;)Ljava/util/UUID;

    move-result-object v0

    const-string v1, "fromString(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lonline/battleterminal/vital/BtCom;->NUS:Ljava/util/UUID;

    .line 254
    const-string v0, "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

    invoke-static {v0}, Ljava/util/UUID;->fromString(Ljava/lang/String;)Ljava/util/UUID;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lonline/battleterminal/vital/BtCom;->RX:Ljava/util/UUID;

    .line 255
    const-string v0, "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    invoke-static {v0}, Ljava/util/UUID;->fromString(Ljava/lang/String;)Ljava/util/UUID;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lonline/battleterminal/vital/BtCom;->TX:Ljava/util/UUID;

    .line 256
    const-string v0, "00002902-0000-1000-8000-00805f9b34fb"

    invoke-static {v0}, Ljava/util/UUID;->fromString(Ljava/lang/String;)Ljava/util/UUID;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    sput-object v0, Lonline/battleterminal/vital/BtCom;->CCCD:Ljava/util/UUID;

    return-void
.end method