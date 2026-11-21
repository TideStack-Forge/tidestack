package com.ouroboros.organizationstructure.context;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationContextInitializerFilterCurrentSecurityContextTest {

  @AfterEach
  void clearContextHolders() {
    CurrentSecurityContextHolder.clearDelegate();
    OrganizationContextHolder.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilterUsesCurrentSecurityContextIdentityAndClearsOrganizationContextAfterChain() throws IOException, ServletException {
    EmployeeAggregator employeeAggregator = mock(EmployeeAggregator.class);
    when(employeeAggregator.getByUser("seam-user")).thenReturn(Optional.empty());

    OrganizationContextInitializerFilter filter = new OrganizationContextInitializerFilter();
    ReflectionTestUtils.setField(filter, "employeeAggregator", employeeAggregator);

    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "seam-user")));
    assertNull(SecurityContextHolder.getContext().getAuthentication());

    ServletRequest request = mock(ServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain filterChain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
        OrganizationContext context = OrganizationContextHolder.getContext()
            .orElseThrow(() -> new AssertionError("Organization context should be set before downstream chain"));

        assertFalse(context.getEmployee().isPresent());
        verify(employeeAggregator).getByUser("seam-user");
      }
    };

    filter.doFilter(request, response, filterChain);

    assertFalse(OrganizationContextHolder.getContext().isPresent());
  }
}
