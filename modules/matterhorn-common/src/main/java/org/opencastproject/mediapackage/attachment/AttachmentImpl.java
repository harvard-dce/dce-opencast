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

package org.opencastproject.mediapackage.attachment;

import org.opencastproject.mediapackage.AbstractMediaPackageElement;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.util.Checksum;
import org.opencastproject.util.MimeType;

import java.net.URI;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlAdapter;

/**
 * Basic implementation of an attachment.
 */
@XmlType(name = "attachment", namespace = "http://mediapackage.opencastproject.org")
@XmlRootElement(name = "attachment", namespace = "http://mediapackage.opencastproject.org")
public class AttachmentImpl extends AbstractMediaPackageElement implements Attachment {

  /** Serial version UID */
  private static final long serialVersionUID = 6626531251856698138L;

  /**
   * Needed by JAXB
   */
  public AttachmentImpl() {
    super(Type.Attachment, null, null);
  }

  /**
   * Creates an attachment.
   *
   * @param identifier
   *          the attachment identifier
   * @param flavor
   *          the attachment type
   * @param uri
   *          the attachments location
   * @param size
   *          the attachments size
   * @param checksum
   *          the attachments checksum
   * @param mimeType
   *          the attachments mime type
   */
  protected AttachmentImpl(String identifier, MediaPackageElementFlavor flavor, URI uri, long size, Checksum checksum,
          MimeType mimeType) {
    super(identifier, Type.Attachment, flavor, uri, size, checksum, mimeType);
  }

  /**
   * Creates an attachment.
   *
   * @param flavor
   *          the attachment type
   * @param uri
   *          the attachment location
   * @param size
   *          the attachment size
   * @param checksum
   *          the attachment checksum
   * @param mimeType
   *          the attachment mime type
   */
  protected AttachmentImpl(MediaPackageElementFlavor flavor, URI uri, long size, Checksum checksum, MimeType mimeType) {
    super(Type.Attachment, flavor, uri, size, checksum, mimeType);
  }

  /**
   * Creates an attachment.
   *
   * @param identifier
   *          the attachment identifier
   * @param uri
   *          the attachments location
   */
  protected AttachmentImpl(String identifier, URI uri) {
    this(identifier, null, uri, 0, null, null);
  }

  /**
   * Creates an attachment.
   *
   * @param uri
   *          the attachments location
   */
  protected AttachmentImpl(URI uri) {
    this(null, null, uri, 0, null, null);
  }

  /**
   * Creates a new attachment from the url.
   *
   * @param uri
   *          the attachment location
   * @return the attachment
   */
  public static Attachment fromURI(URI uri) {
    return new AttachmentImpl(uri);
  }

  public static class Adapter extends XmlAdapter<AttachmentImpl, Attachment> {
    public AttachmentImpl marshal(Attachment mp) throws Exception {
      return (AttachmentImpl) mp;
    }

    public Attachment unmarshal(AttachmentImpl mp) throws Exception {
      return mp;
    }
  }
}
