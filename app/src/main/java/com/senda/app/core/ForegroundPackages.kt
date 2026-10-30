package com.senda.app.core

import android.content.Intent
import android.content.pm.PackageManager

/**
 * Diferencia apps do usuário de janelas de sistema que roubam o foco.
 *
 * A aba de notificações, painéis de borda e diálogos do sistema assumem o foco
 * como qualquer app. Se tratarmos esses pacotes como "app em primeiro plano",
 * o Senda entende que o usuário saiu do app monitorado e derruba a sobreposição.
 * Cada fabricante usa pacotes diferentes para essas janelas, então a regra
 * genérica é: só conta como app do usuário o pacote que tem intenção de launcher.
 */
object ForegroundPackages {

    private val cache = HashMap<String, Boolean>()
    private var homePackage: String? = null
    private var homeResolved = false

    fun isUserApp(packageManager: PackageManager, packageName: String): Boolean {
        cache[packageName]?.let { return it }

        val launchable = runCatching {
            packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(true)

        cache[packageName] = launchable
        return launchable
    }

    /**
     * O launcher não tem "intenção de launcher" (só CATEGORY_HOME), então sem
     * esta checagem ele seria confundido com uma janela de sistema. E não é:
     * quando o usuário vai para a Home ou abre a visão geral, ele saiu do app
     * monitorado e o bloqueio precisa sair da frente.
     */
    fun isHome(packageManager: PackageManager, packageName: String): Boolean {
        if (!homeResolved) {
            homePackage = runCatching {
                packageManager.resolveActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.packageName
            }.getOrNull()
            homeResolved = true
        }
        return packageName == homePackage
    }
}
