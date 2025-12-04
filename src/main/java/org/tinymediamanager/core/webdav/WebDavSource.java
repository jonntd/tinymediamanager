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
package org.tinymediamanager.core.webdav;

import java.util.UUID;

import org.tinymediamanager.core.AbstractModelObject;
import org.tinymediamanager.core.EncryptedStringDeserializer;
import org.tinymediamanager.core.EncryptedStringSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The class WebDavSource - to represent a WebDAV server configuration
 * 
 * @author Manuel Laggner
 */
public class WebDavSource extends AbstractModelObject {
  private static final String ID       = "id";
  private static final String NAME     = "name";
  private static final String URL      = "url";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";

  private String              id;
  private String              name;
  private String              url;
  private String              username;
  private String              password;

  public WebDavSource() {
    this.id = UUID.randomUUID().toString();
  }

  /**
   * copy constructor
   * 
   * @param original
   *          the original to copy
   */
  public WebDavSource(WebDavSource original) {
    this.id = original.id;
    this.name = original.name;
    this.url = original.url;
    this.username = original.username;
    this.password = original.password;
  }

  public String getId() {
    return id;
  }

  public void setId(String newValue) {
    String oldValue = this.id;
    this.id = newValue;
    firePropertyChange(ID, oldValue, newValue);
  }

  public String getName() {
    return name;
  }

  public void setName(String newValue) {
    String oldValue = this.name;
    this.name = newValue;
    firePropertyChange(NAME, oldValue, newValue);
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String newValue) {
    String oldValue = this.url;
    this.url = newValue;
    firePropertyChange(URL, oldValue, newValue);
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String newValue) {
    String oldValue = this.username;
    this.username = newValue;
    firePropertyChange(USERNAME, oldValue, newValue);
  }

  @JsonSerialize(using = EncryptedStringSerializer.class)
  @JsonDeserialize(using = EncryptedStringDeserializer.class)
  public String getPassword() {
    return password;
  }

  public void setPassword(String newValue) {
    String oldValue = this.password;
    this.password = newValue;
    firePropertyChange(PASSWORD, oldValue, newValue);
  }

  /**
   * Get the display name for this WebDAV source
   * 
   * @return the display name (name if set, otherwise URL)
   */
  public String getDisplayName() {
    if (name != null && !name.isEmpty()) {
      return name;
    }
    return url;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    WebDavSource other = (WebDavSource) obj;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }
}

