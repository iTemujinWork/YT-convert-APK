# The youtubedl-android classes are not used (we drive the binaries ourselves); their
# dependencies were excluded on purpose, so R8 must not fail on the missing references.
-dontwarn com.yausername.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.compress.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.itemujinwork.dalma.**$$serializer { *; }
-keepclassmembers class io.github.itemujinwork.dalma.** { *** Companion; }
-keepclasseswithmembers class io.github.itemujinwork.dalma.** { kotlinx.serialization.KSerializer serializer(...); }

# Readable crash lines in the hidden about card
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
