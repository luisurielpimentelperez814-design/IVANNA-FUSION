# IVANNA-FUSION TRASCENDENTAL
# Reglas ProGuard/R8 personalizadas.
# Agrega aquí reglas -keep para Vosk, JNI nativo, etc. según sea necesario.

# Mantener clases nativas (JNI) sin renombrar
-keepclasseswithmembernames class * {
    native <methods>;
}
