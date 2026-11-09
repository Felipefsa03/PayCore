from fastapi import FastAPI, HTTPException
from app.rules import RULE_VERSIONS, evaluate
from app.schemas import RiskEvaluationRequest, RiskEvaluationResponse

app = FastAPI(title="PayCore Risk Engine", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/rules/versions")
def rule_versions() -> dict[str, list[str]]:
    return {"versions": sorted(RULE_VERSIONS)}


@app.post("/risk/evaluate", response_model=RiskEvaluationResponse)
def risk_evaluate(request: RiskEvaluationRequest) -> RiskEvaluationResponse:
    try:
        decision, score, reasons = evaluate(request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return RiskEvaluationResponse(
        payment_id=request.payment_id,
        decision=decision,
        score=score,
        reasons=reasons,
        rule_version=request.rule_version,
    )
