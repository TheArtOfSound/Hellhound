-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.theartofsound.hellhound.**$$serializer { *; }
-keepclassmembers class com.theartofsound.hellhound.** {
    *** Companion;
}
-keepclasseswithmembers class com.theartofsound.hellhound.** {
    kotlinx.serialization.KSerializer serializer(...);
}
