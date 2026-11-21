package com.ouroboros.data.runtime.adapter;

import java.util.Map;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import com.ouroboros.data.record.DefaultRecordImpl;
import com.ouroboros.data.record.Record;
import com.ouroboros.script.JsHostAccessConfigurer;

public class JsTypeMapping implements JsHostAccessConfigurer {
  @Override
  public void config(HostAccess.Builder builder) {
    builder.targetTypeMapping(Value.class, Record.class, Value::hasMembers,
        value -> new DefaultRecordImpl(value.as(Map.class)),
        HostAccess.TargetMappingPrecedence.HIGHEST);
  }
}
