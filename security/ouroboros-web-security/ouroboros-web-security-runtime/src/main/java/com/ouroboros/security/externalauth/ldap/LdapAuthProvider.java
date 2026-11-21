package com.ouroboros.security.externalauth.ldap;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Either;

import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.DirectAuthProvider;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.config.LdapProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;

/**
 * LDAP/AD authentication provider supporting search mode and adDirectBind mode.
 */
public class LdapAuthProvider implements DirectAuthProvider {

  private static final Logger logger = LoggerFactory.getLogger(LdapAuthProvider.class);

  private final LdapProviderSpec spec;
  private final CompositeSecretResolver secretResolver;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;

  public LdapAuthProvider(LdapProviderSpec spec, CompositeSecretResolver secretResolver,
                          int connectTimeoutMs, int readTimeoutMs) {
    this.spec = spec;
    this.secretResolver = secretResolver;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
  }

  @Override
  public String getProviderKey() {
    return spec.getKey();
  }

  @Override
  public ProviderSpec getSpec() {
    return spec;
  }

  @Override
  public Either<AuthError, ExternalIdentity> authenticate(String username, String password) {
    if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
      return Either.left(AuthError.invalidCredentials("Username and password are required"));
    }

    try {
      if ("adDirectBind".equals(spec.getMode())) {
        return adDirectBindAuthenticate(username, password);
      } else {
        return searchModeAuthenticate(username, password);
      }
    } catch (AuthenticationException e) {
      logger.debug("LDAP authentication failed for user: {}", username);
      return Either.left(AuthError.invalidCredentials("Invalid credentials"));
    } catch (NamingException e) {
      logger.error("LDAP error during authentication for provider: {}", spec.getKey(), e);
      return Either.left(AuthError.providerUnavailable("LDAP connection error"));
    }
  }

  private Either<AuthError, ExternalIdentity> searchModeAuthenticate(String username, String password)
      throws NamingException {
    // 1. Resolve service account password
    Optional<String> bindPassword = secretResolver.resolveWithFallback(
        spec.getSearchBindPasswordRef(), spec.getSearchBindPassword());
    if (!bindPassword.isPresent()) {
      logger.error("Cannot resolve search bind password for provider: {}", spec.getKey());
      return Either.left(AuthError.providerUnavailable("Provider configuration error"));
    }

    // 2. Bind with service account
    DirContext serviceCtx = createLdapContext(spec.getSearchBindDn(), bindPassword.get());
    try {
      // 3. Search for user DN
      String filter = buildSearchFilter(username);
      SearchControls searchControls = new SearchControls();
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

      NamingEnumeration<SearchResult> results = serviceCtx.search(spec.getBaseDn(), filter, searchControls);
      try {
        if (!results.hasMore()) {
          logger.debug("LDAP search returned 0 results for user: {}", username);
          return Either.left(AuthError.invalidCredentials("Invalid credentials"));
        }

        SearchResult searchResult = results.next();

        if (results.hasMore()) {
          logger.warn("LDAP search returned multiple results for user: {} - search filter not unique", username);
          return Either.left(AuthError.invalidCredentials("Invalid credentials"));
        }

        String userDn = searchResult.getNameInNamespace();
        Attributes searchAttrs = searchResult.getAttributes();

        // 4. Bind as user to verify credentials
        DirContext userCtx = createLdapContext(userDn, password);
        userCtx.close();

        // 5. Build ExternalIdentity from search attributes
        return Either.right(buildExternalIdentity(searchAttrs));
      } finally {
        results.close();
      }
    } finally {
      serviceCtx.close();
    }
  }

  private Either<AuthError, ExternalIdentity> adDirectBindAuthenticate(String username, String password)
      throws NamingException {
    // 1. Construct bind name
    String bindName = buildAdBindName(username);

    // 2. Bind with user credentials
    DirContext userCtx = createLdapContext(bindName, password);
    try {
      // 3. Search for attributes
      String loginId = extractLoginId(username);
      String filter = buildSearchFilter(loginId);
      SearchControls searchControls = new SearchControls();
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

      NamingEnumeration<SearchResult> results = userCtx.search(spec.getBaseDn(), filter, searchControls);
      try {
        if (!results.hasMore()) {
          logger.warn("AD direct bind succeeded but attribute search returned 0 results for: {}", username);
          return Either.left(AuthError.invalidCredentials("Invalid credentials"));
        }

        SearchResult searchResult = results.next();
        return Either.right(buildExternalIdentity(searchResult.getAttributes()));
      } finally {
        results.close();
      }
    } finally {
      userCtx.close();
    }
  }

  private String buildAdBindName(String username) {
    String resolvedMode = resolveLoginIdMode(username);
    if ("USER_PRINCIPAL_NAME".equals(resolvedMode)) {
      // UPN: if already contains @, use as-is; otherwise append @domain
      if (username.contains("@")) {
        // Warn if domain doesn't match configured domain
        String domainPart = username.substring(username.indexOf('@') + 1);
        if (spec.getDomain() != null && !spec.getDomain().equalsIgnoreCase(domainPart)) {
          logger.warn("UPN domain mismatch: input={}, configured={}", domainPart, spec.getDomain());
        }
        return username;
      }
      return username + "@" + spec.getDomain();
    } else {
      // sAMAccountName: DOMAIN\\username
      return spec.getDomain() + "\\" + username;
    }
  }

  private String extractLoginId(String username) {
    // For adDirectBind attribute search, extract the plain login ID
    if (username.contains("\\")) {
      return username.substring(username.indexOf('\\') + 1);
    }
    if (username.contains("@")) {
      return username.substring(0, username.indexOf('@'));
    }
    return username;
  }

  private String resolveLoginIdMode(String username) {
    String mode = spec.getLoginIdMode();
    if (mode == null || "AUTO".equals(mode)) {
      if (username.contains("@")) {
        return "USER_PRINCIPAL_NAME";
      }
      if (username.contains("\\")) {
        return "SAM_ACCOUNT_NAME";
      }
      return "SAM_ACCOUNT_NAME";
    }
    return mode;
  }

  private String buildSearchFilter(String username) {
    String escaped = LdapFilterEscaper.escape(username);
    // Use explicit searchFilter if configured
    if (spec.getSearchFilter() != null && !spec.getSearchFilter().isEmpty()) {
      return spec.getSearchFilter().replace("{0}", escaped);
    }
    // Default filter based on loginIdMode
    String resolvedMode = resolveLoginIdMode(username);
    if ("USER_PRINCIPAL_NAME".equals(resolvedMode)) {
      return "(userPrincipalName=" + escaped + ")";
    }
    return "(sAMAccountName=" + escaped + ")";
  }

  private ExternalIdentity buildExternalIdentity(Attributes attrs) throws NamingException {
    // Read externalId
    String externalIdAttr = spec.getExternalIdAttribute();
    String externalId = readExternalId(attrs, externalIdAttr);

    // Read mapped attributes
    HashMap<String, Object> mappedAttrs = new HashMap<String, Object>();
    String displayName = null;
    String email = null;

    if (spec.getAttributeMapping() != null) {
      for (Map.Entry<String, String> entry : spec.getAttributeMapping().entrySet()) {
        String ldapAttrName = entry.getKey();
        String targetField = entry.getValue();
        String value = readStringAttribute(attrs, ldapAttrName);
        if (value != null) {
          mappedAttrs.put(targetField, value);
          if ("displayName".equals(targetField)) {
            displayName = value;
          } else if ("email".equals(targetField)) {
            email = value;
          }
        }
      }
    }

    return ExternalIdentity.of(spec.getKey(), externalId, displayName, email, mappedAttrs);
  }

  private String readExternalId(Attributes attrs, String attrName) throws NamingException {
    javax.naming.directory.Attribute attr = attrs.get(attrName);
    if (attr == null) {
      throw new NamingException("External ID attribute not found: " + attrName);
    }
    Object value = attr.get();
    if (value instanceof byte[]) {
      // Binary attribute (e.g. objectGUID) — convert to GUID string
      return LdapObjectGuidConverter.convertToGuidString((byte[]) value);
    }
    return value.toString();
  }

  private String readStringAttribute(Attributes attrs, String attrName) {
    try {
      javax.naming.directory.Attribute attr = attrs.get(attrName);
      if (attr != null) {
        Object value = attr.get();
        return value != null ? value.toString() : null;
      }
    } catch (NamingException e) {
      logger.debug("Failed to read LDAP attribute: {}", attrName);
    }
    return null;
  }

  private DirContext createLdapContext(String principal, String credentials) throws NamingException {
    NamingException lastException = null;
    for (String url : spec.getUrls()) {
      Hashtable<String, String> env = new Hashtable<String, String>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
      env.put(Context.PROVIDER_URL, url);
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, principal);
      env.put(Context.SECURITY_CREDENTIALS, credentials);
      env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connectTimeoutMs));
      env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(readTimeoutMs));
      try {
        return new InitialDirContext(env);
      } catch (AuthenticationException e) {
        throw e;
      } catch (NamingException e) {
        lastException = e;
        logger.warn("LDAP connect failed on url={}, provider={}: {}", url, spec.getKey(), e.getMessage());
      }
    }
    if (lastException != null) {
      throw lastException;
    }
    throw new NamingException("No LDAP URL configured for provider: " + spec.getKey());
  }
}
