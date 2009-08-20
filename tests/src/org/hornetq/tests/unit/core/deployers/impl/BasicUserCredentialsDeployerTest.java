/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.tests.unit.core.deployers.impl;

import org.hornetq.core.deployers.DeploymentManager;
import org.hornetq.core.deployers.impl.BasicUserCredentialsDeployer;
import org.hornetq.core.security.CheckType;
import org.hornetq.core.security.HornetQSecurityManager;
import org.hornetq.core.security.Role;
import org.hornetq.tests.util.UnitTestCase;
import org.hornetq.utils.XMLUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * tests BasicUserCredentialsDeployer
 *
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 */
public class BasicUserCredentialsDeployerTest extends UnitTestCase
{
   private BasicUserCredentialsDeployer deployer;
   
   FakeHornetQUpdateableSecurityManager securityManager;

   private static final String simpleSecurityXml = "<configuration>\n" +
                                                   "<defaultuser name=\"guest\" password=\"guest\">\n" +
                                                   "      <role name=\"guest\"/>\n" +
                                                   "   </defaultuser>" +
                                                   "</configuration>";

   private static final String singleUserXml = "<configuration>\n" +
                                               "      <user name=\"guest\" password=\"guest\">\n" +
                                               "         <role name=\"guest\"/>\n" +
                                               "      </user>\n" +
                                               "</configuration>";

   private static final String multipleUserXml = "<configuration>\n" +
                                                 "      <user name=\"guest\" password=\"guest\">\n" +
                                                 "         <role name=\"guest\"/>\n" +
                                                 "         <role name=\"foo\"/>\n" +
                                                 "      </user>\n" +
                                                 "    <user name=\"anotherguest\" password=\"anotherguest\">\n" +
                                                 "         <role name=\"anotherguest\"/>\n" +
                                                 "         <role name=\"foo\"/>\n" +
                                                 "         <role name=\"bar\"/>\n" +
                                                 "      </user>\n" +
                                                 "</configuration>";

   protected void setUp() throws Exception
   {
      super.setUp();
      DeploymentManager deploymentManager = new FakeDeploymentManager();
      securityManager = new FakeHornetQUpdateableSecurityManager();
      deployer = new BasicUserCredentialsDeployer(deploymentManager, securityManager);
   }

   protected void tearDown() throws Exception
   {
      deployer = null;

      super.tearDown();
   }

   private void deploy(String xml) throws Exception
   {
      NodeList children = XMLUtil.stringToElement(xml).getChildNodes();
      for (int i = 0; i < children.getLength(); i++)
      {
         Node node = children.item(i);
         if (node.getNodeName().equals("user") || node.getNodeName().equals("defaultuser"))
         {
            deployer.deploy(node);
         }
      }
   }

   private void undeploy(String xml) throws Exception
   {
      NodeList children = XMLUtil.stringToElement(xml).getChildNodes();
      for (int i = 0; i < children.getLength(); i++)
      {
         Node node = children.item(i);
         if (node.getNodeName().equals("user"))
         {
            deployer.undeploy(node);
         }
      }
   }

   public void testSimpleDefaultSecurity() throws Exception
   {
      deploy(simpleSecurityXml);
      assertEquals("guest", securityManager.defaultUser);
      User user = securityManager.users.get("guest");
      assertNotNull(user);
      assertEquals("guest",user.user);
      assertEquals("guest",user.password);
      List<String> roles = securityManager.roles.get("guest");
      assertNotNull(roles);
      assertEquals(1, roles.size());
      assertEquals("guest", roles.get(0));
   }

   public void testSingleUserDeploySecurity() throws Exception
   {      
      deploy(singleUserXml);
      assertNull(securityManager.defaultUser);
      User user = securityManager.users.get("guest");
      assertNotNull(user);
      assertEquals("guest",user.user);
      assertEquals("guest",user.password);
      List<String> roles = securityManager.roles.get("guest");
      assertNotNull(roles);
      assertEquals(1, roles.size());
      assertEquals("guest", roles.get(0));
   }

   public void testMultipleUserDeploySecurity() throws Exception
   {            
      deploy(multipleUserXml);
      assertNull(securityManager.defaultUser);
      User user = securityManager.users.get("guest");
      assertNotNull(user);
      assertEquals("guest",user.user);
      assertEquals("guest",user.password);
      List<String> roles = securityManager.roles.get("guest");
      assertNotNull(roles);
      assertEquals(2, roles.size());
      assertEquals("guest", roles.get(0));
      assertEquals("foo", roles.get(1));
      user = securityManager.users.get("anotherguest");
      assertNotNull(user);
      assertEquals("anotherguest",user.user);
      assertEquals("anotherguest",user.password);
      roles = securityManager.roles.get("anotherguest");
      assertNotNull(roles);
      assertEquals(3, roles.size());
      assertEquals("anotherguest", roles.get(0));
      assertEquals("foo", roles.get(1));
      assertEquals("bar", roles.get(2));
   }

   public void testUndeploy() throws Exception
   {      
      deploy(multipleUserXml);
      undeploy(singleUserXml);
      assertNull(securityManager.defaultUser);
      User user = securityManager.users.get("guest");
      assertNull(user);
      List<String> roles = securityManager.roles.get("guest");
      assertNull(roles);
      user = securityManager.users.get("anotherguest");
      assertNotNull(user);
      assertEquals("anotherguest",user.user);
      assertEquals("anotherguest",user.password);
      roles = securityManager.roles.get("anotherguest");
      assertNotNull(roles);
      assertEquals(3, roles.size());
      assertEquals("anotherguest", roles.get(0));
      assertEquals("foo", roles.get(1));
      assertEquals("bar", roles.get(2));
   }

   class FakeHornetQUpdateableSecurityManager implements HornetQSecurityManager
   {
      String defaultUser;

      private Map<String, User> users = new HashMap<String, User>();

      private Map<String, List<String>> roles = new HashMap<String, List<String>>();

      public void addUser(final String user, final String password)
      {
         if (user == null)
         {
            throw new IllegalArgumentException("User cannot be null");
         }
         if (password == null)
         {
            throw new IllegalArgumentException("password cannot be null");
         }
         users.put(user, new User(user, password));
      }

      public void removeUser(final String user)
      {
         users.remove(user);
         roles.remove(user);
      }

      public void addRole(final String user, final String role)
      {
         if (roles.get(user) == null)
         {
            roles.put(user, new ArrayList<String>());
         }
         roles.get(user).add(role);
      }

      public void removeRole(final String user, final String role)
      {
         if (roles.get(user) == null)
         {
            return;
         }
         roles.get(user).remove(role);
      }

      public void setDefaultUser(String username)
      {
         defaultUser = username;
      }

      public boolean validateUser(String user, String password)
      {
         return false;
      }

      public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType)
      {
         return false;
      }
      
      public void start()
      {         
      }
      
      public void stop()
      {         
      }
      
      public boolean isStarted()
      {
         return true;
      }
   }

   static class User
   {
      final String user;

      final String password;

      User(final String user, final String password)
      {
         this.user = user;
         this.password = password;
      }

      public boolean equals(Object o)
      {
         if (this == o)
         {
            return true;
         }
         if (o == null || getClass() != o.getClass())
         {
            return false;
         }

         User user1 = (User) o;

         if (!user.equals(user1.user))
         {
            return false;
         }

         return true;
      }

      public int hashCode()
      {
         return user.hashCode();
      }

      public boolean isValid(final String user, final String password)
      {
         if (user == null)
         {
            return false;
         }
         return this.user.equals(user) && this.password.equals(password);
      }
   }
}