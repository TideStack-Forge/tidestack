package com.ouroboros.ability.auth.oidc.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.ability.auth.oidc.management.ClientSecrets;
import com.ouroboros.util.Json;

/** Issuer-bound one-attempt credentials; never a platform principal or user authorization. */
public final class DcrCredentials {
  public static final String NAMESPACE = "ouroboros:oauth2:dcr:initial-access";
  private final CacheManager cache;
  private final Supplier<String> issuer;
  public DcrCredentials(CacheManager cache, Supplier<String> issuer) { this.cache = cache; this.issuer = issuer; }
  public boolean ready() { return cache != null && cache.capabilities().atomicUpdates() && issuer.get() != null; }
  public record Issued(String id, String token, long expiresAt) {
    @Override public String toString() { return "Issued[REDACTED]"; }
  }
  public record Claim(String id, String binding) {
    @Override public String toString() { return "Claim[REDACTED]"; }
  }
  public record State(String id, String issuer, String digest, String actor, long expiresAt, String status, String binding) {
    @Override public String toString() { return "State[REDACTED]"; }
  }
  public Issued issue(String actor, Duration ttl) {
    requireReady();
    if (actor == null || actor.isBlank() || ttl.isNegative() || ttl.isZero()) { throw DcrFailure.unavailable(); }
    String id = UUID.randomUUID().toString();
    String token = id + "." + ClientSecrets.generate();
    long expires = System.currentTimeMillis() + ttl.toMillis();
    State state = new State(id, issuer.get(), digest(token), actor, expires, "READY", null);
    if (!cache.putIfAbsent(NAMESPACE, id, Json.toJsonString(state), ttl)) { throw DcrFailure.unavailable(); }
    return new Issued(id, token, expires);
  }
  public State check(String token) {
    requireReady();
    if (token == null || !token.matches("[a-f0-9-]{36}\\.[A-Za-z0-9_-]{43}")) { throw invalid(); }
    State state = state(raw(token.substring(0, 36)));
    if (!state.status().equals("READY") || !constantEquals(state.digest(), digest(token))) { throw invalid(); }
    return state;
  }
  public Claim claim(String token, String request, String client, String registration, String candidate, String revision) {
    State state = check(token);
    String id = state.id();
    String old = raw(id);
    state = state(old);
    if (!state.status().equals("READY") || !constantEquals(state.digest(), digest(token))) { throw invalid(); }
    String binding = binding(request, client, registration, candidate, revision);
    State next = new State(id, state.issuer(), state.digest(), state.actor(), state.expiresAt(), "CLAIMED", binding);
    if (!cache.compareAndSet(NAMESPACE, id, old, Json.toJsonString(next), remaining(state))) { throw invalid(); }
    return new Claim(id, binding);
  }
  public void verify(Claim claim, String request, String client, String registration, String candidate, String revision) {
    requireReady();
    if (claim == null) { throw invalid(); }
    State state = state(raw(claim.id()));
    String expected = binding(request, client, registration, candidate, revision);
    if (!state.status().equals("CLAIMED") || !constantEquals(state.binding(), expected)
        || !constantEquals(claim.binding(), expected)) { throw invalid(); }
  }
  public boolean revoke(String id) {
    requireReady();
    String raw = cache.get(NAMESPACE, id).filter(String.class::isInstance).map(String.class::cast).orElse(null);
    if (raw == null) { return false; }
    State state = state(raw);
    if (!state.status().equals("READY")) { return false; }
    return cache.compareAndSet(NAMESPACE, id, raw, Json.toJsonString(new State(id, state.issuer(), state.digest(),
        state.actor(), state.expiresAt(), "REVOKED", null)), remaining(state));
  }
  public void finish(Claim claim, boolean committed) {
    if (claim == null) { return; }
    requireReady();
    String raw = raw(claim.id());
    State state = state(raw);
    if (!state.status().equals("CLAIMED") || !constantEquals(state.binding(), claim.binding())
        || !cache.compareAndSet(NAMESPACE, claim.id(), raw, Json.toJsonString(new State(state.id(), state.issuer(), state.digest(),
            state.actor(), state.expiresAt(), committed ? "CONSUMED" : "CLOSED", state.binding())), remaining(state))) {
      throw DcrFailure.unavailable();
    }
  }
  private String raw(String id) {
    return cache.get(NAMESPACE, id).filter(String.class::isInstance).map(String.class::cast).orElseThrow(DcrCredentials::invalid);
  }
  private State state(String raw) {
    State state = Json.tryToBean(State.class, raw).filter(Objects::nonNull).getOrElseThrow(error -> invalid());
    if (!Objects.equals(issuer.get(), state.issuer()) || state.expiresAt() <= System.currentTimeMillis()) { throw invalid(); }
    return state;
  }
  private Duration remaining(State state) {
    long millis = state.expiresAt() - System.currentTimeMillis();
    if (millis <= 0) { throw invalid(); }
    return Duration.ofMillis(millis);
  }
  private void requireReady() { if (!ready()) { throw DcrFailure.unavailable(); } }
  private static String binding(String request, String client, String registration, String candidate, String revision) {
    return digest(Json.toJsonString(java.util.List.of(request, client, registration, candidate, revision)));
  }
  public static String digest(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException error) { throw new com.ouroboros.exception.InternalException("SHA-256 不可用", error); }
  }
  private static boolean constantEquals(String left, String right) {
    return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }
  private static DcrFailure invalid() { return new DcrFailure(401, "invalid_token"); }
}
