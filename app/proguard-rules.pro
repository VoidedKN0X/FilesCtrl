# FilesCtrl R8 rules. Libraries (Coil, coroutines, Compose) bring their own.

# Keep file and line numbers so crash reports point at real code (decode names with mapping.txt from the CI run).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JNI: the native library looks these up by name.
-keep class com.vknxworks.filesctrl.nfs.NfsNative { *; }
