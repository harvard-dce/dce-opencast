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

package org.opencastproject.authorization.xacml.manager.endpoint;

import static org.opencastproject.rest.RestServiceTestEnv.localhostRandomPort;

import org.opencastproject.archive.api.Archive;
import org.opencastproject.archive.api.HttpMediaPackageElementProvider;
import org.opencastproject.archive.api.Query;
import org.opencastproject.archive.api.ResultSet;
import org.opencastproject.archive.api.UriRewriter;
import org.opencastproject.archive.api.Version;
import org.opencastproject.archive.opencast.OpencastResultItem;
import org.opencastproject.archive.opencast.OpencastResultSet;
import org.opencastproject.authorization.xacml.manager.api.AclService;
import org.opencastproject.authorization.xacml.manager.api.AclServiceFactory;
import org.opencastproject.authorization.xacml.manager.impl.AclDb;
import org.opencastproject.authorization.xacml.manager.impl.AclServiceImpl;
import org.opencastproject.authorization.xacml.manager.impl.AclTransitionDb;
import org.opencastproject.authorization.xacml.manager.impl.persistence.JpaAclDb;
import org.opencastproject.authorization.xacml.manager.impl.persistence.OsgiJpaAclTransitionDb;
import org.opencastproject.distribution.download.DownloadDistributionServiceImpl;
import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageBuilderImpl;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.attachment.AttachmentImpl;
import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.search.api.SearchQuery;
import org.opencastproject.search.api.SearchService;
import org.opencastproject.security.api.AccessControlList;
import org.opencastproject.security.api.AclScope;
import org.opencastproject.security.api.AuthorizationService;
import org.opencastproject.security.api.DefaultOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.OrganizationDirectoryService;
import org.opencastproject.security.api.SecurityConstants;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.series.api.SeriesService;
import org.opencastproject.serviceregistry.api.IncidentService;
import org.opencastproject.serviceregistry.api.ServiceRegistry;
import org.opencastproject.serviceregistry.api.ServiceRegistryException;
import org.opencastproject.serviceregistry.api.ServiceRegistryInMemoryImpl;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.PathSupport;
import org.opencastproject.util.data.Tuple;
import org.opencastproject.util.persistence.PersistenceUtil;
import org.opencastproject.workflow.api.WorkflowService;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import org.easymock.EasyMock;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.junit.Ignore;

import java.beans.PropertyVetoException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Path;

// use base path /test to prevent conflicts with the production service
@Path("/test")
// put @Ignore here to prevent maven surefire from complaining about missing test methods
@Ignore
public class TestRestService extends AbstractAclServiceRestEndpoint {

  public static final URL BASE_URL = localhostRandomPort();

  // Declare this dependency static since the TestRestService gets instantiated multiple times.
  // Haven't found out who's responsible for this but that's the way it is.
  public static final AclServiceFactory aclServiceFactory;
  public static final SecurityService securityService;
  public static final SeriesService seriesService;
  public static final AuthorizationService authorizationService;
  public static final Archive<?> archive;
  public static final ServiceRegistry serviceRegistry;
  public static final DownloadDistributionServiceImpl distributionService = new DownloadDistributionServiceImpl();
  public static final MessageSender messageSender;
  private static final PersistenceProvider persistenceProvider = new PersistenceProvider();
  private static Map<String, Object> persistenceProps = new HashMap<String, Object>();

