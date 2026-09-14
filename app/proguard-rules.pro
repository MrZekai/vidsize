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

# --- Added in v0.9.9, when closedTest started building with R8 on ------------
#
# Until v0.9.8 `closedTest` had isMinifyEnabled = false while `release` had it
# true, so every rule above this line had been written but never actually
# exercised on a device. Turning R8 on for the tested artifact is the real fix;
# these are the rules that were missing from the set while nobody was looking.

# Media3 Transformer instantiates its default encoder and muxer factories by
# name. -keepclassmembers above preserves members of classes that survive, but
# does not stop R8 removing an entire class nothing references directly.
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.common.** { *; }

# The whole of Vidsize's own model layer. These are small, and every one of them
# crosses a boundary R8 cannot follow - Compose state, a Parcelable-free service
# Intent, or the history JSON.
#
# The history JSON itself is NOT at risk: PrefsHistoryRepository writes literal
# string keys through JSONObject rather than reflecting over field names, which
# was checked rather than assumed. This keep is for the enums, whose `name` IS
# read and written across the service boundary - CompressionPreset.valueOf() in
# CompressionService would throw on an obfuscated name.
-keep class com.vidsize.compressor.model.** { *; }

# Kotlin coroutines' internal service loader and the debug agent probe.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
