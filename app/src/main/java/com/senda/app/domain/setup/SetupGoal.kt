package com.senda.app.domain.setup

/** Suggested values only: no group is saved until the user reviews its apps and rules. */
enum class SetupGoal(val suggestedGroupName: String, val dailyMinutes: Int, val sessionMinutes: Int, val cooldownMinutes: Int) {
    REDUCE_DISTRACTIONS("Redes sociais", 30, 5, 10),
    STUDY("Estudos", 20, 3, 15),
    EVENING_REST("Descanso", 45, 5, 15)
}