  static {
    SecurityService testSecurityService = EasyMock.createNiceMock(SecurityService.class);
    User user = new JaxbUser("admin", "test", new DefaultOrganization(), new JaxbRole(
            SecurityConstants.GLOBAL_ADMIN_ROLE, new DefaultOrganization()));
    EasyMock.expect(testSecurityService.getOrganization()).andReturn(new DefaultOrganization()).anyTimes();
    EasyMock.expect(testSecurityService.getUser()).andReturn(user).anyTimes();
    EasyMock.replay(testSecurityService);
    securityService = testSecurityService;
    authorizationService = newAuthorizationService();
    seriesService = newSeriesService();
    archive = newArchive();
    serviceRegistry = newServiceRegistry();
    messageSender = newMessageSender();
    aclServiceFactory = new AclServiceFactory() {
      @Override
      public AclService serviceFor(Organization org) {
        return new AclServiceImpl(new DefaultOrganization(), newAclPersistence(), newTransitionPersistence(),
                seriesService, archive, newSearchService(), newWorkflowService(), securityService,
                newHttpMediaPackageElementProvider(), authorizationService, distributionService, serviceRegistry,
                messageSender);
      }
    };

    long currentTime = System.currentTimeMillis();
    String storage = PathSupport.concat("target", "db" + currentTime + ".h2.db");

    ComboPooledDataSource pooledDataSource = new ComboPooledDataSource();
    try {
      pooledDataSource.setDriverClass("org.h2.Driver");
    } catch (PropertyVetoException e) {
      throw new RuntimeException(e);
    }
    pooledDataSource.setJdbcUrl("jdbc:h2:./target/db" + currentTime);
    pooledDataSource.setUser("sa");
    pooledDataSource.setPassword("sa");

    // Collect the persistence properties
    persistenceProps.put("javax.persistence.nonJtaDataSource", pooledDataSource);
    persistenceProps.put("eclipselink.ddl-generation", "create-tables");
    persistenceProps.put("eclipselink.ddl-generation.output-mode", "database");
  }

  @Override
  protected AclServiceFactory getAclServiceFactory() {
    return aclServiceFactory;
  }

