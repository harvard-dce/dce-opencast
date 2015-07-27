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

package org.opencastproject.authorization.xacml.manager.impl;

import static org.opencastproject.authorization.xacml.manager.impl.Util.getArchiveMp;
import static org.opencastproject.authorization.xacml.manager.impl.Util.toAcl;
import static org.opencastproject.mediapackage.MediaPackageSupport.getId;
import static org.opencastproject.util.data.Collections.list;
import static org.opencastproject.util.data.Monadics.mlist;
import static org.opencastproject.workflow.api.ConfiguredWorkflowRef.toConfiguredWorkflow;
import static org.opencastproject.workflow.handler.distribution.EngagePublicationChannel.CHANNEL_ID;

import org.opencastproject.archive.api.Archive;
import org.opencastproject.archive.api.HttpMediaPackageElementProvider;
import org.opencastproject.archive.base.QueryBuilder;
import org.opencastproject.authorization.xacml.manager.api.AclService;
import org.opencastproject.authorization.xacml.manager.api.AclServiceException;
import org.opencastproject.authorization.xacml.manager.api.EpisodeACLTransition;
import org.opencastproject.authorization.xacml.manager.api.ManagedAcl;
import org.opencastproject.authorization.xacml.manager.api.SeriesACLTransition;
import org.opencastproject.authorization.xacml.manager.api.TransitionQuery;
import org.opencastproject.authorization.xacml.manager.api.TransitionResult;
import org.opencastproject.distribution.api.DistributionException;
import org.opencastproject.distribution.api.DistributionService;
import org.opencastproject.job.api.Job;
import org.opencastproject.job.api.JobBarrier;
import org.opencastproject.job.api.JobBarrier.Result;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementParser;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.message.broker.api.acl.AclItem;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.search.api.SearchResultItem;
import org.opencastproject.search.api.SearchService;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.AclScope;
import org.opencastproject.security.api.AuthorizationService;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Function;
import org.opencastproject.util.data.Option;
import org.opencastproject.workflow.api.ConfiguredWorkflow;
import org.opencastproject.workflow.api.ConfiguredWorkflowRef;
import org.opencastproject.workflow.api.WorkflowService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Organization bound impl. */
public final class AclServiceImpl implements AclService {
  /** Logging utility */
  private static final Logger logger = LoggerFactory.getLogger(AclServiceImpl.class);

  /** Context */
  private final Organization organization;

  /** Service dependencies */
  private final AclTransitionDb persistence;
  private final AclDb aclDb;
  private final SeriesService seriesService;
  private final Archive<?> archive;
  private final SearchService searchService;
  private final AuthorizationService authorizationService;
  private final WorkflowService workflowService;
  private final SecurityService securityService;
  private final HttpMediaPackageElementProvider archiveHttpMediaPackageElementProvider;
  private final DistributionService distributionService;
  private final ServiceRegistry serviceRegistry;
  private final MessageSender messageSender;

  public AclServiceImpl(Organization organization, AclDb aclDb, AclTransitionDb transitionDb,
          SeriesService seriesService, Archive<?> archive, SearchService searchService,
          WorkflowService workflowService, SecurityService securityService,
          HttpMediaPackageElementProvider archiveHttpMediaPackageElementProvider,
          AuthorizationService authorizationService, DistributionService distributionService,
          ServiceRegistry serviceRegistry, MessageSender messageSender) {
    this.organization = organization;
    this.persistence = transitionDb;
    this.aclDb = aclDb;
    this.seriesService = seriesService;
    this.archive = archive;
    this.searchService = searchService;
    this.workflowService = workflowService;
    this.securityService = securityService;
    this.archiveHttpMediaPackageElementProvider = archiveHttpMediaPackageElementProvider;
    this.authorizationService = authorizationService;
    this.distributionService = distributionService;
    this.serviceRegistry = serviceRegistry;
    this.messageSender = messageSender;
  }

