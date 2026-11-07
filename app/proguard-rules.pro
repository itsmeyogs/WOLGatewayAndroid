# Keep classes in their original packages.
# Prevents Kotlin package-private superclass access errors on ART.
-dontrepackage

# Preserve metadata used by Kotlin, Hilt, Room, and Kotlin Serialization.
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

# Ktor's JVM debugger detector references optional java.management APIs.
# Android does not provide these classes; the detector is not used there.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
