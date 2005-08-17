/**
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */


package org.jboss.messaging.tools.jmx;

import org.jboss.resource.adapter.jdbc.local.LocalManagedConnectionFactory;
import org.jboss.resource.adapter.jdbc.remote.WrapperDataSourceService;
import org.jboss.resource.connectionmanager.TxConnectionManager;
import org.jboss.resource.connectionmanager.CachedConnectionManagerMBean;
import org.jboss.resource.connectionmanager.CachedConnectionManager;
import org.jboss.resource.connectionmanager.JBossManagedConnectionPool;
import org.jboss.system.ServiceController;
import org.jboss.system.Registry;
import org.jboss.tm.TxManager;
import org.jboss.logging.Logger;
import org.jboss.messaging.tools.jndi.InVMInitialContextFactory;
import org.jboss.messaging.tools.jndi.JNDIUtil;
import org.jboss.messaging.tools.jndi.InVMInitialContextFactoryBuilder;
import org.jboss.remoting.InvokerLocator;
import org.jboss.aop.AspectXmlLoader;
import org.jboss.jms.server.DestinationManager;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;
import javax.transaction.TransactionManager;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.Context;
import javax.naming.spi.NamingManager;

import org.hsqldb.Server;

import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.net.URL;

/**
 * Esentially an MBeanServer and a configurable set of services (TransactionManager, Remoting, etc)
 * plugged into it.
 */
public class ServiceContainer
{
   private static final Logger log = Logger.getLogger(ServiceContainer.class);

   public static ObjectName SERVICE_CONTROLLER_OBJECT_NAME;
   public static ObjectName TRANSACTION_MANAGER_OBJECT_NAME;
   public static ObjectName CACHED_CONNECTION_MANAGER_OBJECT_NAME;
   public static ObjectName CONNECTION_MANAGER_OBJECT_NAME;
   public static ObjectName MANAGED_CONNECTION_FACTORY_OBJECT_NAME;
   public static ObjectName MANAGED_CONNECTION_POOL_OBJECT_NAME;
   public static ObjectName WRAPPER_DATA_SOURCE_SERVICE_OBJECT_NAME;
   public static ObjectName REMOTING_OBJECT_NAME;

   static
   {
      try
      {
         SERVICE_CONTROLLER_OBJECT_NAME =
         new ObjectName("jboss.system:service=ServiceController");
         TRANSACTION_MANAGER_OBJECT_NAME =
         new ObjectName("jboss:service=TransactionManager");
         CACHED_CONNECTION_MANAGER_OBJECT_NAME =
         new ObjectName("jboss.jca:service=CachedConnectionManager");
         CONNECTION_MANAGER_OBJECT_NAME =
         new ObjectName("jboss.jca:name=DefaultDS,service=LocalTxCM");
         MANAGED_CONNECTION_FACTORY_OBJECT_NAME =
         new ObjectName("jboss.jca:name=DefaultDS,service=ManagedConnectionFactory");
         MANAGED_CONNECTION_POOL_OBJECT_NAME =
         new ObjectName("jboss.jca:name=DefaultDS,service=ManagedConnectionPool");
         WRAPPER_DATA_SOURCE_SERVICE_OBJECT_NAME =
         new ObjectName("jboss.jca:=DefaultDS,service=DataSourceBinding");
         REMOTING_OBJECT_NAME =
         new ObjectName("jboss.remoting:service=Connector,transport=socket");
      }
      catch(Exception e)
      {
         e.printStackTrace();
      }
   }

   private TransactionManager tm;

   private MBeanServer mbeanServer;
   private InitialContext initialContext;

   private String jndiNamingFactory;


   private boolean transaction;
   private boolean database;
   private boolean jca;
   private boolean remoting;

   private List toUnbindAtExit;

   /**
    * @param config - A comma separated list of services to be started. Available services:
    *        transaction, jca, database, remoting.  Example: "transaction,database,remoting"
    * @param tm - specifies a specific TransactionManager instance to bind into the mbeanServer.
    *        If null, the default JBoss TransactionManager implementation will be used.
    */
   public ServiceContainer(String config, TransactionManager tm) throws Exception
   {
      this.tm = tm;
      parseConfig(config);
      toUnbindAtExit = new ArrayList();
   }

   public void start() throws Exception
   {
      toUnbindAtExit.clear();

      jndiNamingFactory = System.getProperty("java.naming.factory.initial");

      //TODO: need to think more about this; if I don't do it, though, bind() fails because it tries to use "java.naming.provider.url"
      try
      {
         NamingManager.setInitialContextFactoryBuilder(new InVMInitialContextFactoryBuilder());
      }
      catch(IllegalStateException e)
      {
         // OK
      }

      Hashtable t = InVMInitialContextFactory.getJNDIEnvironment();
      System.setProperty("java.naming.factory.initial",
                         (String)t.get("java.naming.factory.initial"));

      initialContext = new InitialContext();

      mbeanServer = MBeanServerFactory.createMBeanServer("jboss");

      startServiceController();

      if (database)
      {
         startInVMDatabase();
      }
      if (transaction)
      {
         startTransactionManager();
      }
      if (jca)
      {
         startManagedConnectionFactory();
         startCachedConnectionManager();
         startManagedConnectionPool();
         startConnectionManager();
         startWrapperDataSourceService();
      }
      if (remoting)
      {
         startRemoting();
      }

      loadAspects();
      loadJNDIContexts();

      System.out.println("ServiceContainer started");
   }

