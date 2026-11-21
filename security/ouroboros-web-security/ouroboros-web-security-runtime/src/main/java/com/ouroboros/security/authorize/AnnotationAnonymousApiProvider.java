package com.ouroboros.security.authorize;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;

/**
 * @author liansz
 */
@Component
public class AnnotationAnonymousApiProvider
    extends AbstractAnnotationAccessApiProvider implements AnonymousApiProvider {

  @Override
  @SuppressWarnings("deprecation")
  public List<String> getAnonymousApis() {
    return getAccessApis(handlerMethod -> handlerMethod.getMethodAnnotation(Anonymous.class) != null);
  }
}
