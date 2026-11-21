package com.ouroboros.uimodel.dev.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.util.Json;

class UIDevControllerIconTest {

  @Test
  void treeUiModelShouldKeepIconAsTopLevelField() {
    SimpleUIModel model = Json.toBean(SimpleUIModel.class,
        "{\"title\":\"字段类型管理\",\"path\":\"/dev/ui\",\"type\":\"amis\",\"icon\":\"fa fa-database\"}");

    UIDevController.TreeUIModel treeModel = new UIDevController.TreeUIModel(model);

    assertEquals("fa fa-database", treeModel.getIcon());

    UIDevController.TreeUIModel.Model dto = new UIDevController.TreeUIModel.Model();
    dto.setIcon("fa fa-database");
    assertEquals("fa fa-database", dto.getIcon());
  }
}
