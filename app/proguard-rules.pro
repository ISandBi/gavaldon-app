# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# No conservar nombres de clases de seguridad más de lo necesario (OWASP M7).
# Eliminar logs en release: ningún dato sensible debe llegar a logcat.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
