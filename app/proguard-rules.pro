# Native methods are resolved by JNI symbol names; keep the bridge intact under R8.
-keep class com.nova.engine.NativeEngine { *; }
-keep class com.nova.engine.ProductionRuntime { *; }
-keepclassmembers class * {
    native <methods>;
}
