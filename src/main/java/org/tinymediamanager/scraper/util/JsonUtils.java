package org.tinymediamanager.scraper.util;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * The class {@link JsonUtils} is used as a helper for JSON operations
 * 
 * @author Myron Byole
 */
public class JsonUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

  private JsonUtils() {
    throw new IllegalAccessError();
  }

  public static <E> E parseObject(ObjectMapper mapper, JsonNode jsonNode, Class<E> clazz) {
    if (jsonNode == null || jsonNode.isMissingNode() || jsonNode instanceof NullNode) {
      LOGGER.debug("JsonNode NULL or missing (parsing {})", clazz.getCanonicalName());
      return null;
    }
    JsonParser jsonParser = mapper.treeAsTokens(jsonNode);
    try {
      E ret = mapper.readValue(jsonParser, clazz);
      if (ret == null) {
        LOGGER.debug("JsonNode returning NULL (readValue {})", clazz.getCanonicalName());
      }
      return ret;
    }
    catch (Exception e) {
      LOGGER.debug("mapping to {} failed: {}", clazz, e);
      return null;
    }
  }

  public static <E> List<E> parseList(ObjectMapper mapper, JsonNode jsonNode, Class<E> clazz) {
    if (jsonNode == null || jsonNode.isMissingNode()) {
      LOGGER.debug("JsonNode NULL or missing (parsing {})", clazz.getCanonicalName());
      return Collections.emptyList();
    }
    JsonParser jsonParser = mapper.treeAsTokens(jsonNode);
    try {
      List<E> ret = mapper.readValue(jsonParser, JsonUtils.listType(mapper, clazz));
      if (ret == null) {
        LOGGER.debug("JsonNode returning NULL (readValue {})", clazz.getCanonicalName());
      }
      return ret;
    }
    catch (Exception e) {
      LOGGER.debug("mapping to {} failed: {}", clazz, e);
      return Collections.emptyList();
    }
  }

  public static <E> JavaType listType(ObjectMapper mapper, Class<E> clazz) {
    return mapper.getTypeFactory().constructCollectionType(List.class, clazz);
  }

  /**
   * replacement for JsonNode.at(), to get some logging...
   * 
   * @param node
   * @param jsonPtrExpr
   * @return
   */
  public static JsonNode at(JsonNode node, String jsonPtrExpr) {
    JsonNode ret = node.at(JsonPointer.compile(jsonPtrExpr));
    if (ret == null || ret.isMissingNode()) {
      LOGGER.trace("Cannot parse JSON at '{}', because is was missing/empty/non-existent", jsonPtrExpr);
    }
    return ret;
  }
}