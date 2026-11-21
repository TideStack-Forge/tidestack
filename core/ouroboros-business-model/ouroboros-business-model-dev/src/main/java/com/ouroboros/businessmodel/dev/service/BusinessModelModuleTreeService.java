package com.ouroboros.businessmodel.dev.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.vavr.control.Try;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.util.TreeNode;

public interface BusinessModelModuleTreeService {

  Try<Void> addModule(ModuleTreeNodeVO category);

  Try<Void> deleteModule(String fullName);

  Try<Void> updateModule(String fullName, ModuleTreeNodeVO newModule);

  Try<List<ModuleTreeNodeVO>> getModuleTree();

  @JsonDeserialize(as = ModuleTreeNodePO.class)
  class ModuleTreeNodePO implements AppModule {
    String fullName = "";
    String title = "";
    String icon = "";
    Integer order = 0;

    @Override
    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    @Override
    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public String getIcon() {
      return icon;
    }

    public void setIcon(String icon) {
      this.icon = icon;
    }

    @Override
    public Integer getOrder() {
      return order;
    }

    public void setOrder(Integer order) {
      this.order = order;
    }

    @Override
    public String getMenu() {
      return getFullName();
    }

    @Override
    public List<String> getSubMenus() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getAuthorities() {
      return Collections.emptyList();
    }
  }

  class ModuleTreeNodeVO implements TreeNode<ModuleTreeNodeVO> {
    String name = "";
    String parentName = "";
    String fullName = "";
    String title = "";
    String icon = "";
    Integer order = 0;
    List<ModuleTreeNodeVO> children = new ArrayList<>();

    public String getParentName() {
      return parentName;
    }

    public void setParentName(String parentName) {
      this.parentName = parentName;
    }

    public String getFullName() {
      return fullName;
    }

    public void setFullName(String fullName) {
      this.fullName = fullName;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getIcon() {
      return icon;
    }

    public void setIcon(String icon) {
      this.icon = icon;
    }

    public Integer getOrder() {
      return order;
    }

    public void setOrder(int order) {
      this.order = order;
    }

    @Override
    public List<ModuleTreeNodeVO> getChildren() {
      return children;
    }

    public void setChildren(List<ModuleTreeNodeVO> children) {
      this.children = children;
    }
  }
}