  private static ServiceRegistry newServiceRegistry() {
    // DefaultOrganization defaultOrganization = new DefaultOrganization();
    // User anonymous = new JaxbUser("anonymous", defaultOrganization, new JaxbRole(
    // DefaultOrganization.DEFAULT_ORGANIZATION_ANONYMOUS, defaultOrganization));
    UserDirectoryService userDirectoryService = EasyMock.createMock(UserDirectoryService.class);
    // EasyMock.expect(userDirectoryService.loadUser((String) EasyMock.anyObject())).andReturn(anonymous).anyTimes();
    EasyMock.replay(userDirectoryService);

    // Organization organization = new DefaultOrganization();
    OrganizationDirectoryService organizationDirectoryService = EasyMock.createMock(OrganizationDirectoryService.class);
    // EasyMock.expect(organizationDirectoryService.getOrganization((String) EasyMock.anyObject()))
    // .andReturn(organization).anyTimes();
    EasyMock.replay(organizationDirectoryService);
    try {
      return new ServiceRegistryInMemoryImpl(distributionService, securityService, userDirectoryService,
              organizationDirectoryService, EasyMock.createNiceMock(IncidentService.class));
    } catch (ServiceRegistryException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected SecurityService getSecurityService() {
    return securityService;
  }

  @Override
  protected AuthorizationService getAuthorizationService() {
    return authorizationService;
  }

  @Override
  protected Archive<?> getArchive() {
    return archive;
  }

  @Override
  protected HttpMediaPackageElementProvider getHttpMediaPackageElementProvider() {
    org.opencastproject.archive.api.HttpMediaPackageElementProvider httpMediaPackageElementProvider = EasyMock
            .createNiceMock(HttpMediaPackageElementProvider.class);
    EasyMock.expect(httpMediaPackageElementProvider.getUriRewriter()).andReturn(new UriRewriter() {
      @Override
      public URI apply(Version version, MediaPackageElement mediaPackageElement) {
        return mediaPackageElement.getURI();
      }
    });
    EasyMock.replay(httpMediaPackageElementProvider);
    return httpMediaPackageElementProvider;
  }

  @Override
  protected SeriesService getSeriesService() {
    return seriesService;
  }

  private static org.opencastproject.archive.api.HttpMediaPackageElementProvider newHttpMediaPackageElementProvider() {
    return EasyMock.createNiceMock(org.opencastproject.archive.api.HttpMediaPackageElementProvider.class);
  }

  private static MessageSender newMessageSender() {
    return EasyMock.createNiceMock(MessageSender.class);
  }

  private static SearchService newSearchService() {
    org.opencastproject.search.api.SearchResultItem[] resultItems = new ArrayList<org.opencastproject.search.api.SearchResultItem>()
            .toArray(new org.opencastproject.search.api.SearchResultItem[0]);

    org.opencastproject.search.api.SearchResult searchResult = EasyMock
            .createNiceMock(org.opencastproject.search.api.SearchResult.class);
    EasyMock.expect(searchResult.getItems()).andReturn(resultItems).anyTimes();
    EasyMock.expect(searchResult.size()).andReturn(0L).anyTimes();
    EasyMock.replay(searchResult);

    SearchService searchService = EasyMock.createNiceMock(SearchService.class);
    EasyMock.expect(searchService.getByQuery((SearchQuery) EasyMock.anyObject())).andReturn(searchResult);
    EasyMock.replay(searchService);
    return searchService;
  }

  private static WorkflowService newWorkflowService() {
    return EasyMock.createNiceMock(WorkflowService.class);
  }

  private static AuthorizationService newAuthorizationService() {
    AccessControlList acl = new AccessControlList();
    Attachment attachment = new AttachmentImpl();
    MediaPackage mediapackage;
    try {
      mediapackage = new MediaPackageBuilderImpl().createNew();
    } catch (MediaPackageException e) {
      throw new RuntimeException(e);
    }
    AuthorizationService authorizationService = EasyMock.createNiceMock(AuthorizationService.class);
    EasyMock.expect(authorizationService.getActiveAcl((MediaPackage) EasyMock.anyObject()))
            .andReturn(Tuple.tuple(acl, AclScope.Series)).anyTimes();
    EasyMock.expect(
            authorizationService.setAcl((MediaPackage) EasyMock.anyObject(), (AclScope) EasyMock.anyObject(),
                    (AccessControlList) EasyMock.anyObject())).andReturn(Tuple.tuple(mediapackage, attachment));
    EasyMock.replay(authorizationService);

    return authorizationService;
  }

  private static Archive<?> newArchive() {
    OpencastResultItem searchResultItem = EasyMock.createNiceMock(OpencastResultItem.class);
    try {
      EasyMock.expect(searchResultItem.getMediaPackage()).andReturn(new MediaPackageBuilderImpl().createNew())
              .anyTimes();
    } catch (MediaPackageException e) {
      throw new RuntimeException(e);
    }
    EasyMock.replay(searchResultItem);

    final List<OpencastResultItem> list = new ArrayList<OpencastResultItem>();
    list.add(searchResultItem);

    OpencastResultSet searchResult = new OpencastResultSet() {
      @Override
      public List<OpencastResultItem> getItems() {
        return list;
      }

      @Override
      public String getQuery() {
        return null;
      }

      @Override
      public long getTotalSize() {
        return -1;
      }

      @Override
      public long getLimit() {
        return -1;
      }

      @Override
      public long getOffset() {
        return -1;
      }

      @Override
      public long getSearchTime() {
        return -1;
      }
    };

    Archive<ResultSet> archive = EasyMock.createNiceMock(Archive.class);
    EasyMock.expect(
            archive.find((Query) EasyMock.anyObject(),
                    (org.opencastproject.archive.api.UriRewriter) EasyMock.anyObject())).andReturn(searchResult)
            .anyTimes();
    EasyMock.replay(archive);
    return archive;
  }

  private static AclTransitionDb newTransitionPersistence() {
    OsgiJpaAclTransitionDb aclManagerDatabase = new OsgiJpaAclTransitionDb();
    aclManagerDatabase.setPersistenceProvider(persistenceProvider);
    aclManagerDatabase.setPersistenceProperties(persistenceProps);
    aclManagerDatabase.activate(null);
    return aclManagerDatabase;
  }

  private static AclDb newAclPersistence() {
    return new JpaAclDb(PersistenceUtil.newPersistenceEnvironment(persistenceProvider,
            "org.opencastproject.authorization.xacml.manager", persistenceProps));
  }

  private static SeriesService newSeriesService() {
    AccessControlList acl = new AccessControlList();
    SeriesService seriesService = EasyMock.createNiceMock(SeriesService.class);
    try {
      EasyMock.expect(seriesService.getSeriesAccessControl((String) EasyMock.anyObject())).andReturn(acl).anyTimes();
      EasyMock.expect(
              seriesService.updateAccessControl((String) EasyMock.anyObject(), (AccessControlList) EasyMock.anyObject()))
              .andThrow(new NotFoundException()).andReturn(true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    EasyMock.replay(seriesService);
    return seriesService;
  }

  @Override
  protected String getEndpointBaseUrl() {
    return BASE_URL.toString();
  }

}
