package com.ouroboros.security.rbac.profile.service;



import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.lock.DistributedLockManager;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.profile.*;
import com.ouroboros.security.profile.UserProfilePolicy.Audience;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;

/** Contact proof is bound to one account incarnation, platform session, schema and database revision. */
@Service
public final class ContactChangeService {
  private static final String CHALLENGES = "ouroboros:profile:contacts";
  private static final String LIMITS = "ouroboros:profile:contact-limits";
  private static final Duration LIFETIME = Duration.ofMinutes(10);
  private final UserProfileService profiles;
  private final UserProfilePolicySource policies;
  private final UserService users;
  private final UserPasswordVerifier passwords;
  private final CacheManager cache;
  private final List<ContactVerificationDelivery> delivery;
  private final ObjectProvider<ContactVerificationProof> proofs;
  private final SecureRandom random = new SecureRandom();

  public ContactChangeService(UserProfileService profiles, UserProfilePolicySource policies, UserService users,
      UserPasswordVerifier passwords, CacheManager cache, ObjectProvider<ContactVerificationDelivery> delivery,
      ObjectProvider<ContactVerificationProof> proofs) {
    this.profiles = profiles; this.policies = policies; this.users = users; this.passwords = passwords;
    this.cache = cache; this.delivery = delivery.orderedStream().toList(); this.proofs = proofs;
  }

  record Challenge(String id, String subject, String session, String kind, String value, String schema,
      long revision, String digest, Instant expiresAt, int attempts, String stage) implements java.io.Serializable {
    Challenge stage(String value) { return new Challenge(id, subject, session, kind, this.value, schema, revision, digest, expiresAt, attempts, value); }
    Challenge attempted() { return new Challenge(id, subject, session, kind, value, schema, revision, digest, expiresAt, attempts + 1, stage); }
    @Override public String toString() { return "ContactChallenge[redacted]"; }
  }

  public record Change(String schemaRevision, Long expectedProfileRevision, String newValue, String password) {
    @Override public String toString() { return "ContactChange[redacted]"; }
  }

