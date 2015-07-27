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
$.mediapackageParser = function (mediapackage, smiltype_episode) {
    var SMILTYPE_PRESENTER = "presenter/smil";
    var SMILTYPE_PRESENTATION = "presentation/smil";
    var SMILTYPE_EPISODE = smiltype_episode || "episode/smil";
    var SMIL = "";
    var SMIL_EPISODE = "";

    this.mediapackage = mediapackage;

    if (this.mediapackage) {
        this.start = this.mediapackage.start;
        this.id = this.mediapackage.id;
        this.duration = this.mediapackage.duration;
        this.title = this.mediapackage.title;
        this.creators = this.mediapackage.creators;
        this.license = this.mediapackage.license;
        this.media = this.mediapackage.media;
        this.metadata = this.mediapackage.metadata;
        this.attachments = this.mediapackage.attachments;

        this.smil = "";
        this.smil_episode = "";
        this.smil_id = -1;
        this.smil_episode_id = -1;
        this.smil_mimetype = "";
        this.smil_tags = "";
        this.smil_type = "";
        this.smil_url = "";

        if (this.metadata.catalog) {
            var smil_presenter = undefined;
            var smil_presentation = undefined;
            var smil_episode = undefined;
            $.each(this.metadata.catalog, function (index, value) {
                if (value.type.indexOf(SMILTYPE_PRESENTER) != -1) {
                    smil_presenter = value;
                } else if (value.type.indexOf(SMILTYPE_PRESENTATION) != -1) {
                    smil_presentation = value;
                } else if (value.type.indexOf(SMILTYPE_EPISODE) != -1) {
                    smil_episode = value;
                }
            });
            if (smil_presenter != undefined) {
                SMIL = smil_presenter;
            } else if (smil_presentation != undefined) {
                SMIL = smil_presentation;
            }
            if (smil_episode != undefined) {
                SMIL_EPISODE = smil_episode;
            }
            this.smil = SMIL;
            this.smil_episode = SMIL_EPISODE;
            if (this.smil.id) {
                this.smil_id = this.smil.id;
            }
            if (this.smil_episode.id) {
                this.smil_episode_id = this.smil_episode.id;
            }
            this.smil_mimetype = this.smil.mimetype;
            this.smil_tags = this.smil.tags;
            this.smil_type = this.smil.type;
            this.smil_url = this.smil.url;
        }
    }
}
