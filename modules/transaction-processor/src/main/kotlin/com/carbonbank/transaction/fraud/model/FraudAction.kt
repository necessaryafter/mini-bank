package com.carbonbank.transaction.fraud.model

/**
 * What account-service should do with a transfer given its risk band. The
 * processor decides these; account-service carries them out. Note MANUAL_REVIEW
 * and REQUIRE_STEP_UP_AUTH mean *hold* — neither capture nor fail yet — which is
 * a third outcome beyond the APPROVE/BLOCK the current status contract expects.
 */
enum class FraudAction {
    APPROVE,
    EMIT_MONITORING_EVENT,
    MANUAL_REVIEW,
    REQUIRE_STEP_UP_AUTH,
    BLOCK,
    FREEZE_BALANCE,
    EMIT_ALERT,
}
