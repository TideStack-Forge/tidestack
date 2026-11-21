package com.ouroboros.modelmanage.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.ouroboros.security.annotation.Anonymous;
import com.ouroboros.template.JarTemplateEngine;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev-fragment-schema")
public class DevFragmentSchemaController {
  JarTemplateEngine templateEngine = new JarTemplateEngine("META-INF/ouroboros/dev/fragment-schema");

  @GetMapping("/{*path}")
  @Anonymous
  public WebApiResult<SchemaNode> getSchema(@PathVariable String path,
                                            @RequestParam Map<String, String> requestParam) {
    var paramMap = new HashMap<>(requestParam);
    var finalPath = path.startsWith("/") ? path.substring(1) : path;
    var result = templateEngine.generate(finalPath, paramMap, SchemaNode.class);
    return WebApiResult.success(result);
  }
}
