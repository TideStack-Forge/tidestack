package com.ouroboros.security.rbac.controller;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.User;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserInfoControllerHttpStatusTest {

  @AfterEach
  void clearSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void infoSeparatesNamesAndKeepsTheirAuthoritativeValues() throws Exception {
    var claims = Claims.builder().claim("displayName", "forged-display")
        .claim("formalName", "forged-formal").claim("nickname", "forged-nick").build();
    var user = new User() {
      public String getUserIdentity() { return "alice"; }
      public String getNickname() { return "小张"; }
      public String getFormalName() { return "张三"; }
      public Claims getClaims() { return claims; }
    };
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    MockMvcBuilders.standaloneSetup(new UserInfoController()).build()
        .perform(get("/api/sys/user/info"))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("小张"))
        .andExpect(jsonPath("$.data.nickName").value("小张"))
        .andExpect(jsonPath("$.data.formalName").value("张三"))
        .andExpect(jsonPath("$.data.displayName").value("小张"));
  }

  @Test
  void infoShouldPreferCurrentSecurityContextUserAndKeepEmptyMapFallback() throws Exception {
    var user = user("seam-user", "Seam User", Claims.builder().claim("department", "security").build());

    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy-holder-user", null));

    var mockMvc = MockMvcBuilders.standaloneSetup(new UserInfoController()).build();

    mockMvc.perform(get("/api/sys/user/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.userIdentity").value("seam-user"))
        .andExpect(jsonPath("$.data.displayName").value("Seam User"))
        .andExpect(jsonPath("$.data.formalName").value("Seam User"))
        .andExpect(jsonPath("$.data.nickname").doesNotExist())
        .andExpect(jsonPath("$.data.nickName").doesNotExist())
        .andExpect(jsonPath("$.data.department").value("security"))
        .andExpect(jsonPath("$.data.user.userIdentity").value("seam-user"));

    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();

    mockMvc.perform(get("/api/sys/user/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").isMap())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  private User user(String userIdentity, String displayName, Claims claims) {
    return new User() {
      @Override
      public String getUserIdentity() {
        return userIdentity;
      }

      @Override
      public Claims getClaims() {
        return claims;
      }

      @Override
      public Subject getSubject() {
        return new Subject() {
          @Override
          public String getId() {
            return userIdentity;
          }

          @Override
          public String getName() {
            return displayName;
          }

          @Override
          public String getType() {
            return "USER";
          }

          @Override
          public String getDisplayName() {
            return displayName;
          }
        };
      }
    };
  }
}
