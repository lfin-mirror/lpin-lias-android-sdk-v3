package io.lpin.android.sdk.face

internal object SingleReferenceAuthDecider {
    enum class Decision {
        CONTINUE,
        FAIL,
        REQUIRE_LIVENESS,
        SUCCESS,
    }

    fun decide(
        hasRecognitions: Boolean,
        isCurrentUser: Boolean,
        similarity: Float?,
        threshold: Float,
        isLive: Boolean,
    ): Decision {
        if (!isCurrentUser) {
            return if (hasRecognitions) Decision.FAIL else Decision.CONTINUE
        }

        val safeSimilarity = similarity ?: return Decision.CONTINUE
        if (safeSimilarity < threshold) {
            return Decision.FAIL
        }

        if (!isLive) {
            return Decision.REQUIRE_LIVENESS
        }

        return Decision.SUCCESS
    }
}
