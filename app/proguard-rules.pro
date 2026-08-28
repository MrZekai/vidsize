# Vidsize R8 keep rules.
#
# The Google Mobile Ads SDK and Media3 ship their own consumer rules, so this
# file only guards the two places where Vidsize itself relies on reflection or
# on resources R8 cannot see a direct reference to.

# The Native Advanced layout is inflated by name and its asset views are handed
# back to the SDK by field assignment. If resource shrinking ever decides the
# layout is unused, the result sheet's ad silently disappears in release builds
# while working perfectly in debug.
-keep public class com.google.android.gms.ads.nativead.NativeAdView { *; }
-keep public class com.google.android.gms.ads.nativead.MediaView { *; }
-keep class com.google.android.gms.ads.nativead.** { *; }

# Media3 Transformer resolves effects, encoders and muxers reflectively in a few
# code paths. Keeping members is cheap and removes a whole class of
# release-only failures that never reproduce on a debug build.
-keepclassmembers class androidx.media3.** { *; }

# UMP consent forms are driven from a WebView bridge.
-keep class com.google.android.ump.** { *; }

-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
