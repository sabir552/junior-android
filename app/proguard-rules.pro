# Junior - ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# OkHttp / WebSocket
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# org.json
-keep class org.json.** { *; }

# Android Architecture Components
-keep class androidx.lifecycle.** { *; }

# Junior app classes
-keep class com.junior.assistant.** { *; }
