package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;

public class FlowModelCompatibilityDefaultsTest {
  @Test
  public void flowModelInterfaceShouldDefaultErrorEntryNodeToNull() {
    FlowModel flowModel = new FlowModel() {
      @Override
      public String getName() {
        return "compat";
      }

      @Override
      public String getDescription() {
        return "compat";
      }

      @Override
      public List<FlowNode> getNodes() {
        return Collections.emptyList();
      }

      @Override
      public List<FlowConnection> getConnections() {
        return Collections.emptyList();
      }

      @Override
      public Boolean isEnabled() {
        return true;
      }

      @Override
      public Boolean isValid() {
        return true;
      }

      @Override
      public DataTypeEnum getInputDataType() {
        return DataTypeEnum.ANY;
      }

      @Override
      public DataTypeEnum getOutputDataType() {
        return DataTypeEnum.ANY;
      }

      @Override
      public Try<Object> execute(Object... inputs) {
        return Try.success(null);
      }

      @Override
      public FlowNode getEntryNode() {
        return null;
      }

      @Override
      public FlowNode getExitNode() {
        return null;
      }

      @Override
      public FlowNode getNode(String name) {
        return null;
      }

      @Override
      public List<DesignException> getErrors() {
        return Collections.emptyList();
      }
    };

    assertNull(flowModel.getErrorEntryNode());
  }

  @Test
  public void flowNodePortInterfaceShouldDefaultMetadataToEmpty() {
    FlowNodePort port = new FlowNodePort() {
      @Override
      public Integer getIndex() {
        return 0;
      }

      @Override
      public String getName() {
        return "output";
      }

      @Override
      public String getDescription() {
        return "compat";
      }

      @Override
      public DataTypeEnum getDataType() {
        return DataTypeEnum.ANY;
      }

      @Override
      public FlowNode getFlowNode() {
        return null;
      }
    };

    assertNull(port.getExclusiveGroupId());
    assertFalse(Boolean.TRUE.equals(port.isDefaultBranch()));
  }
}