  public Map<String, Object> change(String session, String source, String kind, Change change) {
    String identity = UserProfileService.self();
    String field = field(kind);
    return locked(identity, kind, () -> {
      var account = profiles.account(identity);
      var rules = policies.current();
      UserProfileService.requireRevision(account, rules, change.schemaRevision(), change.expectedProfileRevision());
      var definition = rules.policy().field(field);
      if (!definition.editableBy().contains(Audience.SELF)) { throw failure(403, "PROFILE_FIELD_READ_ONLY", "联系方式只读"); }
      // Rate limits precede reauthentication to bound online password guessing as well as delivery.
      count("account:" + account.getPublicSubjectId(), 20, Duration.ofHours(1));
      count("source:" + hash(source == null ? "unknown" : source), 100, Duration.ofHours(1));
      if (change.password() == null || !passwords.verify(identity, change.password())) {
        throw failure(403, "CONTACT_REAUTHENTICATION_FAILED", "当前密码验证未通过");
      }
      String value = (String) rules.policy().normalize(definition, change.newValue());
      if (value == null && definition.required()) { throw failure(400, "PROFILE_FIELD_INVALID", "必填联系方式不能清空"); }
      String current = kind.equals("email") ? account.getEmail() : account.getPhoneNumber();
      boolean verified = Boolean.TRUE.equals(kind.equals("email") ? account.getEmailVerified() : account.getPhoneNumberVerified());
      String key = key(account, kind);
      boolean required = rules.policy().verification().get(kind).equals("REQUIRED");
      if (java.util.Objects.equals(value, current) && (!required || verified || value == null)) {
        return Map.of("status", "UNCHANGED", "profileRevision", account.getProfileRevision());
      }
      if (!required || value == null) {
        save(account, field, value, false);
        cache.evict(CHALLENGES, key);
        return Map.of("status", "APPLIED", "profileRevision", account.getProfileRevision() + 1);
      }
      var adapter = delivery.stream().filter(item -> item.supports(kind)).findFirst()
          .orElseThrow(() -> failure(503, "CONTACT_DELIVERY_UNAVAILABLE", "验证投递能力尚未就绪"));
      var proof = proof();
      count("send:" + key, 1, Duration.ofSeconds(60));
      String requestId = UUID.randomUUID().toString();
      String secret;
      if (kind.equals("phone")) { secret = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
      else { byte[] bytes = new byte[32]; random.nextBytes(bytes); secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
      var challenge = new Challenge(requestId, account.getPublicSubjectId(), session, kind, value, rules.revision(),
          account.getProfileRevision(), proof.digest(requestId, secret), Instant.now().plus(LIFETIME), 0, "SENDING");
      put(key, challenge);
      try { adapter.deliver(new ContactVerificationDelivery.Message(kind, value, requestId, secret)); }
      catch (RuntimeException error) {
        cache.evict(CHALLENGES, key);
        // Provider exceptions may contain rendered proofs; never expose them as API details.
        throw failure(503, "CONTACT_DELIVERY_UNAVAILABLE", "验证消息投递失败，请稍后重新发起");
      }
      challenge = challenge.stage("PENDING"); put(key, challenge);
      return view(challenge);
    });
  }

  /** Administrative replacement never asserts proof and invalidates any pending self-service change. */
  public Map<String, Object> replaceByAdmin(String identity, String kind, Replacement change) {
    UserProfileService.self();
    com.ouroboros.security.authorize.AuthorizationEvaluator.standard().evaluate(
        CurrentSecurityContextHolder.getRequiredIdentity(),
        com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API,
        "systemSetting.userManagement.profile").requirePermit();
    String field = field(kind);
    return locked(identity, kind, () -> {
      var account = profiles.account(identity);
      var rules = policies.current();
      UserProfileService.requireRevision(account, rules, change.schemaRevision(), change.expectedProfileRevision());
      var definition = rules.policy().field(field);
      if (!definition.editableBy().contains(Audience.ADMIN)) { throw failure(403, "PROFILE_FIELD_READ_ONLY", "联系方式只读"); }
      String value = (String) rules.policy().normalize(definition, change.newValue());
      if (value == null && definition.required()) { throw failure(400, "PROFILE_FIELD_INVALID", "必填联系方式不能清空"); }
      save(account, field, value, false);
      cache.evict(CHALLENGES, key(account, kind));
      return Map.of("status", "APPLIED");
    });
  }

  public record Replacement(String schemaRevision, Long expectedProfileRevision, String newValue) { }

  public Map<String, Object> confirm(String session, String kind, String requestId, String value) {
    String identity = UserProfileService.self(); field(kind);
    return locked(identity, kind, () -> {
      var account = profiles.account(identity);
      String key = key(account, kind);
      var challenge = pending(account, session, kind, requestId);
      if (challenge.attempts() >= 5) { throw failure(429, "CONTACT_RATE_LIMITED", "验证尝试次数过多，请重新发起"); }
      var attempted = challenge.attempted(); put(key, attempted);
      boolean valid = value != null && value.length() <= 128 && MessageDigest.isEqual(
          proof().digest(requestId, value).getBytes(StandardCharsets.US_ASCII), challenge.digest().getBytes(StandardCharsets.US_ASCII));
      if (!valid) { throw failure(400, "CONTACT_PROOF_INVALID", "验证凭据无效"); }
      put(key, attempted.stage("PROCESSING"));
      save(account, field(kind), challenge.value(), true);
      cache.evict(CHALLENGES, key);
      return Map.of("status", "APPLIED", "profileRevision", account.getProfileRevision() + 1);
    });
  }

  public void cancel(String session, String kind, String requestId) {
    String identity = UserProfileService.self(); field(kind);
    locked(identity, kind, () -> {
      var account = profiles.account(identity);
      pending(account, session, kind, requestId);
      cache.evict(CHALLENGES, key(account, kind)); return null;
    });
  }

  public Map<String, Object> status(String session, String kind) {
    String identity = UserProfileService.self(); field(kind);
    return locked(identity, kind, () -> {
      var account = profiles.account(identity);
      var challenge = cache.get(CHALLENGES, key(account, kind)).filter(Challenge.class::isInstance).map(Challenge.class::cast).orElse(null);
      if (challenge == null || !challenge.session().equals(session)) { return Map.of("status", "NONE"); }
      return view(pending(account, session, kind, challenge.id()));
    });
  }

  private Challenge pending(UserEntity account, String session, String kind, String id) {
    var challenge = cache.get(CHALLENGES, key(account, kind)).filter(Challenge.class::isInstance).map(Challenge.class::cast)
        .orElseThrow(() -> failure(409, "CONTACT_CHALLENGE_EXPIRED", "验证请求已失效，请重新发起"));
    if (!challenge.id().equals(id) || !challenge.session().equals(session) || !challenge.subject().equals(account.getPublicSubjectId())) {
      throw failure(403, "CONTACT_IDENTITY_MISMATCH", "验证请求不属于当前会话");
    }
    if (!challenge.stage().equals("PENDING") || !challenge.expiresAt().isAfter(Instant.now())
        || !challenge.schema().equals(policies.current().revision()) || challenge.revision() != account.getProfileRevision()) {
      cache.evict(CHALLENGES, key(account, kind));
      throw failure(409, "CONTACT_CHALLENGE_EXPIRED", "资料或验证规则已变化，请重新发起");
    }
    return challenge;
  }

  private ContactVerificationProof proof() {
    return proofs.stream().filter(ContactVerificationProof::ready).findFirst()
        .orElseThrow(() -> failure(503, "CONTACT_DELIVERY_UNAVAILABLE", "验证密钥尚未就绪"));
  }

  private void save(UserEntity account, String field, String value, boolean verified) {
    var patch = new LinkedHashMap<String, Object>(); patch.put(field, value); patch.put(field + "Verified", verified);
    CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
      users.saveProfile(account.getUserIdentity(), account.getPublicSubjectId(), account.getProfileRevision(), patch); return null;
    });
  }