  @Override
  public EpisodeACLTransition addEpisodeTransition(String episodeId, Option<Long> managedAclId, Date at,
          Option<ConfiguredWorkflowRef> workflow) throws AclServiceException {
    return persistence.storeEpisodeAclTransition(organization, episodeId, at, managedAclId, workflow);
  }

  @Override
  public SeriesACLTransition addSeriesTransition(String seriesId, long managedAclId, Date at, boolean override,
          Option<ConfiguredWorkflowRef> workflow) throws AclServiceException {
    return persistence.storeSeriesAclTransition(organization, seriesId, at, managedAclId, override, workflow);
  }

  @Override
  public EpisodeACLTransition updateEpisodeTransition(long transitionId, Option<Long> managedAclId, Date at,
          Option<ConfiguredWorkflowRef> workflow) throws AclServiceException, NotFoundException {
    return persistence.updateEpisodeAclTransition(organization, transitionId, at, managedAclId, workflow);
  }

  @Override
  public SeriesACLTransition updateSeriesTransition(long transitionId, long managedAclId, Date at,
          Option<ConfiguredWorkflowRef> workflow, boolean override) throws AclServiceException, NotFoundException {
    return persistence.updateSeriesAclTransition(organization, transitionId, at, managedAclId, override, workflow);
  }

  @Override
  public void deleteEpisodeTransition(long transitionId) throws NotFoundException, AclServiceException {
    persistence.deleteEpisodeAclTransition(organization, transitionId);
  }

  @Override
  public void deleteSeriesTransition(long transitionId) throws NotFoundException, AclServiceException {
    persistence.deleteSeriesAclTransition(organization, transitionId);
  }

  @Override
  public void deleteEpisodeTransitions(String episodeId) throws AclServiceException, NotFoundException {
    List<EpisodeACLTransition> transitions = persistence.getEpisodeAclTransitions(organization, episodeId);
    for (EpisodeACLTransition transition : transitions) {
      persistence.deleteEpisodeAclTransition(organization, transition.getTransitionId());
    }
  }

  @Override
  public void deleteSeriesTransitions(String seriesId) throws AclServiceException, NotFoundException {
    List<SeriesACLTransition> transitions = persistence.getSeriesAclTransitions(organization, seriesId);
    for (SeriesACLTransition transition : transitions) {
      persistence.deleteSeriesAclTransition(organization, transition.getTransitionId());
    }
  }

  @Override
  public TransitionResult getTransitions(TransitionQuery query) throws AclServiceException {
    return persistence.getByQuery(organization, query);
  }

  @Override
  public SeriesACLTransition markSeriesTransitionAsCompleted(long transitionId) throws AclServiceException,
  NotFoundException {
    return persistence.markSeriesTransitionAsCompleted(organization, transitionId);
  }

  @Override
  public EpisodeACLTransition markEpisodeTransitionAsCompleted(long transitionId) throws AclServiceException,
  NotFoundException {
    return persistence.markEpisodeTransitionAsCompleted(organization, transitionId);
  }

