# FilesCtrl R8 rules. Libraries (Coil, coroutines, Compose) bring their own.

# Keep file and line numbers so crash reports point at real code (decode names with mapping.txt from the CI run).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JNI: the native library looks these up by name.
-keep class com.vknxworks.filesctrl.nfs.NfsNative { *; }

# Network protocol libraries: they load ciphers, algorithms and message types by name.
-keep class org.bouncycastle.** { *; }
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.** { *; }
-keep class com.rapid7.client.dcerpc.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class org.apache.commons.net.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.**
-dontwarn com.rapid7.**
-dontwarn net.engio.mbassy.**
-dontwarn org.slf4j.**
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.**
-dontwarn com.jcraft.jzlib.**
-dontwarn org.apache.commons.net.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
