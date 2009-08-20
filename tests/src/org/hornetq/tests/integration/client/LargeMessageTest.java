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

package org.hornetq.tests.integration.client;

import java.util.HashMap;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import junit.framework.AssertionFailedError;

import org.hornetq.core.buffers.ChannelBuffers;
import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientConsumerInternal;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.message.Message;
import org.hornetq.core.remoting.spi.HornetQBuffer;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.Queue;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.tests.integration.largemessage.LargeMessageTestBase;
import org.hornetq.utils.DataConstants;
import org.hornetq.utils.SimpleString;

/**
 * A LargeMessageTest
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * 
 * Created 29-Sep-08 4:04:10 PM
 *
 *
 */
public class LargeMessageTest extends LargeMessageTestBase
{

   // Constants -----------------------------------------------------

   final static int RECEIVE_WAIT_TIME = 60000;

   private final int LARGE_MESSAGE_SIZE = 20 * 1024;

   // Attributes ----------------------------------------------------

   static final SimpleString ADDRESS = new SimpleString("SimpleAddress");

   // Static --------------------------------------------------------
   private final Logger log = Logger.getLogger(LargeMessageTest.class);

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testDLALargeMessage() throws Exception
   {
      final int messageSize = 50000;

      ClientSession session = null;

      try
      {
         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.createQueue(ADDRESS, ADDRESS, true);
         session.createQueue(ADDRESS, ADDRESS.concat("-2"), true);

         SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");

         AddressSettings addressSettings = new AddressSettings();

         addressSettings.setDeadLetterAddress(ADDRESS_DLA);
         addressSettings.setMaxDeliveryAttempts(1);

         server.getAddressSettingsRepository().addMatch("*", addressSettings);

         session.createQueue(ADDRESS_DLA, ADDRESS_DLA, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         Message clientFile = createLargeClientMessage(session, messageSize, true);

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS_DLA);

         ClientConsumer consumerRollback = session.createConsumer(ADDRESS);
         ClientMessage msg1 = consumerRollback.receive(1000);
         assertNotNull(msg1);
         msg1.acknowledge();
         session.rollback();
         consumerRollback.close();

         msg1 = consumer.receive(10000);

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg1.getBody().readByte());
         }

         session.close();
         server.stop();

         server = createServer(true);

         server.start();

         sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.start();

         consumer = session.createConsumer(ADDRESS_DLA);

