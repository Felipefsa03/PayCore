from app.schemas import RiskEvaluationRequest, RiskReason


RULE_VERSIONS = {
    "1.0.0": {
        "high_amount_threshold_cents": 100_000,
        "high_amount_weight": 40,
        "high_frequency_threshold": 5,
        "high_frequency_weight": 25,
        "new_account_days": 7,
        "new_account_weight": 20,
        "country_mismatch_weight": 30,
        "untrusted_device_weight": 15,
        "review_threshold": 35,
        "reject_threshold": 70,
    }
}


def evaluate(request: RiskEvaluationRequest) -> tuple[str, int, list[RiskReason]]:
    if request.rule_version not in RULE_VERSIONS:
        raise ValueError(f"unknown rule version: {request.rule_version}")

    rules = RULE_VERSIONS[request.rule_version]
    reasons: list[RiskReason] = []
    score = 0

    if request.amount_cents >= rules["high_amount_threshold_cents"]:
        score += rules["high_amount_weight"]
        reasons.append(
            RiskReason(
                code="HIGH_AMOUNT",
                message="amount exceeds the configured high-amount threshold",
                weight=rules["high_amount_weight"],
            )
        )

    if request.transactions_last_24h >= rules["high_frequency_threshold"]:
        score += rules["high_frequency_weight"]
        reasons.append(
            RiskReason(
                code="HIGH_FREQUENCY",
                message="transaction frequency exceeds the configured threshold",
                weight=rules["high_frequency_weight"],
            )
        )

    if request.account_age_days < rules["new_account_days"]:
        score += rules["new_account_weight"]
        reasons.append(
            RiskReason(
                code="NEW_ACCOUNT",
                message="account age is below the configured minimum",
                weight=rules["new_account_weight"],
            )
        )

    if request.country_mismatch:
        score += rules["country_mismatch_weight"]
        reasons.append(
            RiskReason(
                code="COUNTRY_MISMATCH",
                message="transaction country differs from the account profile",
                weight=rules["country_mismatch_weight"],
            )
        )

    if not request.device_trusted:
        score += rules["untrusted_device_weight"]
        reasons.append(
            RiskReason(
                code="UNTRUSTED_DEVICE",
                message="device is not in the trusted device set",
                weight=rules["untrusted_device_weight"],
            )
        )

    if score >= rules["reject_threshold"]:
        decision = "REJECT"
    elif score >= rules["review_threshold"]:
        decision = "REVIEW"
    else:
        decision = "APPROVE"

    return decision, score, reasons
