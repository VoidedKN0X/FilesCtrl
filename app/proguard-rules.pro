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

# Archive libraries: commons-compress finds compressors and archivers by name and probes optional ones
# (Zstandard, Brotli, xz) by class; zstd-jni and its JNI glue are looked up by name from native code.
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keep class org.brotli.dec.** { *; }
-keep class net.lingala.zip4j.** { *; }
-keep class com.github.junrar.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.junrar.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.commons.codec.**
