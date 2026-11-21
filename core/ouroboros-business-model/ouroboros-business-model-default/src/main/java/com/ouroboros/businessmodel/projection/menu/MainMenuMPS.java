package com.ouroboros.businessmodel.projection.menu;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.model.BusinessModelMenu;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.menu.MenuModel;

/**
 * 业务模型功能入口菜单，一般为索引页
 */
@Priority(50)
public class MainMenuMPS extends AbstractDefaultBusinessModelProcessStep {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    if (model.isAttachment()) {
      return;
    }
    if (!model.getExtraPropDeep(Boolean.class, "views", "index", "isEnabled").orElse(false)) {
      return;
    }
    var menu = new BusinessModelMenu();
    var basePath = BusinessModelUtil.buildKebabCasePath(model);
    menu.setFullName(model.getFullName());
    menu.setTitle(model.getTitle());
    menu.setUrl(basePath);
    menu.setIcon(model.getIcon());
    menu.setOrder(model.getOrder());
    context.putModel(MenuModel.class, menu.getFullName(), menu);
  }

}
