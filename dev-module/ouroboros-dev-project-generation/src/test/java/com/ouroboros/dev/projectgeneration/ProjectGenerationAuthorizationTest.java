package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.projectgeneration.controller.ProjectGenerationController;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;

class ProjectGenerationAuthorizationTest {

  @Test
  void shouldStayBehindDevAppPathForAnyAuthThenAppFilterGate() throws Exception {
    RequestMapping mapping = ProjectGenerationController.class.getAnnotation(RequestMapping.class);
    assertNotNull(mapping);
    assertEquals("/api/dev/{appName}", mapping.value()[0]);

    assertHasAppNamePathVariable(ProjectGenerationController.class.getMethod(
        "list",
        String.class,
        ProjectTemplateScope.class,
        boolean.class
    ));
    assertHasAppNamePathVariable(ProjectGenerationController.class.getMethod(
        "generate",
        String.class,
        JsonNode.class
    ));
  }

  @Test
  void shouldNotDeclareControllerLevelAuthorizationBypass() {
    assertFalse(hasAnnotationNamed(ProjectGenerationController.class.getAnnotations(), "PermitAll"));
    assertFalse(hasAnnotationNamed(ProjectGenerationController.class.getAnnotations(), "CrossOrigin"));
  }

  private static void assertHasAppNamePathVariable(Method method) {
    boolean found = Arrays.stream(method.getParameterAnnotations())
        .flatMap(Arrays::stream)
        .anyMatch(PathVariable.class::isInstance);
    assertEquals(true, found, method.getName() + " must keep a PathVariable parameter for app filter authorization");
  }

  private static boolean hasAnnotationNamed(Annotation[] annotations, String simpleName) {
    return Arrays.stream(annotations).anyMatch(annotation -> annotation.annotationType().getSimpleName().equals(simpleName));
  }
}
