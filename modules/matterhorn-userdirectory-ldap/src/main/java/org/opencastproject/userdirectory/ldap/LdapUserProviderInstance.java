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

package org.opencastproject.userdirectory.ldap;

import org.opencastproject.security.api.CachingUserProviderMXBean;
import org.opencastproject.security.api.JaxbOrganization;
import org.opencastproject.security.api.JaxbRole;
import org.opencastproject.security.api.JaxbUser;
import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserProvider;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * A UserProvider that reads user roles from LDAP entries.
 */
public class LdapUserProviderInstance implements UserProvider, CachingUserProviderMXBean {

  /** The logger */
  private static final Logger logger = LoggerFactory.getLogger(LdapUserProviderInstance.class);

  public static final String PROVIDER_NAME = "ldap";

  /** The spring ldap userdetails service delegate */
  private LdapUserDetailsService delegate = null;

  /** The organization id */
  private Organization organization = null;

  /** Total number of requests made to load users */
  private AtomicLong requests = null;

  /** The number of requests made to ldap */
  private AtomicLong ldapLoads = null;

  /** A cache of users, which lightens the load on the LDAP server */
  private LoadingCache<String, Object> cache = null;

  /** A token to store in the miss cache */
  protected Object nullToken = new Object();

  /**
   * Constructs an ldap user provider with the needed settings.
   *
   * @param pid
   *          the pid of this service
   * @param organization
   *          the organization
   * @param searchBase
   *          the ldap search base
   * @param searchFilter
   *          the ldap search filter
   * @param url
   *          the url of the ldap server
   * @param userDn
   *          the user to authenticate as
   * @param password
   *          the user credentials
   * @param roleAttributesGlob
   *          the comma separate list of ldap attributes to treat as roles
   * @param cacheSize
   *          the number of users to cache
   * @param cacheExpiration
   *          the number of minutes to cache users
   */
  // CHECKSTYLE:OFF
  LdapUserProviderInstance(String pid, Organization organization, String searchBase, String searchFilter, String url,
          String userDn, String password, String roleAttributesGlob, int cacheSize, int cacheExpiration) {
    // CHECKSTYLE:ON
    this.organization = organization;
    logger.debug("Creating LdapUserProvider instance with pid=" + pid + ", and organization=" + organization
            + ", to LDAP server at url:  " + url);

    DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(url);
    if (StringUtils.isNotBlank(userDn)) {
      contextSource.setPassword(password);
      contextSource.setUserDn(userDn);
      // Required so that authentication will actually be used
      contextSource.setAnonymousReadOnly(false);
    } else {
      // No password set so try to connect anonymously.
      contextSource.setAnonymousReadOnly(true);
    }

    try {
      contextSource.afterPropertiesSet();
    } catch (Exception e) {
      throw new org.opencastproject.util.ConfigurationException("Unable to create a spring context source", e);
    }
    FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(searchBase, searchFilter, contextSource);
    userSearch.setReturningAttributes(roleAttributesGlob.split(","));
    this.delegate = new LdapUserDetailsService(userSearch);

    if (StringUtils.isNotBlank(roleAttributesGlob)) {
      LdapUserDetailsMapper mapper = new LdapUserDetailsMapper();
      mapper.setRoleAttributes(roleAttributesGlob.split(","));
      this.delegate.setUserDetailsMapper(mapper);
    }

    // Setup the caches
    cache = CacheBuilder.newBuilder().maximumSize(cacheSize).expireAfterWrite(cacheExpiration, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Object>() {
              @Override
              public Object load(String id) throws Exception {
                User user = loadUserFromLdap(id);
                return user == null ? nullToken : user;
              }
            });

    registerMBean(pid);
  }

  @Override
  public String getName() {
    return PROVIDER_NAME;
  }

  /**
   * Registers an MXBean.
   */
  protected void registerMBean(String pid) {
    // register with jmx
    requests = new AtomicLong();
    ldapLoads = new AtomicLong();
    try {
      ObjectName name;
      name = LdapUserProviderFactory.getObjectName(pid);
      Object mbean = this;
      MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
      try {
        mbs.unregisterMBean(name);
      } catch (InstanceNotFoundException e) {
        logger.debug(name + " was not registered");
      }
      mbs.registerMBean(mbean, name);
    } catch (Exception e) {
      logger.warn("Unable to register {} as an mbean: {}", this, e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.security.api.UserProvider#getOrganization()
   */
  @Override
  public String getOrganization() {
    return organization.getId();
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.security.api.UserProvider#loadUser(java.lang.String)
   */
  @Override
  public User loadUser(String userName) {
    logger.debug("LdapUserProvider is loading user " + userName);
    requests.incrementAndGet();
    try {
      // use #getUnchecked since the loader does not throw any checked exceptions
      Object user = cache.getUnchecked(userName);
      if (user == nullToken) {
        return null;
      } else {
        return (JaxbUser) user;
      }
    } catch (UncheckedExecutionException e) {
      logger.warn("Exception while loading user " + userName, e);
      return null;
    }
  }

  /**
   * Loads a user from LDAP.
   *
   * @param userName
   *          the username
   * @return the user
   */
  protected User loadUserFromLdap(String userName) {
    if (delegate == null || cache == null) {
      throw new IllegalStateException("The LDAP user detail service has not yet been configured");
    }
    ldapLoads.incrementAndGet();
    UserDetails userDetails = null;

    Thread currentThread = Thread.currentThread();
    ClassLoader originalClassloader = currentThread.getContextClassLoader();
    try {
      currentThread.setContextClassLoader(LdapUserProviderFactory.class.getClassLoader());
      try {
        userDetails = delegate.loadUserByUsername(userName);
      } catch (UsernameNotFoundException e) {
        cache.put(userName, nullToken);
        return null;
      }

      JaxbOrganization jaxbOrganization = JaxbOrganization.fromOrganization(organization);
      Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();
      Set<JaxbRole> roles = new HashSet<JaxbRole>();
      if (authorities != null) {
        for (GrantedAuthority authority : authorities) {
          roles.add(new JaxbRole(authority.getAuthority(), jaxbOrganization));
        }
      }
      User user = new JaxbUser(userDetails.getUsername(), PROVIDER_NAME, jaxbOrganization, roles);
      cache.put(userName, user);
      return user;
    } finally {
      currentThread.setContextClassLoader(originalClassloader);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see org.opencastproject.security.api.CachingUserProviderMXBean#getCacheHitRatio()
   */
  @Override
  public float getCacheHitRatio() {
    if (requests.get() == 0) {
      return 0;
    }
    return (float) (requests.get() - ldapLoads.get()) / requests.get();
  }

  @Override
  public Iterator<User> findUsers(String query, int offset, int limit) {
    if (query == null)
      throw new IllegalArgumentException("Query must be set");
    // TODO implement a LDAP wildcard search
    return Collections.<User> emptyList().iterator();
  }

  @Override
  public Iterator<User> getUsers() {
    // TODO implement LDAP get all users
    return Collections.<User> emptyList().iterator();
  }

  @Override
  public void invalidate(String userName) {
    cache.invalidate(userName);
  }

}
