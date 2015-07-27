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

package org.opencastproject.archive.base.persistence;

import org.opencastproject.archive.api.Version;
import org.opencastproject.util.data.Option;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import static org.opencastproject.util.data.Tuple.tuple;
import static org.opencastproject.util.persistence.PersistenceUtil.runSingleResultQuery;
import static org.opencastproject.util.persistence.PersistenceUtil.runUpdate;

/** Supports the determination of the next free version identifier. */
@Entity(name = "VersionClaim")
@Table(name = "mh_archive_version_claim")
@NamedQueries({
        @NamedQuery(name = "VersionClaim.last", query = "select a from VersionClaim a where a.mediaPackageId = :mediaPackageId"),
        @NamedQuery(name = "VersionClaim.update", query = "update VersionClaim a set a.lastClaimed = :lastClaimed where a.mediaPackageId = :mediaPackageId") })
public final class VersionClaimDto {
  @Id
  @Column(name = "mediapackage", length = 128)
  private String mediaPackageId;

  @Column(name = "last_claimed", nullable = false)
  private long lastClaimed;

  /** Create a new DTO. */
  public static VersionClaimDto create(String mediaPackageId, Version lastClaimed) {
    final VersionClaimDto dto = new VersionClaimDto();
    dto.mediaPackageId = mediaPackageId;
    dto.lastClaimed = lastClaimed.value();
    return dto;
  }

  // Put some getters here. Since this is purely internal there is no need to create a dedicated business object.

  public String getMediaPackageId() {
    return mediaPackageId;
  }

  public Version getLastClaimed() {
    return Version.version(lastClaimed);
  }

  /** Find the last claimed version for a media package. */
  public static Option<VersionClaimDto> findLast(EntityManager em, String mediaPackageId) {
    return runSingleResultQuery(em, "VersionClaim.last", tuple("mediaPackageId", mediaPackageId));
  }

  /** Update the last claimed version of a media package. */
  public static boolean update(EntityManager em, String mediaPackageId, Version lastClaimed) {
    return runUpdate(em, "VersionClaim.update",
                     tuple("mediaPackageId", mediaPackageId),
                     tuple("lastClaimed", lastClaimed.value()));
  }
}