  @Override
  public boolean applyAclToEpisode(String episodeId, AccessControlList acl, Option<ConfiguredWorkflowRef> workflow)
          throws AclServiceException {
    try {
      Option<MediaPackage> mediaPackage = Option.<MediaPackage> none();
      if (archive != null)
        mediaPackage = getFromArchiveByMpId(episodeId);

      Option<AccessControlList> aclOpt = Option.option(acl);
      // the episode service is the source of authority for the retrieval of media packages
      for (final MediaPackage episodeSvcMp : mediaPackage) {
        final String episodeSvcMpId = episodeSvcMp.getIdentifier().toString();
        aclOpt.fold(new Option.EMatch<AccessControlList>() {
          // set the new episode ACL
          @Override
          public void esome(final AccessControlList acl) {
            // update in episode service
            MediaPackage mp = authorizationService.setAcl(episodeSvcMp, AclScope.Episode, acl).getA();
            if (archive != null)
              archive.add(mp);
            // update in search service
            // cannot just add the media package retrieved from the episode service but have to use
            // the one from the search service
            getFromSearchServiceByMpId(episodeSvcMpId).fold(new Option.EMatch<MediaPackage>() {
              @Override
              protected void esome(MediaPackage searchSvcMp) {
                try {
                  Attachment episodeXACML = authorizationService.setAcl(searchSvcMp, AclScope.Episode, acl).getB();

                  // Distribute the updated XACML file
                  Job distributionJob = distributionService.distribute(CHANNEL_ID, searchSvcMp,
                          episodeXACML.getIdentifier());
                  JobBarrier barrier = new JobBarrier(serviceRegistry, distributionJob);
                  Result jobResult = barrier.waitForJobs();
                  if (jobResult.getStatus().get(distributionJob).equals(Job.Status.FINISHED)) {
                    searchSvcMp.remove(episodeXACML);
                    searchSvcMp.add(MediaPackageElementParser.getFromXml(serviceRegistry
                            .getJob(distributionJob.getId()).getPayload()));
                  } else {
                    logger.error("Unable to distribute XACML {}", episodeXACML.getIdentifier());
                  }
                  Job addSearchJob = searchService.add(searchSvcMp);
                  barrier = new JobBarrier(serviceRegistry, addSearchJob);
                  barrier.waitForJobs();
                } catch (Exception e) {
                  logger.warn(e.getMessage());
                }
              }

              @Override
              protected void enone() {
                logger.info("Search does not contain media package {}", episodeSvcMpId);
              }
            });
          }

          // if none EpisodeACLTransition#isDelete returns true so delete the episode ACL
          @Override
          public void enone() {
            // update in episode service
            MediaPackage mp = authorizationService.removeAcl(episodeSvcMp, AclScope.Episode);
            if (archive != null)
              archive.add(mp);
            // update in search service
            // cannot just add the media package retrieved from the episode service but have to use
            // the one from the search service
            for (MediaPackage searchSvcMp : getFromSearchServiceByMpId(episodeSvcMpId)) {
              try {
                // Retract the updated XACML file
                retractXacmlElements(searchSvcMp, AclScope.Episode);
                Job addSearchJob = searchService.add(authorizationService.removeAcl(searchSvcMp, AclScope.Episode));
                JobBarrier barrier = new JobBarrier(serviceRegistry, addSearchJob);
                barrier.waitForJobs();
              } catch (Exception e) {
                logger.warn("Cannot add media package to search service");
              }
            }
          }

        });
        // apply optional workflow
        for (ConfiguredWorkflowRef workflowRef : workflow)
          applyWorkflow(list(episodeSvcMp), workflowRef);
        return true;
      }
      // not found
      return false;
    } catch (Exception e) {
      logger.error("Error applying episode ACL", e);
      throw new AclServiceException(e);
    }
  }

  @Override
  public boolean applyAclToEpisode(String episodeId, Option<ManagedAcl> managedAcl,
          Option<ConfiguredWorkflowRef> workflow) throws AclServiceException {
    return applyAclToEpisode(episodeId, managedAcl.map(toAcl).getOrElseNull(), workflow);
  }

  /** Update the ACL of an episode. */
  @Override
  public void applyEpisodeAclTransition(final EpisodeACLTransition t) throws AclServiceException {
    applyAclToEpisode(t.getEpisodeId(), t.getAccessControlList(), t.getWorkflow());
    try {
      // mark as done
      // todo If acl application fails the transition will not be marked as done. Depending on the
      // failure cause the application of the transition will be tried forever.
      markEpisodeTransitionAsCompleted(t.getTransitionId());
    } catch (NotFoundException e) {
      throw new AclServiceException(e);
    }
  }

