# The JNI functions are looked up by name, so the class that declares them must keep its `native`
# members. The default optimize rules already do this; the explicit rule documents the dependency.
-keepclasseswithmembernames class dev.cniekirk.wikidroid.core.seedmap.NativeSeedMap {
    native <methods>;
}
