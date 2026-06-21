-keep class com.migul.treningsprogram.data.api.model.** { *; }
-keep class com.migul.treningsprogram.data.repository.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep all fields with @SerializedName so Gson can map JSON keys after obfuscation
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
