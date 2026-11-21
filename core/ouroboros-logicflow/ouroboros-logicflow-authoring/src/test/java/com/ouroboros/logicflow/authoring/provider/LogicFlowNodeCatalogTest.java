package com.ouroboros.logicflow.authoring.provider;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import com.ouroboros.logicflow.authoring.model.NodeDefine;

class LogicFlowNodeCatalogTest {
  @Test void applicationDefinitionsOverrideBuiltinsCaseInsensitivelyAndKeepDeclaredOrder() {
    var builtin = new NodeDefine().setType("script").setOrder(100);
    var application = new NodeDefine().setType("SCRIPT").setOrder(5);
    var custom = new NodeDefine().setType("applicationNode").setOrder(1);
    LogicFlowNodeDefineProvider first = () -> Arrays.asList(builtin, null, new NodeDefine().setType(" "));
    LogicFlowNodeDefineProvider last = () -> List.of(application, custom);
    assertEquals(List.of(custom, application), LogicFlowNodeCatalog.merge(Stream.of(first, last)));
  }
}
