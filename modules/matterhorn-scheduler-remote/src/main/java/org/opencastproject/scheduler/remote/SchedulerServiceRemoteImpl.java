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

package org.opencastproject.scheduler.remote;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreCatalogList;
import org.opencastproject.metadata.dublincore.DublinCores;
import org.opencastproject.scheduler.api.SchedulerException;
import org.opencastproject.scheduler.api.SchedulerQuery;
import org.opencastproject.scheduler.api.SchedulerService;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.AccessControlParser;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.serviceregistry.api.RemoteBase;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.UrlSupport;
import org.opencastproject.util.data.Tuple;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * A proxy to a remote series service.
 */
public class SchedulerServiceRemoteImpl extends RemoteBase implements SchedulerService {

  private static final Logger logger = LoggerFactory.getLogger(SchedulerServiceRemoteImpl.class);

  public SchedulerServiceRemoteImpl() {
    super(JOB_TYPE);
  }

  @Override
  public Long addEvent(DublinCoreCatalog eventCatalog, Map<String, String> wfProperties) throws SchedulerException,
          UnauthorizedException {
    HttpPost post = new HttpPost("/");
    logger.debug("Start adding a new event {} through remote Schedule Service",
            eventCatalog.get(DublinCoreCatalog.PROPERTY_IDENTIFIER));

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("dublincore", eventCatalog.toXmlString()));

      // Add an empty agentparameters as it is required by the Rest Endpoint
      params.add(new BasicNameValuePair("agentparameters", "remote.scheduler.empty.parameter"));
      params.add(new BasicNameValuePair("wfproperties", toPropertyString(wfProperties)));