  private void put(String key, Challenge value) {
    var ttl = Duration.between(Instant.now(), value.expiresAt());
    if (ttl.isNegative() || ttl.isZero()) { throw failure(409, "CONTACT_CHALLENGE_EXPIRED", "验证请求已过期"); }
    cache.put(CHALLENGES, key, value, ttl);
  }

  private void count(String key, int limit, Duration window) {
    var lock = DistributedLockManager.getLockManager().getLock("profile:limit:" + key); lock.lock();
    try {
      var old = cache.get(LIMITS, key).filter(Counter.class::isInstance).map(Counter.class::cast)
          .filter(value -> value.expiresAt().isAfter(Instant.now())).orElse(new Counter(0, Instant.now().plus(window)));
      if (old.count() >= limit) { throw failure(429, "CONTACT_RATE_LIMITED", "操作过于频繁，请稍后重试"); }
      cache.put(LIMITS, key, new Counter(old.count() + 1, old.expiresAt()), Duration.between(Instant.now(), old.expiresAt()));
    } finally { lock.unlock(); }
  }
  private record Counter(int count, Instant expiresAt) implements java.io.Serializable { }

  private <T> T locked(String user, String kind, Supplier<T> action) {
    var lock = DistributedLockManager.getLockManager().getLock("profile:contact:" + hash(user) + ":" + kind); lock.lock();
    try { return action.get(); } finally { lock.unlock(); }
  }
  private static String key(UserEntity account, String kind) { return account.getPublicSubjectId() + ":" + kind; }
  private static String field(String kind) {
    return switch (kind) { case "email" -> "email"; case "phone" -> "phoneNumber";
      default -> throw failure(400, "PROFILE_INVALID", "不支持的联系方式类型"); };
  }
  private static Map<String, Object> view(Challenge value) {
    String mask = value.kind().equals("email") ? "***@" + value.value().substring(value.value().lastIndexOf('@') + 1)
        : "***" + value.value().substring(Math.max(0, value.value().length() - 4));
    return Map.of("status", value.stage(), "requestId", value.id(), "expiresAt", value.expiresAt().toString(), "maskedValue", mask);
  }
  private static String hash(String value) {
    try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException error) { throw new com.ouroboros.exception.InternalException("无法计算缓存索引", error); }
  }
  private static BusinessException failure(int status, String code, String message) { return new BusinessException(status, "[" + code + "] " + message); }
}
