package com.paycore.financialcore.app.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RiskClient {
  private final boolean enabled;
  private final URI endpoint;
  private final Duration timeout;
  private final ObjectMapper mapper;
  private final HttpClient http;
  private final CircuitBreaker breaker;

  public RiskClient(
      @Value("${paycore.risk.enabled:false}") boolean enabled,
      @Value("${paycore.risk.url:http://127.0.0.1:8000}") String baseUrl,
      @Value("${paycore.risk.timeout-ms:200}") long timeoutMs,
      ObjectMapper mapper) {
    this.enabled = enabled;
    this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/risk/evaluate");
    this.timeout = Duration.ofMillis(timeoutMs);
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.breaker = new CircuitBreaker(3, 5_000);
  }

  public RiskEvaluation evaluate(
      java.util.UUID transferId,
      java.util.UUID accountId,
      long amount,
      String currency) {
    if (!enabled) {
      return new RiskEvaluation("APPROVE", 0);
    }
    if (!breaker.allowRequest()) {
      throw new RiskUnavailableException("risk circuit breaker is open");
    }
    try {
      String body = mapper.writeValueAsString(java.util.Map.of(
          "payment_id", transferId.toString(), "account_id", accountId.toString(),
          "amount_cents", amount, "currency", currency, "account_age_days", 30,
          "transactions_last_24h", 0, "country_mismatch", false, "device_trusted", true,
          "rule_version", "1.0.0"));
      HttpRequest request =
          HttpRequest.newBuilder(endpoint)
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RiskUnavailableException("risk engine returned HTTP " + response.statusCode());
      }
      JsonNode json = mapper.readTree(response.body());
      if (!transferId.toString().equals(json.path("payment_id").asText())
          || !"1.0.0".equals(json.path("rule_version").asText())
          || !java.util.Set.of("APPROVE", "REVIEW", "REJECT").contains(json.path("decision").asText())
          || !json.path("score").isIntegralNumber()) {
        throw new RiskUnavailableException("invalid risk engine response");
      }
      breaker.recordSuccess();
      return new RiskEvaluation(json.path("decision").asText("REVIEW"), json.path("score").asInt(0));
    } catch (RiskUnavailableException exception) {
      breaker.recordFailure();
      throw exception;
    } catch (Exception exception) {
      breaker.recordFailure();
      throw new RiskUnavailableException("risk engine unavailable", exception);
    }
  }
}
