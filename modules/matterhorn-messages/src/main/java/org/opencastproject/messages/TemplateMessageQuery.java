/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.messages;

public class TemplateMessageQuery {
  protected String name;
  protected String creator;
  protected TemplateType.Type type;
  protected String fullText;
  protected boolean includeHidden = false;

  public String getName() {
    return name;
  }

  public String getCreator() {
    return creator;
  }

  public TemplateType.Type getType() {
    return type;
  }

  public String getFullText() {
    return fullText;
  }

  public boolean isIncludeHidden() {
    return includeHidden;
  }

  public void withName(String name) {
    this.name = name;
  }

  public void withCreator(String creator) {
    this.creator = creator;
  }

  public void withType(TemplateType.Type type) {
    this.type = type;
  }

  public void withFullText(String fullText) {
    this.fullText = fullText;
  }

  public void withIncludeHidden() {
    this.includeHidden = true;
  }

}
