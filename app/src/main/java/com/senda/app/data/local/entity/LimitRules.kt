package com.senda.app.data.local.entity

/** A complete snapshot: custom windows do not change when the group's defaults change. */
data class LimitRules(
    val dailyLimitMinutes: Int = 30,
    val sessionLimitMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val maxDailyOpens: Int = 15,
    val sharedLimitAcrossApps: Boolean = true,
    val sessionCountMode: SessionCountMode = SessionCountMode.ACTIVE_SCREEN_TIME,
    val interventionType: InterventionType = InterventionType.BREATHING,
    val breathingSeconds: Int = 10,
    val mathDifficulty: MathDifficulty = MathDifficulty.EASY,
    val mathEquationCount: Int = 3,
    val progressiveFrictionEnabled: Boolean = true,
    val progressiveSecondsPerOpen: Int = 5,
    val alternativeAppPackage: String? = null
) {
    val isValid: Boolean get() = dailyLimitMinutes in 0..1440 && sessionLimitMinutes in 1..240 &&
        cooldownMinutes in 0..1440 && maxDailyOpens in 0..999 && breathingSeconds in 5..600 &&
        mathEquationCount in 3..20 && progressiveSecondsPerOpen in 1..60
    fun applyTo(group: AppGroupEntity) = group.copy(
        dailyLimitMinutes = dailyLimitMinutes, sessionLimitMinutes = sessionLimitMinutes,
        cooldownMinutes = cooldownMinutes, maxDailyOpens = maxDailyOpens,
        sharedLimitAcrossApps = sharedLimitAcrossApps, sessionCountMode = sessionCountMode,
        interventionType = interventionType, breathingSeconds = breathingSeconds,
        mathDifficulty = mathDifficulty, mathEquationCount = mathEquationCount,
        progressiveFrictionEnabled = progressiveFrictionEnabled, progressiveSecondsPerOpen = progressiveSecondsPerOpen,
        alternativeAppPackage = alternativeAppPackage
    )

    companion object {
        fun from(group: AppGroupEntity) = LimitRules(group.dailyLimitMinutes, group.sessionLimitMinutes,
            group.cooldownMinutes, group.maxDailyOpens, group.sharedLimitAcrossApps, group.sessionCountMode,
            group.interventionType, group.breathingSeconds, group.mathDifficulty, group.mathEquationCount,
            group.progressiveFrictionEnabled, group.progressiveSecondsPerOpen, group.alternativeAppPackage)
    }
}
