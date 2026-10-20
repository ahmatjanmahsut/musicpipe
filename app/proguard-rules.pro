# MusicPipe proguard rules (minify disabled for now; keep rules for future release builds)
-keep class com.musicpipe.app.data.remote.dto.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