  private void retractXacmlElements(MediaPackage searchSvcMp, AclScope scope) throws DistributionException {
    List<Attachment> aclAttachments = authorizationService.getAclAttachments(searchSvcMp, Option.some(scope));
    for (Attachment xacml : aclAttachments) {
      Job retractJob = distributionService.retract(CHANNEL_ID, searchSvcMp, xacml.getIdentifier());
      JobBarrier barrier = new JobBarrier(serviceRegistry, retractJob);
      Result jobResult = barrier.waitForJobs();
      if (!jobResult.getStatus().get(retractJob).equals(Job.Status.FINISHED))
        logger.error("Unable to retract XACML {}", xacml.getIdentifier());
    }
  }

  @Override
  public boolean applyAclToSeries(String seriesId, AccessControlList acl, boolean override,
          Option<ConfiguredWorkflowRef> workflow) throws AclServiceException {
    try {
      if (override) {
        // delete acls before calling seriesService.updateAccessControl to avoid
        // possible interference since a call to this method triggers update event handlers
        // which run on a separate thread. This must be considered a design smell since it
        // requires knowledge of the services implementation.
        //
        // delete in episode service
        List<MediaPackage> mediaPackages = new ArrayList<MediaPackage>();
        if (archive != null)
          mediaPackages = getFromArchiveBySeriesId(seriesId);

        for (MediaPackage mp : mediaPackages) {
          // remove episode xacml and update in archive service
          if (archive != null)
            archive.add(authorizationService.removeAcl(mp, AclScope.Episode));
        }
        // delete in search service
        for (MediaPackage mp : getFromSearchServiceBySeriesId(seriesId)) {
          // Retract the updated XACML file
          retractXacmlElements(mp, AclScope.Episode);
          Job addSearchJob = searchService.add(authorizationService.removeAcl(mp, AclScope.Episode));
          JobBarrier barrier = new JobBarrier(serviceRegistry, addSearchJob);
          barrier.waitForJobs();
        }
      }
      // update in series service
      // this will in turn update the search service by the SeriesUpdatedEventHandler
      // and the episode service by the EpisodesPermissionsUpdatedEventHandler
      try {
        seriesService.updateAccessControl(seriesId, acl);
      } catch (NotFoundException e) {
        return false;
      }
      return true;
    } catch (Exception e) {
      logger.error("Error applying series ACL", e);
      throw new AclServiceException(e);
    }
  }

  @Override
  public boolean applyAclToSeries(String seriesId, ManagedAcl managedAcl, boolean override,
          Option<ConfiguredWorkflowRef> workflow) throws AclServiceException {
    return applyAclToSeries(seriesId, managedAcl.getAcl(), override, workflow);
  }

  /** Update the ACL of a series. */
  @Override
  public void applySeriesAclTransition(SeriesACLTransition t) throws AclServiceException {
    applyAclToSeries(t.getSeriesId(), t.getAccessControlList(), t.isOverride(), t.getWorkflow());
    try {
      // mark as done
      // todo If acl application fails the transition will not be marked as done. Depending on the
      // failure cause the application of the transition will be tried forever.
      markSeriesTransitionAsCompleted(t.getTransitionId());
    } catch (NotFoundException e) {
      throw new AclServiceException(e);
    }
  }

  /**
   * Return media package with id <code>mpId</code> from episode service.
   *
   * @return single element list or empty list
   */
  private Option<MediaPackage> getFromArchiveByMpId(String mpId) {
    return mlist(
            archive.find(QueryBuilder.query().mediaPackageId(mpId).onlyLastVersion(true),
                    archiveHttpMediaPackageElementProvider.getUriRewriter()).getItems()).map(getArchiveMp).option();
  }

  /** Return all media packages of a series from the episode service. */
  private List<MediaPackage> getFromArchiveBySeriesId(String seriesId) {
    return mlist(
            archive.find(QueryBuilder.query().seriesId(seriesId).onlyLastVersion(true),
                    archiveHttpMediaPackageElementProvider.getUriRewriter()).getItems()).map(getArchiveMp).value();
  }

