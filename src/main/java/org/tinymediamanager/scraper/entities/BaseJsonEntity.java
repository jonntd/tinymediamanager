package org.tinymediamanager.scraper.entities;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * All our JSON entities should extend that, so that they do not need to implement the <br>
 * ignore/missing/toString things over and over again or fail on renamed things...
 * 
 * @author Myron Boyle
 *
 */
public class BaseJsonEntity {
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<>();

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  public String toString(ToStringStyle style) {
    return ToStringBuilder.reflectionToString(this, style);
  }
}
