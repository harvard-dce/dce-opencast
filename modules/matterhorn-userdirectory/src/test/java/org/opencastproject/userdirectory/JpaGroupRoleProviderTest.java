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

package org.opencastproject.userdirectory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.opencastproject.util.data.Collections.set;
import static org.opencastproject.util.persistence.PersistenceUtil.newTestEntityManagerFactory;

import org.opencastproject.message.broker.api.MessageSender;
import org.opencastproject.security.api.Group;
import org.opencastproject.security.api.Role;
import org.opencastproject.security.api.SecurityConstants;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.UnauthorizedException;
import org.opencastproject.security.impl.jpa.JpaGroup;
import org.opencastproject.security.impl.jpa.JpaOrganization;
import org.opencastproject.security.impl.jpa.JpaRole;
import org.opencastproject.security.impl.jpa.JpaUser;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.data.Collections;

import org.apache.commons.collections.IteratorUtils;
import org.apache.http.HttpStatus;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;

public class JpaGroupRoleProviderTest {

  private JpaGroupRoleProvider provider = null;
  private static JpaOrganization org1 = new JpaOrganization("org1", "org1", "localhost", 80, "admin", "anon", null);
  private static JpaOrganization org2 = new JpaOrganization("org2", "org2", "127.0.0.1", 80, "admin", "anon", null);

  @Before
  public void setUp() throws Exception {
    JpaUser adminUser = new JpaUser("admin", "pass1", org1, "Admin", "admin@localhost", "opencast", true,
            Collections.set(new JpaRole(SecurityConstants.GLOBAL_ADMIN_ROLE, org1)));

    // Set the security sevice
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(adminUser).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(org1).anyTimes();
    EasyMock.replay(securityService);

    // Create the message sender service
    MessageSender messageSender = EasyMock.createNiceMock(MessageSender.class);
    messageSender.sendObjectMessage(EasyMock.anyObject(String.class),
            EasyMock.anyObject(MessageSender.DestinationType.class), EasyMock.anyObject(Serializable.class));
    EasyMock.expectLastCall();
    EasyMock.replay(messageSender);

    provider = new JpaGroupRoleProvider();
    provider.setSecurityService(securityService);
    provider.setMessageSender(messageSender);
    provider.setEntityManagerFactory(newTestEntityManagerFactory(JpaUserAndRoleProvider.PERSISTENCE_UNIT));
    provider.activate(null);
  }

  @After
  public void tearDown() throws Exception {
    provider.deactivate();
  }

  @Test
  public void testAddAndGetGroup() throws Exception {
    Set<JpaRole> authorities = new HashSet<JpaRole>();
    authorities.add(new JpaRole("ROLE_ASTRO_101_SPRING_2011_STUDENT", org1));
    Set<String> members = new HashSet<String>();
    members.add("admin");

    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", authorities, members);
    provider.addGroup(group);

    Group loadGroup = provider.loadGroup("test", org1.getId());
    Assert.assertNotNull(loadGroup);
    Assert.assertEquals(loadGroup.getGroupId(), loadGroup.getGroupId());
    Assert.assertEquals(loadGroup.getName(), loadGroup.getName());
    Assert.assertEquals(loadGroup.getDescription(), loadGroup.getDescription());
    Assert.assertEquals(loadGroup.getOrganization(), loadGroup.getOrganization());
    Assert.assertEquals(loadGroup.getRole(), loadGroup.getRole());
    Assert.assertEquals(loadGroup.getRoles(), loadGroup.getRoles());
    Assert.assertEquals(loadGroup.getMembers(), loadGroup.getMembers());

    Assert.assertNull("Loading 'does not exist' should return null",
            provider.loadGroup("does not exist", org1.getId()));
    Assert.assertNull("Loading 'does not exist' should return null", provider.loadGroup("user1", org2.getId()));
  }

