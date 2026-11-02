# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.kgcheckin.app.**$$serializer { *; }
-keepclassmembers class com.kgcheckin.app.** { *** Companion; }
-keepclasseswithmembers class com.kgcheckin.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# ZXing（二维码生成）
-keep class com.google.zxing.** { *; }