package com.ouroboros.configuration.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchGroupPanel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchItemPanel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchTreeNode;

public interface ConfigurationWorkbenchService {

  List<ConfigurationWorkbenchTreeNode> getTree();

  Optional<ConfigurationWorkbenchGroupPanel> getGroupPanel(String path);

  Optional<ConfigurationWorkbenchItemPanel> getItemPanel(String key);
}