  /**
   * Return media package with id <code>mpId</code> from search service.
   */
  private Option<MediaPackage> getFromSearchServiceByMpId(String mpId) {
    final SearchQuery q = new SearchQuery().withId(mpId);
    return mlist(searchService.getByQuery(q).getItems()).map(extractMediaPackage).headOpt();
  }

  /**
   * Return media package with id <code>mpId</code> from search service.
   */
  private List<MediaPackage> getFromSearchServiceBySeriesId(String seriesId) {
    final SearchQuery q = new SearchQuery().withSeriesId(seriesId);
    return mlist(searchService.getByQuery(q).getItems()).map(extractMediaPackage).value();
  }

  /** Extract a media package from a search result item. */
  private final Function<SearchResultItem, MediaPackage> extractMediaPackage = new Function<SearchResultItem, MediaPackage>() {
    @Override
    public MediaPackage apply(SearchResultItem item) {
      return item.getMediaPackage();
    }
  };

  /** Apply a workflow to a list of media packages. */
  private void applyWorkflow(final List<MediaPackage> mps, final ConfiguredWorkflowRef workflowRef) {
    toConfiguredWorkflow(workflowService, workflowRef).fold(new Option.Match<ConfiguredWorkflow, Void>() {
      @Override
      public Void some(ConfiguredWorkflow workflow) {
        logger.info("Apply optional workflow {}", workflow.getWorkflowDefinition().getId());
        if (archive != null)
          archive.applyWorkflow(workflow, archiveHttpMediaPackageElementProvider.getUriRewriter(), mlist(mps)
                  .map(getId).value());
        return null;
      }

      @Override
      public Void none() {
        logger.warn("{} does not exist", workflowRef.getWorkflowId());
        return null;
      }
    });
  }

  @Override
  public List<ManagedAcl> getAcls() {
    return aclDb.getAcls(organization);
  }

  @Override
  public Option<ManagedAcl> getAcl(long id) {
    return aclDb.getAcl(organization, id);
  }

  @Override
  public boolean updateAcl(ManagedAcl acl) {
    Option<ManagedAcl> oldName = getAcl(acl.getId());
    boolean updateAcl = aclDb.updateAcl(acl);
    if (updateAcl) {
      if (oldName.isSome() && !(oldName.get().getName().equals(acl.getName()))) {
        AclItem aclItem = AclItem.update(oldName.get().getName(), acl.getName());
        messageSender.sendObjectMessage(AclItem.ACL_QUEUE, MessageSender.DestinationType.Queue, aclItem);
      }
    }
    return updateAcl;
  }

  @Override
  public Option<ManagedAcl> createAcl(AccessControlList acl, String name) {
    Option<ManagedAcl> createAcl = aclDb.createAcl(organization, acl, name);
    if (createAcl.isSome()) {
      AclItem aclItem = AclItem.create(createAcl.get().getName());
      messageSender.sendObjectMessage(AclItem.ACL_QUEUE, MessageSender.DestinationType.Queue, aclItem);
    }
    return createAcl;
  }

  @Override
  public boolean deleteAcl(long id) throws AclServiceException, NotFoundException {
    final TransitionQuery query = TransitionQuery.query().withDone(false).withAclId(id);
    final TransitionResult result = persistence.getByQuery(organization, query);
    if (result.getEpisodeTransistions().size() > 0 || result.getSeriesTransistions().size() > 0)
      return false;
    Option<ManagedAcl> deletedAcl = getAcl(id);
    if (aclDb.deleteAcl(organization, id)) {
      if (deletedAcl.isSome()) {
        AclItem aclItem = AclItem.delete(deletedAcl.get().getName());
        messageSender.sendObjectMessage(AclItem.ACL_QUEUE, MessageSender.DestinationType.Queue, aclItem);
      }
      return true;
    }
    throw new NotFoundException("Managed acl with id " + id + " not found.");
  }
}
