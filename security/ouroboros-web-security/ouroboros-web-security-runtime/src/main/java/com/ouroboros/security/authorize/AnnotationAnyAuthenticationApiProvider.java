package com.ouroboros.security.authorize;

import java.util.List;

import jakarta.annotation.security.PermitAll;

import org.springframework.stereotype.Component;

import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;

/**
 * @author liansz
 */
@Component
public class AnnotationAnyAuthenticationApiProvider
    extends AbstractAnnotationAccessApiProvider implements AnyAuthenticationApiProvider {

  @Override
  @SuppressWarnings("deprecation")
  public List<String> getAnyAuthenticationApis() {
    return getAccessApis(handlerMethod -> handlerMethod.getMethodAnnotation(PermitAll.class) != null);
  }
}