  @Test(expected = UnauthorizedException.class)
  public void testAddGroupNotAllowedAsNonAdminUser() throws UnauthorizedException {
    JpaUser user = new JpaUser("user", "pass1", org1, "User", "user@localhost", "opencast", true,
            Collections.set(new JpaRole("ROLE_USER", org1)));

    // Set the security sevice
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(user).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(org1).anyTimes();
    EasyMock.replay(securityService);
    provider.setSecurityService(securityService);

    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", Collections.set(
            new JpaRole(SecurityConstants.GLOBAL_ADMIN_ROLE, org1)));
    provider.addGroup(group);
    fail("The group with admin role should not be created by an non admin user");
  }

  @Test
  public void testUpdateGroupNotAllowedAsNonAdminUser() throws UnauthorizedException {
    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", Collections.set(
            new JpaRole(SecurityConstants.GLOBAL_ADMIN_ROLE, org1)));
    try {
      provider.addGroup(group);
      Group loadGroup = provider.loadGroup(group.getGroupId(), group.getOrganization().getId());
      assertNotNull(loadGroup);
      assertEquals(loadGroup.getGroupId(), loadGroup.getGroupId());
    } catch (Exception e) {
      fail("The group schould be added");
    }

    JpaUser user = new JpaUser("user", "pass1", org1, "User", "user@localhost", "opencast", true,
            Collections.set(new JpaRole("ROLE_USER", org1)));

    // Set the security sevice
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(user).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(org1).anyTimes();
    EasyMock.replay(securityService);
    provider.setSecurityService(securityService);

    try {
      // try add ROLE_USER
      Response updateGroupResponse = provider.updateGroup(group.getGroupId(), group.getName(), group.getDescription(),
              "ROLE_USER, " + SecurityConstants.GLOBAL_ADMIN_ROLE, null);
      assertNotNull(updateGroupResponse);
      assertEquals(HttpStatus.SC_FORBIDDEN, updateGroupResponse.getStatus());

      // try remove ROLE_ADMIN
      updateGroupResponse = provider.updateGroup(group.getGroupId(), group.getName(), group.getDescription(),
              "ROLE_USER", null);
      assertNotNull(updateGroupResponse);
      assertEquals(HttpStatus.SC_FORBIDDEN, updateGroupResponse.getStatus());
    } catch (NotFoundException e) {
      fail("The existing group isn't found");
    }
  }

  @Test
  public void testRemoveGroupNotAllowedAsNonAdminUser() throws UnauthorizedException {
    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", Collections.set(
            new JpaRole(SecurityConstants.GLOBAL_ADMIN_ROLE, org1)));
    try {
      provider.addGroup(group);
      Group loadGroup = provider.loadGroup(group.getGroupId(), group.getOrganization().getId());
      assertNotNull(loadGroup);
      assertEquals(group.getGroupId(), loadGroup.getGroupId());
    } catch (Exception e) {
      fail("The group should be added");
    }

    JpaUser user = new JpaUser("user", "pass1", org1, "User", "user@localhost", "opencast", true,
            Collections.set(new JpaRole("ROLE_USER", org1)));

    // Set the security sevice
    SecurityService securityService = EasyMock.createNiceMock(SecurityService.class);
    EasyMock.expect(securityService.getUser()).andReturn(user).anyTimes();
    EasyMock.expect(securityService.getOrganization()).andReturn(org1).anyTimes();
    EasyMock.replay(securityService);
    provider.setSecurityService(securityService);

    Response removeGroupResponse = provider.removeGroup(group.getGroupId());
    assertNotNull(removeGroupResponse);
    assertEquals(HttpStatus.SC_FORBIDDEN, removeGroupResponse.getStatus());
  }

  @Test
  public void testDuplicateGroupCreation() {
    Response response = provider.createGroup("Test 1", "Test group", "ROLE_ASTRO_101_SPRING_2011_STUDENT", "admin");
    assertEquals(HttpStatus.SC_CREATED, response.getStatus());
    response = provider.createGroup("Test 1", "Test group 2", "ROLE_ASTRO_101_SPRING_2011_STUDENT", "admin");
    assertEquals(HttpStatus.SC_CONFLICT, response.getStatus());
  }

  @Test
  public void testDuplicateGroup() throws UnauthorizedException {
    Set<JpaRole> roles1 = set(new JpaRole("ROLE_ASTRO_101_SPRING_2011_STUDENT", org1));
    Set<JpaRole> roles2 = set(new JpaRole("ROLE_ASTRO_101_SPRING_2011_STUDENT", org2));
    Set<String> members = set("admin");
    provider.addGroup(new JpaGroup("test1", org1, "Test", "Test group", roles1, members));
    provider.addGroup(new JpaGroup("test1", org2, "Test 2", "Test group 2", roles2, members));
    assertEquals("Test", provider.loadGroup("test1", org1.getId()).getName());
    // duplicate group, but add group does an update so it will pass
    provider.addGroup(new JpaGroup("test1", org1, "Test 1", "Test group 1", roles1, members));
    assertEquals("Test 1", provider.loadGroup("test1", org1.getId()).getName());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testRoles() throws Exception {
    Set<JpaRole> authorities = new HashSet<JpaRole>();
    authorities.add(new JpaRole("ROLE_ASTRO_101_SPRING_2011_STUDENT", org1));
    authorities.add(new JpaRole("ROLE_ASTRO_109_SPRING_2012_STUDENT", org1));
    Set<String> members = new HashSet<String>();
    members.add("admin");

    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", authorities, members);
    provider.addGroup(group);

    authorities.clear();
    authorities.add(new JpaRole("ROLE_ASTRO_122_SPRING_2011_STUDENT", org1));
    authorities.add(new JpaRole("ROLE_ASTRO_124_SPRING_2012_STUDENT", org1));

    JpaGroup group2 = new JpaGroup("test2", org1, "Test2", "Test 2 group", authorities, members);
    provider.addGroup(group2);

    authorities.clear();
    authorities.add(new JpaRole("ROLE_ASTRO_134_SPRING_2011_STUDENT", org2));
    authorities.add(new JpaRole("ROLE_ASTRO_144_SPRING_2012_STUDENT", org2));

    JpaGroup group3 = new JpaGroup("test2", org2, "Test2", "Test 2 group", authorities, members);
    provider.addGroup(group3);

    List<Role> roles = IteratorUtils.toList(provider.getRoles());
    Assert.assertEquals("There should be four role", 6, roles.size());
    roles.contains(new JpaRole(group.getRole(), org1));
    roles.contains(new JpaRole(group2.getRole(), org1));
  }

  @Test
  public void testRolesForUser() throws UnauthorizedException {
    Set<JpaRole> authorities = new HashSet<JpaRole>();
    authorities.add(new JpaRole("ROLE_ASTRO_101_SPRING_2011_STUDENT", org1));
    authorities.add(new JpaRole("ROLE_ASTRO_109_SPRING_2012_STUDENT", org1));
    Set<String> members = new HashSet<String>();
    members.add("admin");

    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", authorities, members);
    provider.addGroup(group);

    authorities.clear();
    authorities.add(new JpaRole("ROLE_ASTRO_122_SPRING_2011_STUDENT", org1));
    authorities.add(new JpaRole("ROLE_ASTRO_124_SPRING_2012_STUDENT", org1));

    JpaGroup group2 = new JpaGroup("test2", org1, "Test2", "Test 2 group", authorities, members);
    provider.addGroup(group2);

    authorities.clear();
    authorities.add(new JpaRole("ROLE_ASTRO_134_SPRING_2011_STUDENT", org2));
    authorities.add(new JpaRole("ROLE_ASTRO_144_SPRING_2012_STUDENT", org2));

    JpaGroup group3 = new JpaGroup("test2", org2, "Test2", "Test 2 group", authorities, members);
    provider.addGroup(group3);

    List<Role> rolesForUser = provider.getRolesForUser("admin");
    Assert.assertEquals("There should be four roles", 6, rolesForUser.size());
    rolesForUser.contains(new JpaRole(group.getRole(), org1));
    rolesForUser.contains(new JpaRole(group2.getRole(), org1));
  }

  @Test
  public void testFindRoles() throws UnauthorizedException {
    Set<JpaRole> authorities = new HashSet<JpaRole>();
    authorities.add(new JpaRole("ROLE_ASTRO_101_SPRING_2011_STUDENT", org1));
    authorities.add(new JpaRole("ROLE_ASTRO_109_SPRING_2012_STUDENT", org1));
    Set<String> members = new HashSet<String>();
    members.add("admin");

    JpaGroup group = new JpaGroup("test", org1, "Test", "Test group", authorities, members);
    provider.addGroup(group);

    authorities.clear();
    authorities.add(new JpaRole("ROLE_ASTRO_122_SPRING_2011_STUDENT", org1));
    authorities.add(new JpaRole("ROLE_ASTRO_124_SPRING_2012_STUDENT", org1));

    JpaGroup group2 = new JpaGroup("test2", org1, "Test2", "Test 2 group", authorities, members);
    provider.addGroup(group2);

    authorities = new HashSet<JpaRole>();
    authorities.add(new JpaRole("ROLE_ASTRO_134_SPRING_2011_STUDENT", org2));
    authorities.add(new JpaRole("ROLE_ASTRO_144_SPRING_2012_STUDENT", org2));

    JpaGroup group3 = new JpaGroup("test2", org2, "Test2", "Test 2 group", authorities, members);
    provider.addGroup(group3);

    Assert.assertEquals(4, IteratorUtils.toList(provider.findRoles("%PrIn%", 0, 0)).size());
    Assert.assertEquals(1, IteratorUtils.toList(provider.findRoles("%PrIn%", 0, 1)).size());
    Role role = provider.findRoles("%24%SPrIn%", 0, 0).next();
    Assert.assertEquals("ROLE_ASTRO_124_SPRING_2012_STUDENT", role.getName());

    Assert.assertEquals(6, IteratorUtils.toList(provider.findRoles("%oLe%", 0, 0)).size());
    Assert.assertEquals(2, IteratorUtils.toList(provider.findRoles("%olE%", 1, 2)).size());
  }

}
