/*
 * Copyright 2012 - 2025 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.util.ListUtils;

/**
 * This class is used for holding a config setting
 * 
 * @author Myron Boyle
 */
public class MediaProviderConfigObject {
  public enum ConfigType {
    TEXT,
    BOOL,
    SELECT,
    SELECT_INDEX,
    INTEGER,
    LABEL, // just for labeling in the UI
    MULTI_SELECT
  }

  private static final Logger                   LOGGER         = LoggerFactory.getLogger(MediaProviderConfigObject.class);
  public final static MediaProviderConfigObject EMPTY_OBJECT   = new MediaProviderConfigObject("", ConfigType.TEXT);

  final String                                  key;
  final ConfigType                              type;

  String                                        keyDescription = "";
  String                                        value          = "";
  String                                        defaultValue   = "";
  boolean                                       encrypt        = false;
  boolean                                       visible        = true;
  List<String>                                  possibleValues = new ArrayList<>();

  public MediaProviderConfigObject(String key, ConfigType type) {
    this.key = key;
    this.type = type;
  }

  /**
   * Get the key for this config object
   * 
   * @return the key
   */
  public String getKey() {
    return key;
  }

  /**
   * short description for key, to display in GUI<br>
   * if empty, we are returning the key (as before)
   */
  public String getKeyDescription() {
    return keyDescription.isEmpty() ? key : keyDescription;
  }

  /**
   * short description for key, to display in GUI<br>
   * 
   * @param keyDescription
   *          the description for the key
   */
  public void setKeyDescription(String keyDescription) {
    this.keyDescription = keyDescription;
  }

  /**
   * gets the configured value, or the default one
   * 
   * @return the value or an empty {@link String}
   */
  public String getValue() {
    String ret = "";
    switch (type) {
      case SELECT:
        ret = getValueAsString();
        break;

      case SELECT_INDEX:
        Integer i = getValueIndex();
        ret = (i == null || i < 0) ? "" : String.valueOf(i);
        break;

      case BOOL:
        return String.valueOf(getValueAsBool());

      case INTEGER:
        return String.valueOf(getValueAsInteger());

      case TEXT:
      default:
        return this.value;
    }
    return ret;
  }

  /**
   * Gets the value as a {@link String}
   * 
   * @return the value as {@link String}
   */
  public String getValueAsString() {
    if (type == ConfigType.SELECT && !possibleValues.contains(this.value)) {
      return this.defaultValue;
    }
    return this.value;
  }

  /**
   * Gets the value as boolean
   * 
   * @return the value as boolean or false when not parsable to a boolean
   */
  public boolean getValueAsBool() {
    boolean bool = Boolean.FALSE;
    if (type != ConfigType.BOOL) {
      return bool;
    }
    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) { // always false when unparseable :/
      bool = Boolean.parseBoolean(value);
    }
    else {
      bool = Boolean.parseBoolean(defaultValue);
    }
    return bool;
  }

  /**
   * Gets the value as an {@link Integer}
   * 
   * @return the value as an {@link Integer} or null if not parsable
   */
  public Integer getValueAsInteger() {
    Integer integer = null;
    if (type != ConfigType.INTEGER) {
      return null;
    }
    try {
      integer = Integer.parseInt(value);
    }
    catch (Exception e) {
      try {
        integer = Integer.parseInt(defaultValue);
      }
      catch (Exception e1) {
        // ignored
      }
    }
    return integer;
  }

  /**
   * Gets the index of the value
   * 
   * @return the index or null
   */
  public Integer getValueIndex() {
    // FIXME: Index is just stored in value? return 1:1 ?!? no example found yet...
    Integer ret;
    if (type != ConfigType.SELECT && type != ConfigType.SELECT_INDEX) {
      return null;
    }
    ret = possibleValues.indexOf(value);
    if (ret == -1) {
      ret = possibleValues.indexOf(defaultValue);
      if (ret == -1) {
        ret = null;
      }
    }
    return ret;
  }

  /**
   * Sets the {@link String} value
   * 
   * @param value
   *          the value as {@link String}
   */
  public void setValue(String value) {
    if (type == ConfigType.SELECT && !possibleValues.contains(value)) {
      return;
    }
    this.value = StringUtils.strip(value);
  }

  /**
   * Sets the value as boolean
   * 
   * @param value
   *          the value as boolean
   */
  public void setValue(boolean value) {
    if (type != ConfigType.BOOL) {
      LOGGER.trace("This is not a boolean configuration object - setting keep current value");
    }
    else {
      this.value = String.valueOf(value);
    }
  }

  /**
   * Sets the value as {@link Integer}
   * 
   * @param value
   *          the value as {@link Integer}
   */
  public void setValue(Integer value) {
    if (type != ConfigType.INTEGER) {
      LOGGER.trace("This is not an Integer configuration object - setting keep current value");
    }
    else {
      this.value = String.valueOf(value);
    }
  }

  /**
   * Gets the default value
   * 
   * @return the default value
   */
  public String getDefaultValue() {
    return defaultValue;
  }

  /**
   * Sets the default value
   * 
   * @param defaultValue
   *          the default value
   */
  public void setDefaultValue(String defaultValue) {
    if (type == ConfigType.SELECT && !possibleValues.contains(defaultValue)) {
      LOGGER.trace("Will not set defaultValue '{}={}' - since it is not in the list of possible values!", key, defaultValue);
    }
    else {
      this.defaultValue = StringUtils.strip(defaultValue);
    }
  }

  /**
   * Gets all possible values for this config object
   * 
   * @return a {@link List} containing all possible values
   */
  public List<String> getPossibleValues() {
    return possibleValues;
  }

  /**
   * Sets all possible values for this config object
   * 
   * @param possibleValues
   *          a {@link List} containing all possible values
   */
  public void setPossibleValues(List<String> possibleValues) {
    if (ListUtils.isNotEmpty(possibleValues)) {
      this.possibleValues.addAll(possibleValues);
    }
  }

  /**
   * Adds a possible value
   * 
   * @param possibleValue
   *          the possible valule to add
   */
  public void addPossibleValue(String possibleValue) {
    if (!this.possibleValues.contains(possibleValue)) {
      this.possibleValues.add(possibleValue);
    }
  }

  /**
   * Gets the {@link ConfigType} of this config object
   * 
   * @return the {@link ConfigType}
   */
  public ConfigType getType() {
    return type;
  }

  /**
   * Is this config object encrypted
   * 
   * @return true/false
   */
  public boolean isEncrypt() {
    return encrypt;
  }

  /**
   * Should this config object be encrypted
   * 
   * @param encrypt
   *          true/false
   */
  public void setEncrypt(boolean encrypt) {
    this.encrypt = encrypt;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  /**
   * option can be "hidden" in GUI, but be still an option!
   * 
   * @return true/false
   */
  public boolean isVisible() {
    return visible;
  }

  /**
   * option can be "hidden" in GUI, but be still an option!
   * 
   * @param visible
   *          true if that object should be visible in the UI. false otherwise
   */
  public void setVisible(boolean visible) {
    this.visible = visible;
  }
}
