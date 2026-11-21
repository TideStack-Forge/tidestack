package ${package};

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 自动配置类
 */
@Configuration
@ConditionalOnClass(name = "${package}.${autoConfigClassName}")
public class ${autoConfigClassName} {

  private static final Logger logger = LoggerFactory.getLogger(${autoConfigClassName}.class);

  @PostConstruct
  private void init() {
    logger.info("${autoConfigClassName} 模块已加载");
  }

}
