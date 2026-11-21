package com.ouroboros.data.dict.serializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.pojo.FlatDataDictItem;
import com.ouroboros.data.dict.pojo.TreeDataDictItem;

/**
 * @author liansz
 */
public class DataDictItemDeserializer extends JsonDeserializer<DataDictItem> {
  @Override
  public DataDictItem deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) parser.getCodec();
    ObjectNode node = mapper.readTree(parser);

    JsonNode children = node.get("children");
    if (children == null || ((children instanceof ArrayNode arrayNode) && arrayNode.isEmpty())) {
      return mapper.treeToValue(node, FlatDataDictItem.class);
    } else {
      return mapper.treeToValue(node, TreeDataDictItem.class);
    }
  }
}
