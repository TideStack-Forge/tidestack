package com.ouroboros.businesslog.util;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import io.vavr.control.Try;

import com.ouroboros.expression.ContextWrappingHelper;
import com.ouroboros.util.Json;

import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * @author liansz
 **/
public class StringTemplateEngine {
  final Map<String, Object> wrapper = new HashMap<>();
  private Configuration cfg;

  public StringTemplateEngine() {
    cfg = new Configuration(Configuration.VERSION_2_3_31);
    cfg.setDefaultEncoding("UTF-8");
    var json = new Json();
    wrapper.put("Jsons", json);
    wrapper.put("Json", new Json());
  }

  public String generate(String template, Map<String, ?> config) {
    return Try.of(() -> {
      Template tpl = new Template(DigestUtils.md5DigestAsHex(template.getBytes()), new StringReader(template), cfg);
      var stringWriter = new StringWriter();
      tpl.process(wrapData(config), stringWriter);
      return stringWriter.toString();
    }).toEither().peekLeft(Throwable::printStackTrace).getOrElse((String) null);
  }

  public <T> T generate(String template, Map<String, ?> config, Class<T> clazz) {
    var resultStr = generate(template, config);
    return Optional.ofNullable(resultStr)
        .filter(StringUtils::isNotBlank)
        .map(str -> Json.tryToBean(clazz, str).toEither().peekLeft(Throwable::printStackTrace).getOrNull())
        .orElse(null);
  }

  private Map<String, Object> wrapData(Map<String, ?> data) {
    return new ContextWrappingHelper((Map<String, Object>) data, wrapper);
  }
}
