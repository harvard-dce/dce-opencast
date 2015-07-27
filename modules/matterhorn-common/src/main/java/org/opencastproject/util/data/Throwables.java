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

package org.opencastproject.util.data;

import static org.opencastproject.util.data.functions.Misc.chuck;

public final class Throwables {
  private Throwables() {
  }

  /**
   * Forward exception <code>t</code> directly or wrap it into an instance of <code>wrapper</code>
   * if it is not of the wrapper's type. Class <code>wrapper</code> needs a constructor taking just
   * a {@link Throwable}.
   */
  public static <T extends Throwable, A> A forward(Throwable t, Class<T> wrapper) throws T {
    if (wrapper.isAssignableFrom(t.getClass())) {
      throw (T) t;
    } else {
      try {
        throw wrapper.getConstructor(Exception.class).newInstance(t);
      } catch (Exception e) {
        return chuck(e);
      }
    }
  }
}
