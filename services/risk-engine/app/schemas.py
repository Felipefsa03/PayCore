from pydantic import BaseModel, Field


class RiskEvaluationRequest(BaseModel):
    payment_id: str = Field(min_length=1, max_length=120)
    account_id: str = Field(min_length=1, max_length=120)
    amount_cents: int = Field(ge=0)
    currency: str = Field(min_length=3, max_length=3)
    account_age_days: int = Field(ge=0)
    transactions_last_24h: int = Field(ge=0)
    country_mismatch: bool = False
    device_trusted: bool = True
    rule_version: str = "1.0.0"


class RiskReason(BaseModel):
    code: str
    message: str
    weight: int


class RiskEvaluationResponse(BaseModel):
    payment_id: str
    decision: str
    score: int
    reasons: list[RiskReason]
    rule_version: str
    deterministic: bool = True
