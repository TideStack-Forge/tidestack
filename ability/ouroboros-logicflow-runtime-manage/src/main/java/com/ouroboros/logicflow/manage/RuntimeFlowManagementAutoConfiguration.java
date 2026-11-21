package com.ouroboros.logicflow.manage;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.ouroboros.data.annotation.DataModelScan;

/** Optional module assembly. Persisted source reading is independent of the write permission. */
@ComponentScan
@DataModelScan
@AutoConfiguration
public class RuntimeFlowManagementAutoConfiguration { }
