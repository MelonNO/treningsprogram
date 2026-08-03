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

# Keep Gson-serialized log data model classes (field names must survive R8)
-keep class com.migul.treningsprogram.data.RejectionLog { *; }
-keep class com.migul.treningsprogram.data.RejectionLog$** { *; }
-keep class com.migul.treningsprogram.data.CrashLog { *; }
-keep class com.migul.treningsprogram.data.CrashLog$** { *; }
-keep class com.migul.treningsprogram.data.PromptLog { *; }
-keep class com.migul.treningsprogram.data.PromptLog$** { *; }

# ── Backup portability (2026-08-03) ────────────────────────────────────────────────────────────
# Release builds used to serialize the backup envelope/entities with R8-MINIFIED field names
# (debug and release wrote mutually incompatible JSON; cross-release restore relied on R8
# assigning the same letters). Every backup-reachable class now carries an explicit
# @SerializedName on every serialized field — the annotation supplies the JSON key regardless of
# renaming. These -keepclassmembernames rules are defense-in-depth: if a future field is added
# WITHOUT an annotation, it still serializes under its source name instead of an R8 letter.
# (MinifiedBackupCompat translates the historical minified backups on import.)
-keepclassmembernames class com.migul.treningsprogram.data.backup.** { <fields>; }
-keepclassmembernames class com.migul.treningsprogram.data.db.entity.** { <fields>; }
