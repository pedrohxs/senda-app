package com.senda.app.service

import com.senda.app.domain.policy.InterventionDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Main-thread handoff. Lost process state never grants access to a target. */
class ChallengeRequestStore {
    enum class Status { ACTIVE, SUBMITTING, ACCEPTED }

    data class Request(
        val id: String,
        val challenge: InterventionDecision.Challenge,
        val status: Status = Status.ACTIVE
    )

    private val mutableRequest = MutableStateFlow<Request?>(null)
    val request = mutableRequest.asStateFlow()

    fun begin(challenge: InterventionDecision.Challenge): Request {
        request.value?.takeIf { it.challenge.target.packageName == challenge.target.packageName }
            ?.let { return it }
        return replace(challenge)
    }

    /** Only explicit policy revalidation may replace an in-progress challenge. */
    fun replace(challenge: InterventionDecision.Challenge): Request {
        return Request(UUID.randomUUID().toString(), challenge).also { mutableRequest.value = it }
    }

    fun submit(id: String): Boolean {
        val current = request.value ?: return false
        if (current.id != id || current.status != Status.ACTIVE) return false
        mutableRequest.value = current.copy(status = Status.SUBMITTING)
        return true
    }

    fun accept(id: String): Boolean {
        val current = request.value ?: return false
        if (current.id != id || current.status != Status.SUBMITTING) return false
        mutableRequest.value = current.copy(status = Status.ACCEPTED)
        return true
    }

    fun clear(id: String? = request.value?.id): Request? {
        val current = request.value ?: return null
        if (current.id != id) return null
        mutableRequest.value = null
        return current
    }
}

object ChallengeIntervention {
    val store = ChallengeRequestStore()
}
