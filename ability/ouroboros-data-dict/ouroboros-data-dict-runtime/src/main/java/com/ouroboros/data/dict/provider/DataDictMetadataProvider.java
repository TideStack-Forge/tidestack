package com.ouroboros.data.dict.provider;

import java.util.Map;

import com.ouroboros.data.dict.DataDict;

public interface DataDictMetadataProvider {
  Map<String, DataDict> getDataDicts();
}
