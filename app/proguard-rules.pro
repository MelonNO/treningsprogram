-keep class com.migul.treningsprogram.data.api.** { *; }
-keep class com.migul.treningsprogram.data.repository.** { *; }
-keep class com.migul.treningsprogram.data.preferences.** { *; }
-keep class com.migul.treningsprogram.domain.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep all fields with @SerializedName so Gson can map JSON keys after obfuscation
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
