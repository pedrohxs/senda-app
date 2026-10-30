package com.senda.app.core

object FocoPackages {

    private val sensitivePackages = setOf(
        "com.nu.production",
        "com.itau",
        "com.itau.empresas",
        "com.bradesco",
        "com.bradesco.next",
        "com.santander.app",
        "com.santander.empresas",
        "br.com.bb.android",
        "br.com.gabba.Caixa",
        "br.com.intermedium",
        "com.c6bank.app",
        "com.picpay",
        "com.mercadopago.wallet",
        "com.pagseguro.app",
        "br.com.neon",
        "com.btg.pactual",
        "com.xp.inc",
        "com.bancointer",
        "com.banco.pan",
        "com.banrisul",
        "com.brb.android",
        "br.gov.meugovbr",
        "br.gov.serpro.carteiradetrabalho",
        "br.gov.serpro.ctpsdigital",
        "br.gov.dataprev.meuinss",
        "com.google.android.apps.authenticator2",
        "com.microsoft.authenticator",
        "com.azure.authenticator",
        "com.authy.authy",
        "com.lastpass.lpandroid",
        "com.onelogin.onelogin",
        "com.okta.android.auth",
        "com.duosecurity.duomobile",
        "com.rsa.securidapp",
        "com.bitwarden",
        "com.x8bit.bitwarden",
        "com.onepassword",
        "com.agilebits.onepassword",
        "com.dashlane",
        "com.keepersecurity.passwordmanager",
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay"
    )

    private val sensitivePrefixes = listOf(
        "br.gov.",
        "com.itau",
        "com.bradesco",
        "com.santander",
        "com.nu.",
        "com.c6bank",
        "com.picpay",
        "com.mercadopago",
        "com.pagseguro",
        "br.com.bb",
        "br.com.intermedium",
        "br.com.neon",
        "com.btg",
        "com.xp",
        "com.banco",
        "com.sicredi",
        "com.sicoob",
        "com.google.android.apps.authenticator",
        "com.microsoft.authenticator",
        "com.azure.authenticator",
        "com.bitwarden",
        "com.onepassword",
        "com.lastpass",
        "com.dashlane",
        "com.keepersecurity",
        "com.okta",
        "com.onelogin",
        "com.authy",
        "com.duosecurity",
        "com.rsa.securid"
    )

    val browsers = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "org.cromite.cromite",
        "org.torproject.torbrowser",
        "com.ecosia.android"
    )

    fun isSensitive(packageName: String): Boolean =
        packageName in sensitivePackages || sensitivePrefixes.any { prefix -> packageName.startsWith(prefix) }

    fun isBrowser(packageName: String): Boolean = packageName in browsers
}
