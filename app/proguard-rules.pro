-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep,allowoptimization class com.tamerslab.battle.** { *; }
-keep,allowoptimization class com.tamerslab.chat.** { *; }
-keep,allowoptimization class com.tamerslab.data.database.** { *; }
-keep,allowoptimization class com.tamerslab.farm.map.** { *; }
-keep,allowoptimization class com.tamerslab.domain.identity.** { *; }
-keep,allowoptimization class com.tamerslab.domain.reactions.** { *; }
-keep,allowoptimization class com.tamerslab.security.** { *; }
-keep,allowoptimization class com.tamerslab.settings.data.** { *; }
-keep,allowoptimization class com.tamerslab.species.** { *; }
-keep,allowoptimization class com.tamerslab.transfer.data.** { *; }
-keep,allowoptimization class com.tamerslab.world.ecosystem.** { *; }

# Gson-serialized models whose field names are part of a persisted/network
# contract: game-save-core JSON is stored in Room and the PVP DTOs cross the
# network, so R8 must not rename their fields.
-keep,allowoptimization class com.tamerslab.gamesave.** { *; }
-keep,allowoptimization class com.tamerslab.dtos.** { *; }

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

-keep class com.tamerslab.vitalwear.protos.** { *; }
-keep class com.tamerslab.security.proto.** { *; }

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface * {
    @retrofit2.http.* <methods>;
}

-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(...);
}

-keep class org.koin.** { *; }
-keepclassmembers class **$$serializer {
    *;
}

-dontwarn javax.annotation.**
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