         msg1 = consumer.receive(10000);

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg1.getBody().readByte());
         }

         msg1.acknowledge();

         session.commit();

         validateNoFilesOnLargeDir(1);

         consumer = session.createConsumer(ADDRESS.concat("-2"));

         msg1 = consumer.receive(10000);

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg1.getBody().readByte());
         }

         msg1.acknowledge();

         session.commit();

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   public void testDLAOnExpiry() throws Exception
   {
      final int messageSize = 50000;

      ClientSession session = null;

      try
      {
         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.createQueue(ADDRESS, ADDRESS, true);

         SimpleString ADDRESS_DLA = ADDRESS.concat("-dla");
         SimpleString ADDRESS_EXPIRY = ADDRESS.concat("-expiry");

         AddressSettings addressSettings = new AddressSettings();

         addressSettings.setDeadLetterAddress(ADDRESS_DLA);
         addressSettings.setExpiryAddress(ADDRESS_EXPIRY);
         addressSettings.setMaxDeliveryAttempts(1);

         server.getAddressSettingsRepository().addMatch("*", addressSettings);

         session.createQueue(ADDRESS_DLA, ADDRESS_DLA, true);
         session.createQueue(ADDRESS_EXPIRY, ADDRESS_EXPIRY, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         Message clientFile = createLargeClientMessage(session, messageSize, true);
         clientFile.setExpiration(System.currentTimeMillis());

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumerExpired = session.createConsumer(ADDRESS);
         // to kick expiry quicker than waiting reaper thread
         assertNull(consumerExpired.receive(1000));
         consumerExpired.close();

         ClientConsumer consumerExpiry = session.createConsumer(ADDRESS_EXPIRY);

         ClientMessage msg1 = consumerExpiry.receive(5000);
         assertNotNull(msg1);
         msg1.acknowledge();

         session.rollback();

         for (int j = 0; j < messageSize; j++)
         {
            assertEquals(getSamplebyte(j), msg1.getBody().readByte());
         }

         consumerExpiry.close();

         for (int i = 0; i < 10; i++)
         {

            consumerExpiry = session.createConsumer(ADDRESS_DLA);

            msg1 = consumerExpiry.receive(5000);
            assertNotNull(msg1);
            msg1.acknowledge();

            session.rollback();

            for (int j = 0; j < messageSize; j++)
            {
               assertEquals(getSamplebyte(j), msg1.getBody().readByte());
            }

            consumerExpiry.close();
         }

         session.close();
         server.stop();

         server = createServer(true);

         server.start();

         sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.start();

         consumerExpiry = session.createConsumer(ADDRESS_DLA);

         msg1 = consumerExpiry.receive(5000);
         assertNotNull(msg1);
         msg1.acknowledge();

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg1.getBody().readByte());
         }

         session.commit();

         consumerExpiry.close();

         session.commit();

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   public void testExpiryLargeMessage() throws Exception
   {
      final int messageSize = 50000;

      ClientSession session = null;

      try
      {
         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.createQueue(ADDRESS, ADDRESS, true);

         SimpleString ADDRESS_EXPIRY = ADDRESS.concat("-expiry");

         AddressSettings addressSettings = new AddressSettings();

         addressSettings.setExpiryAddress(ADDRESS_EXPIRY);

         server.getAddressSettingsRepository().addMatch("*", addressSettings);

         session.createQueue(ADDRESS_EXPIRY, ADDRESS_EXPIRY, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         Message clientFile = createLargeClientMessage(session, messageSize, true);

         clientFile.setExpiration(System.currentTimeMillis());

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS_EXPIRY);

         // Creating a consumer just to make the expiry process go faster and not have to wait for the reaper
         ClientConsumer consumer2 = session.createConsumer(ADDRESS);
         assertNull(consumer2.receive(1000));

         ClientMessage msg1 = consumer.receive(50000);

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg1.getBody().readByte());
         }

         session.close();
         server.stop();

         server = createServer(true);

         server.start();

         sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.start();

         consumer = session.createConsumer(ADDRESS_EXPIRY);

         msg1 = consumer.receive(10000);

         assertNotNull(msg1);

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg1.getBody().readByte());
         }

         msg1.acknowledge();

         session.commit();

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   public void testResendSmallStreamMessage() throws Exception
   {
      internalTestResendMessage(50000);
   }

   public void testResendLargeStreamMessage() throws Exception
   {
      internalTestResendMessage(150 * 1024);
   }

   public void internalTestResendMessage(long messageSize) throws Exception
   {
      ClientSession session = null;

      try
      {
         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         session = sf.createSession(false, false, false);

         session.createQueue(ADDRESS, ADDRESS, true);

         SimpleString ADDRESS2 = ADDRESS.concat("-2");

         session.createQueue(ADDRESS2, ADDRESS2, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         ClientProducer producer2 = session.createProducer(ADDRESS2);

         Message clientFile = createLargeClientMessage(session, messageSize, false);

         producer.send(clientFile);

         session.commit();

         session.start();

         ClientConsumer consumer = session.createConsumer(ADDRESS);
         ClientConsumer consumer2 = session.createConsumer(ADDRESS2);

         ClientMessage msg1 = consumer.receive(10000);
         msg1.acknowledge();

         producer2.send(msg1);

         try
         {
            producer2.send(msg1);
            fail("Expected Exception");
         }
         catch (Throwable e)
         {
         }

         session.commit();

         ClientMessage msg2 = consumer2.receive(10000);

         assertNotNull(msg2);

         msg2.acknowledge();

         session.commit();

         assertEquals(messageSize, msg2.getBodySize());

         for (int i = 0; i < messageSize; i++)
         {
            assertEquals(getSamplebyte(i), msg2.getBody().readByte());
         }

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   public void testFilePersistenceOneHugeMessage() throws Exception
   {
      testChunks(false,
                 false,
                 true,
                 true,
                 false,
                 false,
                 false,
                 false,
                 1,
                 100 * 1024l * 1024l,
                 RECEIVE_WAIT_TIME,
                 0,
                 10 * 1024 * 1024,
                 1024 * 1024);
   }

   public void testFilePersistenceOneMessageStreaming() throws Exception
   {
      testChunks(false, false, true, true, false, false, false, false, 1, 100 * 1024l * 1024l, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceSmallMessageStreaming() throws Exception
   {
      testChunks(false, false, true, true, false, false, false, false, 100, 1024, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceOneHugeMessageConsumer() throws Exception
   {
      testChunks(false,
                 false,
                 true,
                 true,
                 false,
                 false,
                 false,
                 true,
                 1,
                 100 * 1024 * 1024,
                 120000,
                 0,
                 10 * 1024 * 1024,
                 1024 * 1024);
   }

   public void testFilePersistence() throws Exception
   {
      testChunks(false, true, false, true, false, false, true, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceConsumer() throws Exception
   {
      testChunks(false, true, false, true, false, false, true, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceXA() throws Exception
   {
      testChunks(true, true, false, true, false, false, true, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceXAStream() throws Exception
   {
      testChunks(true, false, true, true, false, false, false, false, 1, 1024 * 1024, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceXAConsumer() throws Exception
   {
      testChunks(true, true, false, true, false, false, true, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlocked() throws Exception
   {
      testChunks(false, true, false, true, false, true, true, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedConsumer() throws Exception
   {
      testChunks(false, true, false, true, false, true, true, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedXA() throws Exception
   {
      testChunks(true, true, false, true, false, true, true, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedXAConsumer() throws Exception
   {
      testChunks(true, true, false, true, false, true, true, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedPreACK() throws Exception
   {
      testChunks(false, true, false, true, true, true, true, false, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedPreACKConsumer() throws Exception
   {
      testChunks(false, true, false, true, true, true, true, true, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedPreACKXA() throws Exception
   {
      testChunks(true, true, false, true, true, true, true, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceBlockedPreACKXAConsumer() throws Exception
   {
      testChunks(true, true, false, true, true, true, true, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testFilePersistenceDelayed() throws Exception
   {
      testChunks(false, true, false, true, false, false, false, false, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 2000);
   }

   public void testFilePersistenceDelayedConsumer() throws Exception
   {
      testChunks(false, true, false, true, false, false, false, true, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 2000);
   }

   public void testFilePersistenceDelayedXA() throws Exception
   {
      testChunks(true, true, false, true, false, false, false, false, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 2000);
   }

   public void testFilePersistenceDelayedXAConsumer() throws Exception
   {
      testChunks(true, true, false, true, false, false, false, true, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 2000);
   }

   public void testNullPersistence() throws Exception
   {
      testChunks(false, true, false, false, false, false, true, true, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testNullPersistenceConsumer() throws Exception
   {
      testChunks(false, true, false, false, false, false, true, true, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testNullPersistenceXA() throws Exception
   {
      testChunks(true, true, false, false, false, false, true, false, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testNullPersistenceXAConsumer() throws Exception
   {
      testChunks(true, true, false, false, false, false, true, true, 1, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 0);
   }

   public void testNullPersistenceDelayed() throws Exception
   {
      testChunks(false, true, false, false, false, false, false, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 100);
   }

   public void testNullPersistenceDelayedConsumer() throws Exception
   {
      testChunks(false, true, false, false, false, false, false, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 100);
   }

   public void testNullPersistenceDelayedXA() throws Exception
   {
      testChunks(true, true, false, false, false, false, false, false, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 100);
   }

   public void testNullPersistenceDelayedXAConsumer() throws Exception
   {
      testChunks(true, true, false, false, false, false, false, true, 100, LARGE_MESSAGE_SIZE, RECEIVE_WAIT_TIME, 100);
   }

   public void testPageOnLargeMessage() throws Exception
   {
      testPageOnLargeMessage(true, false);
   }

   public void testPageOnLargeMessageNullPersistence() throws Exception
   {
      testPageOnLargeMessage(false, false);

   }

   public void testSendSmallMessageXA() throws Exception
   {
      testChunks(true, true, false, true, false, false, true, false, 100, 4, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendSmallMessageXAConsumer() throws Exception
   {
      testChunks(true, true, false, true, false, false, true, true, 100, 4, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendSmallMessageNullPersistenceXA() throws Exception
   {
      testChunks(true, true, false, false, false, false, true, false, 100, 100, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendSmallMessageNullPersistenceXAConsumer() throws Exception
   {
      testChunks(true, true, false, false, false, false, true, true, 100, 100, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendRegularMessageNullPersistenceDelayed() throws Exception
   {
      testChunks(false, true, false, false, false, false, false, false, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessageNullPersistenceDelayedConsumer() throws Exception
   {
      testChunks(false, true, false, false, false, false, false, true, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessageNullPersistenceDelayedXA() throws Exception
   {
      testChunks(true, true, false, false, false, false, false, false, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessageNullPersistenceDelayedXAConsumer() throws Exception
   {
      testChunks(true, true, false, false, false, false, false, true, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessagePersistence() throws Exception
   {
      testChunks(false, true, false, true, false, false, true, false, 100, 100, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendRegularMessagePersistenceConsumer() throws Exception
   {
      testChunks(false, true, false, true, false, false, true, true, 100, 100, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendRegularMessagePersistenceXA() throws Exception
   {
      testChunks(true, true, false, true, false, false, true, false, 100, 100, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendRegularMessagePersistenceXAConsumer() throws Exception
   {
      testChunks(true, true, false, true, false, false, true, true, 100, 100, RECEIVE_WAIT_TIME, 0);
   }

   public void testSendRegularMessagePersistenceDelayed() throws Exception
   {
      testChunks(false, true, false, true, false, false, false, false, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessagePersistenceDelayedConsumer() throws Exception
   {
      testChunks(false, true, false, true, false, false, false, true, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessagePersistenceDelayedXA() throws Exception
   {
      testChunks(false, true, false, true, false, false, false, false, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testSendRegularMessagePersistenceDelayedXAConsumer() throws Exception
   {
      testChunks(false, true, false, true, false, false, false, true, 100, 100, RECEIVE_WAIT_TIME, 1000);
   }

   public void testTwoBindingsTwoStartedConsumers() throws Exception
   {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted

      try
      {

         server = createServer(true);

         server.start();

         SimpleString queue[] = new SimpleString[] { new SimpleString("queue1"), new SimpleString("queue2") };

         ClientSessionFactory sf = createInVMFactory();

         ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(ADDRESS, queue[0], null, true);
         session.createQueue(ADDRESS, queue[1], null, true);

         int numberOfBytes = 400000;

         Message clientFile = createLargeClientMessage(session, numberOfBytes);

         ClientProducer producer = session.createProducer(ADDRESS);

         session.start();

         producer.send(clientFile);

         producer.close();

         ClientConsumer consumer = session.createConsumer(queue[1]);
         ClientMessage msg = consumer.receive(RECEIVE_WAIT_TIME);
         assertNull(consumer.receive(1000));
         assertNotNull(msg);

         msg.acknowledge();
         consumer.close();

         log.debug("Stopping");

         session.stop();

         ClientConsumer consumer1 = session.createConsumer(queue[0]);

         session.start();

         msg = consumer1.receive(RECEIVE_WAIT_TIME);
         assertNotNull(msg);
         msg.acknowledge();
         consumer1.close();

         session.commit();

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   public void testTwoBindingsAndRestart() throws Exception
   {
      testTwoBindings(true);
   }

   public void testTwoBindingsNoRestart() throws Exception
   {
      testTwoBindings(false);
   }

   public void testTwoBindings(final boolean restart) throws Exception
   {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted

      try
      {

         server = createServer(true);

         server.start();

         SimpleString queue[] = new SimpleString[] { new SimpleString("queue1"), new SimpleString("queue2") };

         ClientSessionFactory sf = createInVMFactory();

         ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(ADDRESS, queue[0], null, true);
         session.createQueue(ADDRESS, queue[1], null, true);

         int numberOfBytes = 400000;

         Message clientFile = createLargeClientMessage(session, numberOfBytes);

         ClientProducer producer = session.createProducer(ADDRESS);
         producer.send(clientFile);

         producer.close();

         readMessage(session, queue[1], numberOfBytes);

         if (restart)
         {
            session.close();

            server.stop();

            log.info("Restartning");

            server = createServer(true);

            server.start();

            sf = createInVMFactory();

            session = sf.createSession(null, null, false, true, true, false, 0);
         }

         readMessage(session, queue[0], numberOfBytes);

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   public void testSendRollbackXA() throws Exception
   {
      internalTestSendRollback(true);
   }

   public void testSendRollback() throws Exception
   {
      internalTestSendRollback(false);
   }

   private void internalTestSendRollback(final boolean isXA) throws Exception
   {

      ClientSession session = null;

      try
      {
         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         session = sf.createSession(isXA, false, false);

         session.createQueue(ADDRESS, ADDRESS, true);

         Xid xid = null;

         if (isXA)
         {
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }

         ClientProducer producer = session.createProducer(ADDRESS);

         Message clientFile = createLargeClientMessage(session, 50000, false);

         for (int i = 0; i < 1; i++)
         {
            producer.send(clientFile);
         }

         if (isXA)
         {
            session.end(xid, XAResource.TMSUCCESS);
            session.prepare(xid);
            session.rollback(xid);
         }
         else
         {
            session.rollback();
         }

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   public void testSimpleRollback() throws Exception
   {
      simpleRollbackInternalTest(false);
   }

   public void testSimpleRollbackXA() throws Exception
   {
      simpleRollbackInternalTest(true);
   }

   public void simpleRollbackInternalTest(final boolean isXA) throws Exception
   {
      // there are two bindings.. one is ACKed, the other is not, the server is restarted
      // The other binding is acked... The file must be deleted

      try
      {

         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         ClientSession session = sf.createSession(isXA, false, false);

         Xid xid = null;

         if (isXA)
         {
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }

         session.createQueue(ADDRESS, ADDRESS, null, true);

         int numberOfBytes = 200000;

         session.start();

         log.info("Session started");

         ClientProducer producer = session.createProducer(ADDRESS);

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         for (int n = 0; n < 10; n++)
         {
            Message clientFile = createLargeClientMessage(session, numberOfBytes, n % 2 == 0);

            producer.send(clientFile);

            assertNull(consumer.receiveImmediate());

            if (isXA)
            {
               session.end(xid, XAResource.TMSUCCESS);
               session.rollback(xid);
               xid = newXID();
               session.start(xid, XAResource.TMNOFLAGS);
            }
            else
            {
               session.rollback();
            }

            clientFile = createLargeClientMessage(session, numberOfBytes, n % 2 == 0);

            producer.send(clientFile);

            assertNull(consumer.receiveImmediate());

            if (isXA)
            {
               session.end(xid, XAResource.TMSUCCESS);
               session.commit(xid, true);
               xid = newXID();
               session.start(xid, XAResource.TMNOFLAGS);
            }
            else
            {
               session.commit();
            }

            for (int i = 0; i < 2; i++)
            {

               ClientMessage clientMessage = consumer.receive(5000);

               assertNotNull(clientMessage);

               assertEquals(numberOfBytes, clientMessage.getBody().writerIndex());

               clientMessage.acknowledge();

               if (isXA)
               {
                  if (i == 0)
                  {
                     session.end(xid, XAResource.TMSUCCESS);
                     session.prepare(xid);
                     session.rollback(xid);
                     xid = newXID();
                     session.start(xid, XAResource.TMNOFLAGS);
                  }
                  else
                  {
                     session.end(xid, XAResource.TMSUCCESS);
                     session.commit(xid, true);
                     xid = newXID();
                     session.start(xid, XAResource.TMNOFLAGS);
                  }
               }
               else
               {
                  if (i == 0)
                  {
                     session.rollback();
                  }
                  else
                  {
                     session.commit();
                  }
               }
            }
         }

         session.close();

         validateNoFilesOnLargeDir();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   public void testBufferMultipleLargeMessages() throws Exception
   {
      ClientSession session = null;
      HornetQServer server = null;

      final int SIZE = 10 * 1024;
      final int NUMBER_OF_MESSAGES = 30;
      try
      {

         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         sf.setMinLargeMessageSize(1024);
         sf.setConsumerWindowSize(1024 * 1024);

         session = sf.createSession(null, null, false, false, false, false, 0);

         session.createQueue(ADDRESS, ADDRESS, null, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
         {
            Message clientFile = session.createClientMessage(true);
            clientFile.setBodyInputStream(createFakeLargeStream(SIZE));
            producer.send(clientFile);

         }
         session.commit();
         producer.close();

         session.start();

         ClientConsumerInternal consumer = (ClientConsumerInternal)session.createConsumer(ADDRESS);

         // Wait the consumer to be complete with 10 messages before getting others
         long timeout = System.currentTimeMillis() + 10000;
         while (consumer.getBufferSize() < NUMBER_OF_MESSAGES && timeout > System.currentTimeMillis())
         {
            Thread.sleep(10);
         }
         assertEquals(NUMBER_OF_MESSAGES, consumer.getBufferSize());

         // Reads the messages, rollback.. read them again
         for (int trans = 0; trans < 2; trans++)
         {

            for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
            {
               ClientMessage msg = consumer.receive(10000);
               assertNotNull(msg);

               // it will ignore the buffer (not read it) on the first try
               if (trans == 0)
               {
                  for (int byteRead = 0; byteRead < SIZE; byteRead++)
                  {
                     assertEquals(getSamplebyte(byteRead), msg.getBody().readByte());
                  }
               }

               msg.acknowledge();
            }
            if (trans == 0)
            {
               session.rollback();
            }
            else
            {
               session.commit();
            }
         }

         assertGlobalSize(server);
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getMessageCount());

      }
      finally
      {
         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   public void testReceiveMultipleMessages() throws Exception
   {
      ClientSession session = null;
      HornetQServer server = null;

      final int SIZE = 10 * 1024;
      final int NUMBER_OF_MESSAGES = 1000;
      try
      {

         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         sf.setMinLargeMessageSize(1024);
         sf.setConsumerWindowSize(1024 * 1024);

         session = sf.createSession(null, null, false, false, false, false, 0);

         session.createQueue(ADDRESS, ADDRESS, null, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
         {
            Message clientFile = session.createClientMessage(true);
            clientFile.setBodyInputStream(createFakeLargeStream(SIZE));
            producer.send(clientFile);

         }
         session.commit();
         producer.close();

         session.start();

         // Reads the messages, rollback.. read them again
         for (int trans = 0; trans < 2; trans++)
         {

            ClientConsumerInternal consumer = (ClientConsumerInternal)session.createConsumer(ADDRESS);

            // Wait the consumer to be complete with 10 messages before getting others
            long timeout = System.currentTimeMillis() + 10000;
            while (consumer.getBufferSize() < 10 && timeout > System.currentTimeMillis())
            {
               Thread.sleep(10);
            }

            for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
            {
               ClientMessage msg = consumer.receive(10000);
               assertNotNull(msg);

               // it will ignore the buffer (not read it) on the first try
               if (trans == 0)
               {
                  for (int byteRead = 0; byteRead < SIZE; byteRead++)
                  {
                     assertEquals(getSamplebyte(byteRead), msg.getBody().readByte());
                  }
               }

               msg.acknowledge();
            }
            if (trans == 0)
            {
               session.rollback();
            }
            else
            {
               session.commit();
            }

            consumer.close();
         }

         assertGlobalSize(server);
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getMessageCount());

      }
      finally
      {
         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   public void testSendStreamingSingleMessage() throws Exception
   {
      ClientSession session = null;
      HornetQServer server = null;

      final int SIZE = 10 * 1024 * 1024;
      try
      {

         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         sf.setMinLargeMessageSize(100 * 1024);

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(ADDRESS, ADDRESS, null, true);

         Message clientFile = session.createClientMessage(true);
         clientFile.setBodyInputStream(createFakeLargeStream(SIZE));

         ClientProducer producer = session.createProducer(ADDRESS);

         session.start();

         log.debug("Sending");
         producer.send(clientFile);

         producer.close();

         log.debug("Waiting");

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         ClientMessage msg2 = consumer.receive(10000);

         msg2.acknowledge();

         msg2.setOutputStream(createFakeOutputStream());
         assertTrue(msg2.waitOutputStreamCompletion(60000));

         // for (int i = 0; i < SIZE; i++)
         // {
         // byte value = msg2.getBody().readByte();
         // assertEquals("Error position " + i, (byte)'a', value);
         // }

         session.commit();

         assertGlobalSize(server);
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getMessageCount());

      }
      finally
      {
         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   /** Receive messages but never reads them, leaving the buffer pending */
   public void testIgnoreStreaming() throws Exception
   {
      ClientSession session = null;
      HornetQServer server = null;

      final int SIZE = 10 * 1024;
      final int NUMBER_OF_MESSAGES = 1;
      try
      {

         server = createServer(true);

         server.start();

         ClientSessionFactory sf = createInVMFactory();

         sf.setMinLargeMessageSize(1024);

         session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(ADDRESS, ADDRESS, null, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
         {
            Message msg = session.createClientMessage(true);
            msg.setBodyInputStream(createFakeLargeStream(SIZE));
            msg.putIntProperty(new SimpleString("key"), i);
            producer.send(msg);

            log.debug("Sent msg " + i);
         }

         session.start();

         log.debug("Sending");

         producer.close();

         log.debug("Waiting");

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         for (int i = 0; i < NUMBER_OF_MESSAGES; i++)
         {
            ClientMessage msg = consumer.receive(50000);
            assertNotNull(msg);

            assertEquals(i, msg.getProperty(new SimpleString("key")));

            msg.acknowledge();
         }

         consumer.close();

         session.commit();

         assertGlobalSize(server);
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         assertEquals(0, ((Queue)server.getPostOffice().getBinding(ADDRESS).getBindable()).getMessageCount());

         log.debug("Thread done");
      }
      finally
      {
         try
         {
            session.close();
         }
         catch (Throwable ignored)
         {
         }

         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      clearData();
      log.info("\n*********************************************************************************\n Starting " + getName() +
               "\n*********************************************************************************");
   }

   @Override
   protected void tearDown() throws Exception
   {
      log.info("\n*********************************************************************************\nDone with  " + getName() +
               "\n*********************************************************************************");
      super.tearDown();
   }

   protected void testPageOnLargeMessage(final boolean realFiles, final boolean sendBlocking) throws Exception
   {
      Configuration config = createDefaultConfig();

      final int PAGE_MAX = 20 * 1024;

      final int PAGE_SIZE = 10 * 1024;

      server = createServer(realFiles, config, PAGE_SIZE, PAGE_MAX, new HashMap<String, AddressSettings>());

      server.start();

      final int numberOfBytes = 1024;

      final int numberOfBytesBigMessage = 400000;

      try
      {
         ClientSessionFactory sf = createInVMFactory();

         if (sendBlocking)
         {
            sf.setBlockOnNonPersistentSend(true);
            sf.setBlockOnPersistentSend(true);
            sf.setBlockOnAcknowledge(true);
         }

         ClientSession session = sf.createSession(null, null, false, true, true, false, 0);

         session.createQueue(ADDRESS, ADDRESS, null, true);

         ClientProducer producer = session.createProducer(ADDRESS);

         // printBuffer("body to be sent : " , body);

         ClientMessage message = null;

         HornetQBuffer body = null;

         for (int i = 0; i < 100; i++)
         {
            HornetQBuffer bodyLocal = ChannelBuffers.buffer(DataConstants.SIZE_INT * numberOfBytes);

            for (int j = 1; j <= numberOfBytes; j++)
            {
               bodyLocal.writeInt(j);
            }

            if (i == 0)
            {
               body = bodyLocal;
            }

            message = session.createClientMessage(true);
            message.setBody(bodyLocal);

            producer.send(message);
         }

         ClientMessage clientFile = createLargeClientMessage(session, numberOfBytesBigMessage);

         producer.send(clientFile);

         session.close();

         if (realFiles)
         {
            server.stop();

            server = createServer(true, config, PAGE_SIZE, PAGE_MAX, new HashMap<String, AddressSettings>());
            server.start();

            sf = createInVMFactory();
         }

         session = sf.createSession(null, null, false, true, true, false, 0);

         ClientConsumer consumer = session.createConsumer(ADDRESS);

         session.start();

         for (int i = 0; i < 100; i++)
         {
            ClientMessage message2 = consumer.receive(RECEIVE_WAIT_TIME);

            log.debug("got message " + i);

            assertNotNull(message2);

            message2.acknowledge();

            assertNotNull(message2);

            try
            {
               assertEqualsByteArrays(body.writerIndex(), body.array(), message2.getBody().array());
            }
            catch (AssertionFailedError e)
            {
               log.info("Expected buffer:" + dumbBytesHex(body.array(), 40));
               log.info("Arriving buffer:" + dumbBytesHex(message2.getBody().array(), 40));
               throw e;
            }
         }

         consumer.close();

         session.close();

         session = sf.createSession(null, null, false, true, true, false, 0);

         readMessage(session, ADDRESS, numberOfBytesBigMessage);

         // printBuffer("message received : ", message2.getBody());

         session.close();
      }
      finally
      {
         try
         {
            server.stop();
         }
         catch (Throwable ignored)
         {
         }
      }

   }

   // Private -------------------------------------------------------

   private void assertGlobalSize(HornetQServer server) throws InterruptedException
   {
      // addGlobalSize on LargeMessage is only done after the delivery, and the addSize could be asynchronous
      long timeout = System.currentTimeMillis() + 5000;
      while (timeout > System.currentTimeMillis() && server.getPostOffice().getPagingManager().getTotalMemory() != 0)
      {
         Thread.sleep(100);
      }

      assertEquals(0l, server.getPostOffice().getPagingManager().getTotalMemory());
   }

   // Inner classes -------------------------------------------------

}