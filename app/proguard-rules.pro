# Keep main classes
-keep class com.phenix.wirelessadb.** { *; }

# Conscrypt (TLS 1.3 for ADB Pairing)
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**

# JSch (SSH tunneling)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Shizuku AIDL
-keep class com.phenix.wirelessadb.IShellService { *; }
-keep class com.phenix.wirelessadb.IShellService$* { *; }
-keep class com.phenix.wirelessadb.shell.ShellUserService { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# SLF4J
-dontwarn org.slf4j.**

# Kotlinx serialization
-dontwarn kotlinx.serialization.**

# Tink (via androidx.security:security-crypto) references compile-only annotations
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
