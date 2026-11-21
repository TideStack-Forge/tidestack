package com.ouroboros.data.dict.serializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.pojo.FlatDataDict;
import com.ouroboros.data.dict.pojo.TreeDataDict;

/**
 * @author liansz
 **/
public class DataDictDeserializer extends JsonDeserializer<DataDict> {

  @Override
  public DataDict deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) parser.getCodec();
    ObjectNode node = mapper.readTree(parser);

    if (!node.get("treeMode").asBoolean()) {
      return mapper.treeToValue(node, FlatDataDict.class);
    } else {
      return mapper.treeToValue(node, TreeDataDict.class);
    }
  }
}