   public void stop() throws Exception
   {
      unloadJNDIContexts();
      unloadAspects();

      stopService(REMOTING_OBJECT_NAME);
      stopService(WRAPPER_DATA_SOURCE_SERVICE_OBJECT_NAME);
      stopService(CONNECTION_MANAGER_OBJECT_NAME);
      stopService(MANAGED_CONNECTION_POOL_OBJECT_NAME);
      stopService(CACHED_CONNECTION_MANAGER_OBJECT_NAME);
      stopService(TRANSACTION_MANAGER_OBJECT_NAME);
      stopService(MANAGED_CONNECTION_FACTORY_OBJECT_NAME);
      if (database)
      {
         stopInVMDatabase();
      }
      stopServiceController();
      MBeanServerFactory.releaseMBeanServer(mbeanServer);
      initialContext.close();

      cleanJNDI();

      if (jndiNamingFactory != null)
      {
         System.setProperty("java.naming.factory.initial", jndiNamingFactory);
      }
      System.out.println("ServiceContainer stopped");
   }

   public DataSource getDataSource()
   {
      return null;
   }

   public UserTransaction getUserTransaction()
   {
      return null;
   }

   public Object getService(ObjectName on) throws Exception
   {
      return mbeanServer.invoke(on, "getInstance", new Object[0], new String[0]);
   }


   private void loadJNDIContexts() throws Exception
   {
      String[] names = {DestinationManager.DEFAULT_QUEUE_CONTEXT,
                        DestinationManager.DEFAULT_TOPIC_CONTEXT};

      for (int i = 0; i < names.length; i++)
      {
         try
         {
            initialContext.lookup(names[i]);
         }
         catch(NameNotFoundException e)
         {
            JNDIUtil.createContext(initialContext, names[i]);
            log.info("Created context /" + names[i]);
         }
      }
   }

   private void unloadJNDIContexts() throws Exception
   {
      Context c = (Context)initialContext.lookup("/topic");
      JNDIUtil.tearDownRecursively(c);
      c = (Context)initialContext.lookup("/queue");
      JNDIUtil.tearDownRecursively(c);
   }

   private void loadAspects() throws Exception
   {
      URL url = this.getClass().getClassLoader().getResource("jms-aop.xml");
      AspectXmlLoader.deployXML(url);
   }

   private void unloadAspects() throws Exception
   {
      URL url = this.getClass().getClassLoader().getResource("jms-aop.xml");
      AspectXmlLoader.undeployXML(url);
   }

   private void startServiceController() throws Exception
   {
      // I don't really need it, because I enforce dependencies by hand, but this will keep some
      // services happy.
      ServiceController sc = new ServiceController();
      mbeanServer.registerMBean(sc, SERVICE_CONTROLLER_OBJECT_NAME);
   }

   private void stopServiceController() throws Exception
   {
      mbeanServer.unregisterMBean(SERVICE_CONTROLLER_OBJECT_NAME);
   }

   private void startInVMDatabase() throws Exception
   {
      String[] args =
            {
               "-database.0", "mem:test",
               "-dbname.0", "memtest",
               "-trace", "false",
            };

      Server.main(args);
      log.info("started the database");
   }

