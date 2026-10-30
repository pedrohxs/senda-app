package com.senda.app.core

object UrlMatcher {

    private val hostPattern = Regex(
        pattern = """^(?:[a-z][a-z0-9+.-]*://)?(?:[^@/]+@)?(?:www\.)?([a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+)(?::\d+)?(?:[/?#].*)?$""",
        option = RegexOption.IGNORE_CASE
    )

    fun extractHost(rawValue: String): String? {
        val value = rawValue.trim()
        if (value.isEmpty() || value.contains(' ')) return null

        val match = hostPattern.matchEntire(value) ?: return null
        val host = match.groupValues[1].lowercase().removePrefix("www.")
        return host.takeIf { it.contains('.') }
    }

    fun isBlocked(host: String, blockedDomains: Set<String>): Boolean {
        val normalizedHost = host.lowercase().removePrefix("www.")
        return blockedDomains.any { domain ->
            val normalizedDomain = domain.lowercase().removePrefix("www.")
            normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
        }
    }
}
