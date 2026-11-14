# Speedbump keeps its AccessibilityService and its entry points intact.
-keep class com.speedbump.service.accessibility.SpeedbumpService { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.service.quicksettings.TileService { *; }

# Room + Hilt generated code
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.speedbump.**$$serializer { *; }
-keepclassmembers class com.speedbump.** { *** Companion; }
-keepclasseswithmembers class com.speedbump.** { kotlinx.serialization.KSerializer serializer(...); }
