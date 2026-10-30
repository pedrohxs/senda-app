package com.senda.app.service

import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.domain.policy.InterventionTarget
import org.junit.Assert.*
import org.junit.Test

class ChallengeRequestStoreTest {
    private val challenge = InterventionDecision.Challenge(
        target = InterventionTarget("test.app", "test.app", "Test", false),
        groupId = "group", groupName = "Group", interventionType = InterventionType.BREATHING,
        frictionSeconds = 10, sessionLimitMinutes = 5, cooldownMinutes = 10,
        countMode = SessionCountMode.ACTIVE_SCREEN_TIME, alternativeAppPackage = null, usageKey = "group"
    )

    @Test fun repeatedAccessibilityEventsPreservePendingCompletion() {
        val store = ChallengeRequestStore()
        val request = store.begin(challenge)
        assertTrue(store.submit(request.id))
        assertEquals(request.id, store.begin(challenge).id)
        assertEquals(ChallengeRequestStore.Status.SUBMITTING, store.request.value?.status)
        assertFalse(store.submit(request.id))
        assertTrue(store.accept(request.id))
        assertFalse(store.accept(request.id))
    }

    @Test fun leavingDuringSubmissionCannotGrantAccessLater() {
        val store = ChallengeRequestStore()
        val request = store.begin(challenge)
        store.submit(request.id)
        store.clear(request.id)
        assertFalse(store.accept(request.id))
        val reopened = store.begin(challenge)
        assertNotEquals(request.id, reopened.id)
        assertEquals(ChallengeRequestStore.Status.ACTIVE, reopened.status)
    }

    @Test fun oldActivityCannotClearOrCompleteReplacementChallenge() {
        val store = ChallengeRequestStore()
        val old = store.begin(challenge)
        store.submit(old.id)
        val replacement = store.replace(challenge.copy(frictionSeconds = 30))
        assertNull(store.clear(old.id))
        assertFalse(store.accept(old.id))
        assertFalse(store.submit(old.id))
        assertEquals(replacement, store.request.value)
    }

    @Test fun restoredProcessWithoutRequestCannotGrantAccess() {
        val old = ChallengeRequestStore().begin(challenge)
        val restored = ChallengeRequestStore()
        assertFalse(restored.submit(old.id))
        assertFalse(restored.accept(old.id))
        assertNull(restored.request.value)
    }

    @Test fun cosmeticChangesPreserveChallengeProgress() {
        val store = ChallengeRequestStore()
        val first = store.begin(challenge)
        assertEquals(first.id, store.begin(challenge.copy(groupName = "Renamed", sessionLimitMinutes = 15)).id)
        assertEquals(first.id, store.begin(challenge.copy(frictionSeconds = 60)).id)
        assertNotEquals(first.id, store.replace(challenge.copy(frictionSeconds = 60)).id)
    }

    @Test fun challengeMustBeSubmittedBeforeAcceptance() {
        val store = ChallengeRequestStore()
        val request = store.begin(challenge)
        assertFalse(store.accept(request.id))
        assertEquals(ChallengeRequestStore.Status.ACTIVE, store.request.value?.status)
    }
}
