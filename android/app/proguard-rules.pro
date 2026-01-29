# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes used with Firebase
-keepclassmembers class com.scout.routeplanner.data.model.** {
    *;
}

# Keep Room entities
-keep class com.scout.routeplanner.data.local.entity.** { *; }

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**