   private void stopInVMDatabase() throws Exception
   {
      Class.forName("org.hsqldb.jdbcDriver" );
      Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "");
      Statement stat = conn.createStatement();
      stat.executeUpdate("SHUTDOWN");
      conn.close();
   }

   private void startTransactionManager() throws Exception
   {
      if (tm == null)
      {
         // the default JBoss TransactionManager
         tm = TxManager.getInstance();
      }

      TransactionManagerJMXWrapper mbean = new TransactionManagerJMXWrapper(tm);
      mbeanServer.registerMBean(mbean, TRANSACTION_MANAGER_OBJECT_NAME);
      mbeanServer.invoke(TRANSACTION_MANAGER_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + TRANSACTION_MANAGER_OBJECT_NAME);

      initialContext.bind("java:/TransactionManager", tm);
      toUnbindAtExit.add("java:/TransactionManager");

      log.info("bound java:/TransactionManager");

      // to get this to work I need to bind DTMTransactionFactory in JNDI
//      ClientUserTransaction singleton = ClientUserTransaction.getSingleton();
//      initialContext.bind("UserTransaction", singleton);
//      toUnbindAtExit.add("UserTransaction");
//      log.info("bound /UserTransaction");

   }

   private void startCachedConnectionManager() throws Exception
   {
      CachedConnectionManager ccm = new CachedConnectionManager();

      // dependencies
      ccm.setTransactionManagerServiceName(TRANSACTION_MANAGER_OBJECT_NAME);

      mbeanServer.registerMBean(ccm, CACHED_CONNECTION_MANAGER_OBJECT_NAME);
      mbeanServer.invoke(CACHED_CONNECTION_MANAGER_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + CACHED_CONNECTION_MANAGER_OBJECT_NAME);

   }

   private void startManagedConnectionFactory() throws Exception
   {
      LocalManagedConnectionFactory mcf = new LocalManagedConnectionFactory();
      mcf.setConnectionURL("jdbc:hsqldb:mem:test");
      mcf.setDriverClass("org.hsqldb.jdbcDriver");
      mcf.setUserName("sa");

      ManagedConnectionFactoryJMXWrapper mbean = new ManagedConnectionFactoryJMXWrapper(mcf);
      mbeanServer.registerMBean(mbean, MANAGED_CONNECTION_FACTORY_OBJECT_NAME);
      mbeanServer.invoke(MANAGED_CONNECTION_FACTORY_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + MANAGED_CONNECTION_FACTORY_OBJECT_NAME);
   }

   private void startManagedConnectionPool() throws Exception
   {
      JBossManagedConnectionPool mcp = new JBossManagedConnectionPool();
      mcp.setCriteria("ByContainer");

      // dependencies
      mcp.setManagedConnectionFactoryName(MANAGED_CONNECTION_FACTORY_OBJECT_NAME);

      mbeanServer.registerMBean(mcp, MANAGED_CONNECTION_POOL_OBJECT_NAME);
      mbeanServer.invoke(MANAGED_CONNECTION_POOL_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + MANAGED_CONNECTION_POOL_OBJECT_NAME);
   }

   private void startConnectionManager() throws Exception
   {
      TxConnectionManager cm = new TxConnectionManager();
      cm.preRegister(mbeanServer, CONNECTION_MANAGER_OBJECT_NAME);
      cm.setTrackConnectionByTx(true);
      cm.setLocalTransactions(true);

      // dependencies
      cm.setTransactionManagerService(TRANSACTION_MANAGER_OBJECT_NAME);
      cm.setCachedConnectionManager(CachedConnectionManagerMBean.OBJECT_NAME);
      cm.setManagedConnectionPool(MANAGED_CONNECTION_POOL_OBJECT_NAME);


      mbeanServer.registerMBean(cm, CONNECTION_MANAGER_OBJECT_NAME);
      mbeanServer.invoke(CONNECTION_MANAGER_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + CONNECTION_MANAGER_OBJECT_NAME);
   }

   private void startWrapperDataSourceService() throws Exception
   {
      WrapperDataSourceService wdss = new WrapperDataSourceService();
      wdss.setJndiName("java:/DefaultDS");

      // dependencies
      wdss.setConnectionManager(CONNECTION_MANAGER_OBJECT_NAME);
      ObjectName irrelevant = new ObjectName(":name=irrelevant");
      wdss.setJMXInvokerName(irrelevant);
      Registry.bind(irrelevant, new NoopInvoker());

      mbeanServer.registerMBean(wdss, WRAPPER_DATA_SOURCE_SERVICE_OBJECT_NAME);
      mbeanServer.invoke(WRAPPER_DATA_SOURCE_SERVICE_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + WRAPPER_DATA_SOURCE_SERVICE_OBJECT_NAME);
   }

   private void startRemoting() throws Exception
   {
      RemotingJMXWrapper mbean =
            new RemotingJMXWrapper(new InvokerLocator("socket://localhost:9890"));
      mbeanServer.registerMBean(mbean, REMOTING_OBJECT_NAME);
      mbeanServer.invoke(REMOTING_OBJECT_NAME, "start", new Object[0], new String[0]);
      log.info("started " + REMOTING_OBJECT_NAME);
   }

   private void stopService(ObjectName target) throws Exception
   {
      if (mbeanServer.isRegistered(target))
      {
         mbeanServer.invoke(target, "stop", new Object[0], new String[0]);
         mbeanServer.unregisterMBean(target);
         log.info("stopped " + target);
      }
   }

   private void cleanJNDI() throws Exception
   {
      InitialContext ic = new InitialContext();

      for(Iterator i = toUnbindAtExit.iterator(); i.hasNext(); )
      {
         String name = (String)i.next();
         ic.unbind(name);
      }
      ic.close();
   }

   private void parseConfig(String config)
   {
      config = config.toLowerCase();
      for (StringTokenizer st = new StringTokenizer(config, ", "); st.hasMoreTokens(); )
      {
         String tok = st.nextToken();
         if ("transaction".equals(tok))
         {
            transaction = true;
         }
         else if ("database".equals(tok))
         {
            database = true;
         }
         else if ("jca".equals(tok))
         {
            jca = true;
         }
         else if ("remoting".equals(tok))
         {
            remoting = true;
         }
      }
   }
}
