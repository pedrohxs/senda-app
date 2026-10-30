# Regras de ofuscação específicas do app Senda.

# Metadados necessários para Room, Kotlin e reflexão leve.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Os enums são persistidos no Room pelo nome da constante
# (InterventionType.valueOf, SessionCountMode.valueOf). Renomear quebraria
# os dados já gravados no aparelho.
-keep enum com.senda.app.data.local.entity.** { *; }
-keepclassmembers enum com.senda.app.data.local.entity.** { *; }

# Entidades e conversores são instanciados pelo Room.
-keep class com.senda.app.data.local.entity.** { *; }
-keep class com.senda.app.data.local.SendaConverters { *; }

# WorkManager instancia o Worker pelo nome da classe.
-keep class com.senda.app.service.SendaHealthWorker { *; }

# kotlinx.serialization: o backup usa serializadores gerados. O código chama
# `serializer()` direto, então os nomes dos campos do arquivo JSON continuam
# corretos; manter os companheiros evita quebra em qualquer caminho reflexivo.
-keepclassmembers class com.senda.app.domain.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.senda.app.domain.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
