-keep class com.tavi.v2ex.data.** { *; }
-dontwarn org.jspecify.**
-dontwarn okhttp3.internal.**

# ---- 1.0.0 生产构建 ----
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# jsoup：可选依赖 re2j 不存在
-dontwarn com.google.re2j.**
-keep public class org.jsoup.** { public *; }
# Room：实体/DAO 由生成代码引用（库已带 consumer rules，这里兜底）
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
# Kyant backdrop / shapes：运行时着色器字符串，无反射；仅防告警
-dontwarn com.kyant.**
# 发布版剥离 android.util.Log 调用
-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }
