package com.ouroboros.security.externalauth.cas;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.vavr.control.Either;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.util.SPI;
import com.ouroboros.security.externalauth.AuthError;
import com.ouroboros.security.externalauth.AuthorizeUrlResult;
import com.ouroboros.security.externalauth.CallbackParams;
import com.ouroboros.security.externalauth.ExchangeParams;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.externalauth.RedirectAuthProvider;
import com.ouroboros.security.externalauth.config.CasProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;

/**
 * CAS protocol provider implementation.
 * R1: exchange flow only (ticket validation via HTTP).
 */
public class CasProvider implements RedirectAuthProvider {

    private static final Logger logger = LoggerFactory.getLogger(CasProvider.class);

    private static final String CAS_TICKET_RESULT_CACHE = "ouroboros:auth:cas-ticket-result";
    private static final long TICKET_RESULT_TTL_MS = 10_000L;

    /** CAS 2.0 响应命名空间（协议标识，非部署端点）。 */
    private static final String CAS_NAMESPACE = "http://www.yale.edu/tp/cas";

    private final CasProviderSpec spec;
    private final String publicBaseUrl;
    private final CacheManager cacheManager;
    private final CasHttpClient casHttpClient;
    private final TicketDigestAlgorithm digestAlgorithm;

    public CasProvider(CasProviderSpec spec, String publicBaseUrl, CacheManager cacheManager,
                       CasHttpClient casHttpClient) {
        this.spec = spec;
        this.publicBaseUrl = publicBaseUrl;
        this.cacheManager = cacheManager;
        this.casHttpClient = casHttpClient;
        this.digestAlgorithm = SPI.getCachedPrimaryService(TicketDigestAlgorithm.class);
        if (this.digestAlgorithm == null) {
            throw new IllegalStateException("No TicketDigestAlgorithm SPI implementation found");
        }
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
    public AuthorizeUrlResult buildAuthorizeUrl(String returnUrl) {
        String state = com.ouroboros.security.externalauth.SecurityTokens.generateSecureToken();
        String serviceUrl = publicBaseUrl + "/api/auth/cas/" + spec.getKey() + "/callback?state=" + state;
        String encodedServiceUrl;
        try {
            encodedServiceUrl = URLEncoder.encode(serviceUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding not supported", e);
        }
        String casLoginUrl = spec.getCasBaseUrl() + "/login?service=" + encodedServiceUrl;

        Map<String, Object> cacheEntry = new HashMap<>();
        cacheEntry.put("returnUrl", returnUrl);
        cacheEntry.put("serviceUrl", serviceUrl);

        return AuthorizeUrlResult.of(casLoginUrl, state, cacheEntry);
    }

    @Override
    public Either<AuthError, ExternalIdentity> handleCallback(CallbackParams params) {
        try {
            String ticket = params.getSingleCallbackParameter("ticket");
            if (ticket == null || ticket.isEmpty()) {
                return Either.left(AuthError.invalidCredentials("CAS ticket is required"));
            }

            String serviceUrl = (String) params.getCachedState().get("serviceUrl");
            if (serviceUrl == null) {
                return Either.left(AuthError.providerUnavailable("State corrupted: missing serviceUrl"));
            }

            return validateTicketAndBuildIdentity(ticket, serviceUrl);
        } catch (Exception e) {
            logger.error("CAS callback handling failed for provider '{}': {}", spec.getKey(), e.getMessage(), e);
            return Either.left(AuthError.providerUnavailable("CAS callback handling failed: " + e.getMessage()));
        }
    }

    @Override
    public Either<AuthError, ExternalIdentity> handleExchange(ExchangeParams params) {
        try {
            String ticket = params.getTicketOrCode();
            if (ticket == null || ticket.isEmpty()) {
                return Either.left(AuthError.invalidCredentials("CAS ticket is required"));
            }
            String serviceUrl = publicBaseUrl + "/api/auth/cas/" + spec.getKey() + "/callback";
            return validateTicketAndBuildIdentity(ticket, serviceUrl);
        } catch (Exception e) {
            logger.error("CAS exchange failed for provider {}: {}", spec.getKey(), e.getMessage(), e);
            return Either.left(AuthError.providerUnavailable("CAS ticket validation failed: " + e.getMessage()));
        }
    }

    private Either<AuthError, ExternalIdentity> validateTicketAndBuildIdentity(String ticket,
                                                                                String serviceUrl) throws Exception {
        String xml = casHttpClient.validateTicket(spec.getCasBaseUrl(), resolveValidatePath(), ticket, serviceUrl);
        Either<AuthError, CasPrincipal> parseResult = parseCasResponse(xml);
        if (parseResult.isLeft()) {
            return Either.left(parseResult.getLeft());
        }
        return Either.right(buildIdentity(parseResult.get()));
    }

    public Optional<CasTicketFinalResult> consumeFinalResult(String ticket) {
        Optional<Object> cached = cacheManager.getAndEvict(CAS_TICKET_RESULT_CACHE, buildTicketCacheKey(ticket));
        if (!cached.isPresent()) {
            return Optional.empty();
        }
        CasTicketCacheEntry entry = (CasTicketCacheEntry) cached.get();
        logger.info("[cid={}] CAS ticket retry-cache hit: provider={}", entry.correlationId, spec.getKey());
        return Optional.of(entry.result);
    }

    public void cacheFinalResult(String ticket, CasTicketFinalResult result, String correlationId) {
        cacheManager.put(CAS_TICKET_RESULT_CACHE, buildTicketCacheKey(ticket),
            new CasTicketCacheEntry(result, correlationId), TICKET_RESULT_TTL_MS);
    }

    private String buildTicketCacheKey(String ticket) {
        String ticketDigest = digestAlgorithm.digest(spec.getKey(), ticket);
        return spec.getKey() + ":" + digestAlgorithm.getName() + ":" + ticketDigest;
    }

    private String resolveValidatePath() {
        String validatePath = spec.getValidatePath();
        if (validatePath == null || validatePath.isEmpty()) {
            return "/serviceValidate";
        }
        return validatePath;
    }

    /**
     * Parse CAS XML response into CasPrincipal or AuthError.
     */
    private Either<AuthError, CasPrincipal> parseCasResponse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Check for authentication failure
            NodeList failureNodes = doc.getElementsByTagNameNS(CAS_NAMESPACE, "authenticationFailure");
            if (failureNodes.getLength() > 0) {
                String failureText = failureNodes.item(0).getTextContent().trim();
                logger.warn("CAS authentication failure for provider {}: {}", spec.getKey(), failureText);
                return Either.left(AuthError.invalidCredentials("CAS authentication failed"));
            }

            // Extract user principal
            NodeList userNodes = doc.getElementsByTagNameNS(CAS_NAMESPACE, "user");
            if (userNodes.getLength() == 0) {
                return Either.left(AuthError.providerUnavailable("CAS response missing user element"));
            }
            String user = userNodes.item(0).getTextContent().trim();

            // Extract attributes
            Map<String, String> attributes = new HashMap<>();
            NodeList attrNodes = doc.getElementsByTagNameNS(CAS_NAMESPACE, "attributes");
            if (attrNodes.getLength() > 0) {
                Element attrsElement = (Element) attrNodes.item(0);
                NodeList children = attrsElement.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element) {
                        Element child = (Element) children.item(i);
                        attributes.put(child.getLocalName(), child.getTextContent().trim());
                    }
                }
            }

