# 忽略缺失类导致的编译警告和错误
-ignorewarnings
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# 基础配置
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 核心逻辑：除了 XcXorEncryption 之外，保留所有类不被混淆、不被优化
-keep class !com.xcheng.xclogger.filemanager.XcXorEncryption, ** { *; }
-keepclassmembers class !com.xcheng.xclogger.filemanager.XcXorEncryption, ** { *; }

# 保留 Android 系统核心组件
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留序列化与资源引用逻辑
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keep class * implements java.io.Serializable { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留第三方库
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep class com.google.android.material.** { *; }
-keep interface com.google.android.material.** { *; }

# 对混淆目标进行扁平化处理
-repackageclasses com.xcheng.xclogger.filemanager