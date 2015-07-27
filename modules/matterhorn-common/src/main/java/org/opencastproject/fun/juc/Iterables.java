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

package org.opencastproject.fun.juc;

import java.util.Iterator;

/** Functions for {@link Iterable}s. */
public final class Iterables {
  private Iterables() {
  }

  public static String mkString(Iterable<?> as, String sep) {
    final StringBuilder b = new StringBuilder();
    for (Iterator<?> i = as.iterator(); i.hasNext();) {
      b.append(i.next());
      if (i.hasNext()) {
        b.append(sep);
      }
    }
    return b.toString();
  }

  /**
   * Make a string from an iterable separating each element by <code>sep</code>. The string is surrounded by
   * <code>pre</code> and <code>post</code>.
   */
  public static String mkString(Iterable<?> as, String sep, String pre, String post) {
    return pre + mkString(as, sep) + post;
  }

  /** Return an iterator as an iterable to make it usable in a for comprehension. */
  public static <A> Iterable<A> asIterable(final Iterator<A> i) {
    return new Iterable<A>() {
      @Override
      public Iterator<A> iterator() {
        return i;
      }
    };
  }
}
