package com.ouroboros.businessmodel;

import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.event.BusinessEventPort;

/** Connects SPI-built model definitions to the configured execution ports. */
public interface BusinessModelRuntimeAccess {
  BusinessActionRuntime actions();

  BusinessEventPort events();
}
