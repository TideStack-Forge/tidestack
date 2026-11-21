package com.ouroboros.dynamicform;

import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

@ComponentScan(value = "com.ouroboros.dynamicform.**")
@DataModelScan
public class DynamicFormAutoConfiguration {
}