      post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException("Unable to assemble a remote scheduler request for adding an event " + eventCatalog,
              e);
    }

    HttpResponse response = getResponse(post, SC_CREATED, SC_UNAUTHORIZED);
    try {
      if (response != null && SC_CREATED == response.getStatusLine().getStatusCode()) {
        Header header = response.getFirstHeader("Location");
        if (header != null) {
          String idString = FilenameUtils.getBaseName(header.getValue());
          if (StringUtils.isNotEmpty(idString)) {
            Long id = Long.parseLong(idString);
            logger.info("Successfully added event {} to the scheduler service with id {}", eventCatalog, id);
            return id;
          }
        }
        throw new SchedulerException("Event " + eventCatalog
                + " added to the scheduler service but got not event id in response.");
      } else if (response != null && SC_UNAUTHORIZED == response.getStatusLine().getStatusCode()) {
        logger.info("Unauthorized to create the event");
        throw new UnauthorizedException("Unauthorized to create the event");
      } else {
        throw new SchedulerException("Unable to add event " + eventCatalog + " to the scheduler service");
      }
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to add event " + eventCatalog + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
  }

  @Override
  public Long[] addReccuringEvent(DublinCoreCatalog eventCatalog, Map<String, String> wfProperties)
          throws SchedulerException, UnauthorizedException {
    HttpPost post = new HttpPost("/");
    logger.debug("Start adding new events through remote Schedule Service");

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("dublincore", eventCatalog.toXmlString()));

      // Add an empty agentparameters as it is required by the Rest Endpoint
      params.add(new BasicNameValuePair("agentparameters", "remote.scheduler.empty.parameter"));
      params.add(new BasicNameValuePair("wfproperties", toPropertyString(wfProperties)));

      post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException("Unable to assemble a remote scheduler request for adding events " + eventCatalog, e);
    }

    HttpResponse response = getResponse(post, SC_CREATED, SC_UNAUTHORIZED);
    try {
      if (response != null && SC_CREATED == response.getStatusLine().getStatusCode()) {

        try {
          String idsStr = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
          List<Long> ids = new ArrayList<Long>();

          for (String id : StringUtils.split(idsStr, ",")) {
            ids.add(Long.parseLong(id));
          }

          return ids.toArray(new Long[ids.size()]);
        } catch (IOException e) {
          throw new SchedulerException("Unable to parse the events ids from the reponse creation.");
        }
      } else if (response != null && SC_UNAUTHORIZED == response.getStatusLine().getStatusCode()) {
        logger.info("Unauthorized to add reccuring event");
        throw new UnauthorizedException("Unauthorized to add reccuring event");
      } else {
        throw new SchedulerException("Unable to add event " + eventCatalog + " to the scheduler service");
      }
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to add event " + eventCatalog + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }

  }

  @Override
  public void updateCaptureAgentMetadata(Properties configuration, Tuple<Long, DublinCoreCatalog>... events)
          throws NotFoundException, SchedulerException {
    logger.debug("Start updating {} events with following capture agent metadata: {}", events.length, configuration);
    for (Tuple<Long, DublinCoreCatalog> event : events) {
      final long eventId = event.getA();
      final DublinCoreCatalog eventCatalog = event.getB();

      logger.debug("Start updating event {} with capture agent metadata", eventId);

      String propertiesString = "";
      for (Entry<Object, Object> entry : configuration.entrySet())
        propertiesString += (String) entry.getKey() + "=" + (String) entry.getValue() + "\n";

      HttpPut put = new HttpPut("/" + eventId);

      try {
        List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("dublincore", eventCatalog.toXmlString()));
        params.add(new BasicNameValuePair("agentproperties", propertiesString));

        put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
      } catch (Exception e) {
        throw new SchedulerException("Unable to assemble a remote scheduler request for adding an event "
                + eventCatalog, e);
      }

      HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND);
      try {
        if (response != null) {
          if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
            logger.info("Event {} was not found by the scheduler service", eventId);
            throw new NotFoundException("Event '" + eventId + "' not found on remote scheduler service!");
          } else if (SC_OK == response.getStatusLine().getStatusCode()) {
            logger.info("Event {} successfully updated with capture agent metadata.", eventId);
            return;
          } else {
            throw new SchedulerException("Unexpected status code " + response.getStatusLine());
          }
        }
      } catch (NotFoundException e) {
        throw e;
      } catch (Exception e) {
        throw new SchedulerException("Unable to update event " + eventId + " to the scheduler service: " + e);
      } finally {
        closeConnection(response);
      }
      throw new SchedulerException("Unable to update  event " + eventId);
    }
  }

  @Override
  public void updateEvent(long eventId, DublinCoreCatalog eventCatalog, Map<String, String> wfProperties)
          throws NotFoundException, SchedulerException, UnauthorizedException {
    logger.debug("Start updating event {}.", eventId);
    HttpPut put = new HttpPut("/" + eventId);

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      if (eventCatalog != null)
        params.add(new BasicNameValuePair("dublincore", eventCatalog.toXmlString()));
      params.add(new BasicNameValuePair("wfproperties", toPropertyString(wfProperties)));
      put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException("Unable to assemble a remote scheduler request for updating event " + eventCatalog,
              e);
    }

    HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND, SC_UNAUTHORIZED);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          logger.info("Event {} was not found by the scheduler service", eventId);
          throw new NotFoundException("Event '" + eventId + "' not found on remote scheduler service!");
        } else if (SC_OK == response.getStatusLine().getStatusCode()) {
          logger.info("Event {} successfully updated with capture agent metadata.", eventId);
          return;
        } else if (SC_UNAUTHORIZED == response.getStatusLine().getStatusCode()) {
          logger.info("Unauthorized to update the event {}.", eventId);
          throw new UnauthorizedException("Unauthorized to update the event " + eventId);
        } else {
          throw new SchedulerException("Unexpected status code " + response.getStatusLine());
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to update event " + eventId + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to update  event " + eventId);
  }

  @Override
  public void removeEvent(long eventId) throws SchedulerException, NotFoundException, UnauthorizedException {
    logger.debug("Start removing event {} from scheduling service.", eventId);
    HttpDelete delete = new HttpDelete("/" + eventId);

    HttpResponse response = getResponse(delete, SC_OK, SC_NOT_FOUND, SC_UNAUTHORIZED);
    try {
      if (response != null && SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
        logger.info("Event {} was not found by the scheduler service", eventId);
        throw new NotFoundException("Event '" + eventId + "' not found on remote scheduler service!");
      } else if (response != null && SC_OK == response.getStatusLine().getStatusCode()) {
        logger.info("Event {} removed from scheduling service.", eventId);
        return;
      } else if (response != null && SC_UNAUTHORIZED == response.getStatusLine().getStatusCode()) {
        logger.info("Unauthorized to remove the event {}.", eventId);
        throw new UnauthorizedException("Unauthorized to remove the event " + eventId);
      }
    } catch (UnauthorizedException e) {
      throw e;
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to remove event " + eventId + " from the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to remove  event " + eventId);
  }

  @Override
  public DublinCoreCatalog getEventDublinCore(long eventId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(Long.toString(eventId).concat(".xml"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event catalog '" + eventId + "' not found on remote scheduler service!");
        } else {
          DublinCoreCatalog dublinCoreCatalog = DublinCores.read(response.getEntity().getContent());
          logger.info("Successfully get event dublincore {} from the remote scheduler service", eventId);
          return dublinCoreCatalog;
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to parse event dublincore from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event dublincore from remote scheduler service");
  }

  @Override
  public Properties getEventCaptureAgentConfiguration(long eventId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(Long.toString(eventId).concat("/agent.properties"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event capture agent configuration '" + eventId
                  + "' not found on remote scheduler service!");
        } else {
          Properties properties = new Properties();
          properties.load(response.getEntity().getContent());
          logger.info("Successfully get event capture agent configuration {} from the remote scheduler service",
                  eventId);
          return properties;
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to parse event capture agent configuration from remote scheduler service: "
              + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event capture agent configuration from remote scheduler service");
  }

  @Override
  public DublinCoreCatalogList search(SchedulerQuery query) throws SchedulerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DublinCoreCatalogList findConflictingEvents(String captureDeviceID, Date startDate, Date endDate)
          throws SchedulerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public DublinCoreCatalogList findConflictingEvents(String captureDeviceID, String rrule, Date startDate,
          Date endDate, long duration, String timezone) throws SchedulerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCalendar(SchedulerQuery filter) throws SchedulerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getScheduleLastModified(String agentId) throws SchedulerException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateEvents(List<Long> eventIds, DublinCoreCatalog eventCatalog) throws NotFoundException,
          SchedulerException, UnauthorizedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateAccessControlList(long eventId, AccessControlList accessControlList) throws NotFoundException,
          SchedulerException {
    HttpPut put = new HttpPut(UrlSupport.concat(Long.toString(eventId), "acl"));

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("acl", AccessControlParser.toJson(accessControlList)));
      put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException(
              "Unable to assemble a remote scheduler request for updating the access control status", e);
    }

    HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          logger.warn("Event with id {} was not found by the scheduler service", eventId);
          throw new NotFoundException("Event with id '" + eventId + "' not found on remote scheduler service!");
        } else if (SC_OK == response.getStatusLine().getStatusCode()) {
          logger.info("Event with id {} successfully updated access control list.", eventId);
          return;
        } else {
          throw new SchedulerException("Unexpected status code " + response.getStatusLine());
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to update event with id " + eventId + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to update  event with id " + eventId);
  }

  @Override
  public AccessControlList getAccessControlList(long eventId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(Long.toString(eventId).concat("/acl"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event '" + eventId + "' not found on remote scheduler service!");
        } else {
          String aclString = EntityUtils.toString(response.getEntity(), "UTF-8");
          AccessControlList accessControlList = AccessControlParser.parseAcl(aclString);
          logger.info("Successfully get event {} access control list from the remote scheduler service", eventId);
          return accessControlList;
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to get event access control list from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event access control list from remote scheduler service");
  }

  @Override
  public String getMediaPackageId(long eventId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(Long.toString(eventId).concat("/mediapackageId"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event '" + eventId + "' not found on remote scheduler service!");
        } else {
          String mediaPackageId = EntityUtils.toString(response.getEntity(), "UTF-8");
          logger.info("Successfully get event  {} mediapackage id from the remote scheduler service", eventId);
          return mediaPackageId;
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to get event mediapackage identifier from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event mediapackage identifier from remote scheduler service");
  }

  @Override
  public Long getEventId(String mediaPackageId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(mediaPackageId.concat("/eventId"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Scheduled event from mediapackage '" + mediaPackageId
                  + "' not found on remote scheduler service!");
        } else {
          Long eventId = Long.parseLong(EntityUtils.toString(response.getEntity(), "UTF-8"));
          logger.info("Successfully get scheduled event id {} from the remote scheduler service", eventId);
          return eventId;
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to get event identifier from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event identifier from remote scheduler service");
  }

  @Override
  public boolean isOptOut(String mediapackageId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(UrlSupport.concat(mediapackageId, "optOut"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else {
          String optOutString = EntityUtils.toString(response.getEntity(), "UTF-8");

          Boolean booleanObject = BooleanUtils.toBooleanObject(optOutString);
          if (booleanObject == null)
            throw new SchedulerException("Could not parse opt out status from the remote scheduler service: "
                    + optOutString);

          logger.info(
                  "Successfully get opt out status of event with mediapackage id {} from the remote scheduler service",
                  mediapackageId);
          return booleanObject.booleanValue();
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to get event opt out status from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event opt out status from remote scheduler service");
  }

  @Override
  public void updateOptOutStatus(String mediapackageId, boolean optedOut) throws NotFoundException, SchedulerException {
    HttpPut put = new HttpPut(UrlSupport.concat(mediapackageId, "optOut"));

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("optOut", Boolean.toString(optedOut)));
      put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException("Unable to assemble a remote scheduler request for updating the opt out status", e);
    }

    HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          logger.warn("Event with mediapackage id {} was not found by the scheduler service", mediapackageId);
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else if (SC_OK == response.getStatusLine().getStatusCode()) {
          logger.info("Event with mediapackage id {} successfully updated with opt out status.", mediapackageId);
          return;
        } else {
          throw new SchedulerException("Unexpected status code " + response.getStatusLine());
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to update event with mediapackage id " + mediapackageId
              + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to update  event with mediapackage id " + mediapackageId);
  }

  @Override
  public ReviewStatus getReviewStatus(String mediapackageId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(UrlSupport.concat(mediapackageId, "reviewStatus"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else {
          String reviewStatusString = EntityUtils.toString(response.getEntity(), "UTF-8");

          ReviewStatus reviewStatus;
          try {
            reviewStatus = ReviewStatus.valueOf(reviewStatusString);
          } catch (Exception e) {
            throw new SchedulerException("Could not parse review status from the remote scheduler service: "
                    + reviewStatusString);
          }
          logger.info(
                  "Successfully get review status of event with mediapackage id {} from the remote scheduler service",
                  mediapackageId);
          return reviewStatus;
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to get event review status from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event review status from remote scheduler service");
  }

  @Override
  public void updateReviewStatus(String mediapackageId, ReviewStatus reviewStatus) throws NotFoundException,
          SchedulerException {
    HttpPut put = new HttpPut(UrlSupport.concat(mediapackageId, "reviewStatus"));

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("reviewStatus", reviewStatus.toString()));
      put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException("Unable to assemble a remote scheduler request for updating the review status", e);
    }

    HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          logger.warn("Event with mediapackage id {} was not found by the scheduler service", mediapackageId);
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else if (SC_OK == response.getStatusLine().getStatusCode()) {
          logger.info("Event with mediapackage id {} successfully updated with review status.", mediapackageId);
          return;
        } else {
          throw new SchedulerException("Unexpected status code " + response.getStatusLine());
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to update event with mediapackage id " + mediapackageId
              + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to update  event with mediapackage id " + mediapackageId);
  }

  @Override
  public boolean isBlacklisted(String mediapackageId) throws NotFoundException, SchedulerException {
    HttpGet get = new HttpGet(UrlSupport.concat(mediapackageId, "blacklisted"));
    HttpResponse response = getResponse(get, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else {
          String blacklistString = EntityUtils.toString(response.getEntity(), "UTF-8");

          Boolean booleanObject = BooleanUtils.toBooleanObject(blacklistString);
          if (booleanObject == null)
            throw new SchedulerException("Could not parse blacklist status from the remote scheduler service: "
                    + blacklistString);

          logger.info(
                  "Successfully get blacklist status of event with mediapackage id {} from the remote scheduler service",
                  mediapackageId);
          return booleanObject.booleanValue();
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to get event blacklist status from remote scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to get event blacklist status from remote scheduler service");
  }

  @Override
  public void updateBlacklistStatus(String mediapackageId, boolean blacklisted) throws NotFoundException,
          SchedulerException {
    HttpPut put = new HttpPut(UrlSupport.concat(mediapackageId, "blacklisted"));

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("blacklisted", Boolean.toString(blacklisted)));
      put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException("Unable to assemble a remote scheduler request for updating the blacklist status", e);
    }

    HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          logger.warn("Event with mediapackage id {} was not found by the scheduler service", mediapackageId);
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else if (SC_OK == response.getStatusLine().getStatusCode()) {
          logger.info("Event with mediapackage id {} successfully updated with blacklist status.", mediapackageId);
          return;
        } else {
          throw new SchedulerException("Unexpected status code " + response.getStatusLine());
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to update event with mediapackage id " + mediapackageId
              + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to update event with mediapackage id " + mediapackageId);
  }

  @Override
  public void updateWorkflowConfig(String mediapackageId, Map<String, String> properties) throws NotFoundException,
          SchedulerException {
    HttpPut put = new HttpPut(UrlSupport.concat(mediapackageId, "workflowConfig"));

    try {
      List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
      params.add(new BasicNameValuePair("workflowConfig", toPropertyString(properties)));
      put.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
    } catch (Exception e) {
      throw new SchedulerException(
              "Unable to assemble a remote scheduler request for updating the workflow configuration", e);
    }

    HttpResponse response = getResponse(put, SC_OK, SC_NOT_FOUND);
    try {
      if (response != null) {
        if (SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
          logger.warn("Event with mediapackage id {} was not found by the scheduler service", mediapackageId);
          throw new NotFoundException("Event with mediapackage id '" + mediapackageId
                  + "' not found on remote scheduler service!");
        } else if (SC_OK == response.getStatusLine().getStatusCode()) {
          logger.info("Event with mediapackage id {} successfully updated with workflow configuration.", mediapackageId);
          return;
        } else {
          throw new SchedulerException("Unexpected status code " + response.getStatusLine());
        }
      }
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      throw new SchedulerException("Unable to update event with mediapackage id " + mediapackageId
              + " to the scheduler service: " + e);
    } finally {
      closeConnection(response);
    }
    throw new SchedulerException("Unable to update  event with mediapackage id " + mediapackageId);
  }

  private String toPropertyString(Map<String, String> properties) {
    StringBuilder wfPropertiesString = new StringBuilder();
    for (Map.Entry<String, String> entry : properties.entrySet())
      wfPropertiesString.append(entry.getKey() + "=" + entry.getValue() + "\n");
    return wfPropertiesString.toString();
  }

  @Override
  public void removeScheduledRecordingsBeforeBuffer(long buffer) throws SchedulerException {
    throw new UnsupportedOperationException();
  }

}