            return Either.right(new CasPrincipal(user, attributes));
        } catch (Exception e) {
            logger.error("Failed to parse CAS response for provider {}: {}", spec.getKey(), e.getMessage(), e);
            return Either.left(AuthError.providerUnavailable("Failed to parse CAS response"));
        }
    }

    private ExternalIdentity buildIdentity(CasPrincipal principal) {
        String externalId;
        if (spec.getExternalIdField() != null && !spec.getExternalIdField().isEmpty()) {
            externalId = principal.attributes.get(spec.getExternalIdField());
            if (externalId == null) {
                externalId = principal.user;
            }
        } else {
            externalId = principal.user;
        }

        Map<String, String> attrMapping = spec.getAttributeMapping();
        String displayName = null;
        String email = null;
        if (attrMapping != null) {
            String displayNameAttr = attrMapping.get("displayName");
            if (displayNameAttr != null) {
                displayName = principal.attributes.get(displayNameAttr);
            }
            String emailAttr = attrMapping.get("email");
            if (emailAttr != null) {
                email = principal.attributes.get(emailAttr);
            }
        }

        Map<String, Object> attrs = new HashMap<>(principal.attributes);
        return ExternalIdentity.of(spec.getKey(), externalId, displayName, email, attrs);
    }

    /**
     * Internal data holder for parsed CAS principal and attributes.
     */
    private static class CasPrincipal {
        final String user;
        final Map<String, String> attributes;

        CasPrincipal(String user, Map<String, String> attributes) {
            this.user = user;
            this.attributes = attributes;
        }
    }

    public static class CasTicketFinalResult implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private final String accessToken;
        private final String refreshToken;
        private final String bindingToken;

        private CasTicketFinalResult(String accessToken, String refreshToken, String bindingToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.bindingToken = bindingToken;
        }

        public static CasTicketFinalResult loggedIn(String accessToken, String refreshToken) {
            return new CasTicketFinalResult(accessToken, refreshToken, null);
        }

        public static CasTicketFinalResult bindingRequired(String bindingToken) {
            return new CasTicketFinalResult(null, null, bindingToken);
        }

        public boolean isLoggedIn() {
            return bindingToken == null;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getBindingToken() {
            return bindingToken;
        }
    }

    static class CasTicketCacheEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        final CasTicketFinalResult result;
        final String correlationId;

        CasTicketCacheEntry(CasTicketFinalResult result, String correlationId) {
            this.result = result;
            this.correlationId = correlationId;
        }
    }
}
