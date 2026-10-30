package com.senda.app.core

object UsageKey {

    private const val SEPARATOR = "::item::"
    private const val LIMIT_SEPARATOR = "::limit::"

    fun forLimit(groupId: String, ruleId: String): String = "$groupId$LIMIT_SEPARATOR$ruleId"
    fun scopeKey(usageKey: String): String = usageKey.substringBefore(SEPARATOR)

    fun forTarget(groupId: String, targetIdentifier: String): String =
        "$groupId$SEPARATOR$targetIdentifier"

    fun baseGroupId(usageKey: String): String =
        scopeKey(usageKey).substringBefore(LIMIT_SEPARATOR)

    fun targetIdentifier(usageKey: String): String? =
        usageKey.substringAfter(SEPARATOR, "").takeIf { it.isNotBlank() }
}
