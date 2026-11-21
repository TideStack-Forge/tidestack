package com.ouroboros.devhelper.command;

import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeCatalog;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;
import com.ouroboros.util.SPI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public final class EditorCatalogCommand extends AbstractAsyncCommand {
  private final ObjectProvider<FieldTypeCatalog> fields;
  public EditorCatalogCommand(ObjectProvider<FieldTypeCatalog> fields) {
    super(Commands.EDITOR_CATALOG.getCommand()); this.fields = fields;
  }
  @Override protected void doExecute(CommandRequest request) {
    switch (String.valueOf(request.getParams().get("kind"))) {
      case "logicflow" -> success(request, LogicFlowNodeCatalog.merge(SPI.getCachedSortedServiceStream(LogicFlowNodeDefineProvider.class)));
      case "field-types" -> success(request, fields.getObject().listMetadata(FieldTypeCatalogContext.runtime()));
      default -> failure(request, "不支持的编辑器目录类型");
    }
  }
}
