package com.ouroboros.businesslog.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.constant.OperationResultEnum;
import com.ouroboros.businesslog.model.BusinessLogModel;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BusinessLogExtractorCurrentSecurityContextTest {

  private static final Map<String, String> PATH_VARIABLES = Collections.singletonMap("businessId", "42");
  private static final String REQUEST_BODY = "{\"payload\":\"value\"}";
  private static final String RESPONSE_BODY = "{\"code\":0,\"data\":{\"result\":\"ok\"}}";
  private static final String SEAM_IDENTITY = "seam-operator";
  private static final String LEGACY_PRINCIPAL = "legacy-principal";
  private static final String SEAM_AUTH_PRINCIPAL = "seam-auth-principal";
  private static final String AUTH_SUMMARY_TEMPLATE = "<#if Auth??>auth:${Auth.principal}<#else>auth:</#if>";
  private static final User SEAM_USER = () -> SEAM_IDENTITY;
  private static final User SEAM_AUTH_USER = () -> SEAM_AUTH_PRINCIPAL;
  private static final OuroborosAuthentication SEAM_AUTH = new OuroborosAuthentication() {
    @Override
    public List<? extends Role> getRoles() {
      return Collections.emptyList();
    }

    @Override
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return Collections.emptyList();
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.empty();
    }

    @Override
    public User getUserDetail() {
      return SEAM_AUTH_USER;
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public String getPrincipal() {
      return SEAM_AUTH_PRINCIPAL;
    }

    @Override
    public boolean isAuthenticated() {
      return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
    }

    @Override
    public String getName() {
      return SEAM_AUTH_PRINCIPAL;
    }
  };

  @BeforeEach
  void resetSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void cleanUpSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldCaptureSeamIdentityWhenPresent() throws Exception {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(SEAM_USER)));

    BusinessLogModel model = BusinessLogExtractor.extract(
        createTemplate("seam-summary"),
        PATH_VARIABLES,
        buildRequest(REQUEST_BODY),
        buildResponse(RESPONSE_BODY)
    );

    assertEquals(SEAM_IDENTITY, model.getOperator());
    assertEquals("seam-summary", model.getSummary());
    assertEquals(OperationResultEnum.SUCCESS.getValue(), model.getOperationResult());
  }

  @Test
  void shouldLeaveOperatorNullWhenSeamIdentityAbsent() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new TestingAuthenticationToken(LEGACY_PRINCIPAL, "secret")
    );

    BusinessLogModel model = BusinessLogExtractor.extract(
        createTemplate("legacy-summary"),
        PATH_VARIABLES,
        buildRequest(REQUEST_BODY),
        buildResponse(RESPONSE_BODY)
    );

    assertNull(model.getOperator());
    assertNotEquals(LEGACY_PRINCIPAL, model.getOperator());
    assertNotEquals("anonymousUser", model.getOperator());
    assertEquals("legacy-summary", model.getSummary());
    assertEquals(OperationResultEnum.SUCCESS.getValue(), model.getOperationResult());
  }

  @Test
  void shouldExposeAuthPrincipalWhenCurrentSecurityContextAuthenticationAvailable() throws Exception {
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(SEAM_AUTH);
      }

      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.of(new DefaultSecurityIdentity(SEAM_USER));
      }
    });

    BusinessLogModel model = BusinessLogExtractor.extract(
        createTemplate(AUTH_SUMMARY_TEMPLATE),
        PATH_VARIABLES,
        buildRequest(REQUEST_BODY),
        buildResponse(RESPONSE_BODY)
    );

    assertEquals("auth:" + SEAM_AUTH_PRINCIPAL, model.getSummary());
  }

  @Test
  void shouldKeepAuthSummaryEmptyWhenOnlyLegacySecurityContextAvailable() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(
        new TestingAuthenticationToken(LEGACY_PRINCIPAL, "secret")
    );

    BusinessLogModel model = BusinessLogExtractor.extract(
        createTemplate(AUTH_SUMMARY_TEMPLATE),
        PATH_VARIABLES,
        buildRequest(REQUEST_BODY),
        buildResponse(RESPONSE_BODY)
    );

    assertEquals("auth:", model.getSummary());
    assertNotEquals("auth:" + LEGACY_PRINCIPAL, model.getSummary());
  }

  private BusinessOperationTemplate createTemplate(String summary) {
    return new BusinessOperationTemplate() {
      @Override
      public String getHttpMethod() {
        return "POST";
      }

      @Override
      public String getUrl() {
        return "/api/business";
      }

      @Override
      public String getModuleName() {
        return "business";
      }

      @Override
      public String getOperation() {
        return "operate";
      }

      @Override
      public String getLevel() {
        return "INFO";
      }

      @Override
      public String getBusinessKeyName() {
        return "businessId";
      }

      @Override
      public String getSummaryTemplate() {
        return summary;
      }

      @Override
      public List<String> getSensitiveFields() {
        return Collections.singletonList("secret");
      }

      @Override
      public Boolean isShouldSaveResponseBody() {
        return true;
      }
    };
  }

  private ContentCachingRequestWrapper buildRequest(String payload) throws IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/business");
    request.setContentType("application/json");
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    request.setContent(payload.getBytes(StandardCharsets.UTF_8));

    ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
    StreamUtils.copyToByteArray(wrapper.getInputStream());
    return wrapper;
  }

  private ContentCachingResponseWrapper buildResponse(String body) throws IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    wrapper.setStatus(200);
    wrapper.setContentType("application/json");
    wrapper.getWriter().write(body);
    wrapper.getWriter().flush();
    return wrapper;
  }
}
