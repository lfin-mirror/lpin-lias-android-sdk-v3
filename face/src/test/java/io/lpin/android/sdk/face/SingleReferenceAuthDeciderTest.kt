package io.lpin.android.sdk.face

import org.junit.Assert.assertEquals
import org.junit.Test

class SingleReferenceAuthDeciderTest {
    @Test
    fun `returns continue when no recognition yet`() {
        val decision = SingleReferenceAuthDecider.decide(
            hasRecognitions = false,
            isCurrentUser = false,
            similarity = null,
            threshold = 0.7f,
            isLive = false,
        )

        assertEquals(SingleReferenceAuthDecider.Decision.CONTINUE, decision)
    }

    @Test
    fun `returns fail when another user is recognized`() {
        val decision = SingleReferenceAuthDecider.decide(
            hasRecognitions = true,
            isCurrentUser = false,
            similarity = null,
            threshold = 0.7f,
            isLive = true,
        )

        assertEquals(SingleReferenceAuthDecider.Decision.FAIL, decision)
    }

    @Test
    fun `returns fail when similarity is below threshold`() {
        val decision = SingleReferenceAuthDecider.decide(
            hasRecognitions = true,
            isCurrentUser = true,
            similarity = 0.69f,
            threshold = 0.7f,
            isLive = true,
        )

        assertEquals(SingleReferenceAuthDecider.Decision.FAIL, decision)
    }

    @Test
    fun `returns require liveness when similarity passes but liveness fails`() {
        val decision = SingleReferenceAuthDecider.decide(
            hasRecognitions = true,
            isCurrentUser = true,
            similarity = 0.8f,
            threshold = 0.7f,
            isLive = false,
        )

        assertEquals(SingleReferenceAuthDecider.Decision.REQUIRE_LIVENESS, decision)
    }

    @Test
    fun `returns success when similarity passes and liveness passes`() {
        val decision = SingleReferenceAuthDecider.decide(
            hasRecognitions = true,
            isCurrentUser = true,
            similarity = 0.8f,
            threshold = 0.7f,
            isLive = true,
        )

        assertEquals(SingleReferenceAuthDecider.Decision.SUCCESS, decision)
    }
}
