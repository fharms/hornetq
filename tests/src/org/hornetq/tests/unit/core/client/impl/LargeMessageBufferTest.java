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

package org.hornetq.tests.unit.core.client.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.core.buffers.ChannelBuffer;
import org.hornetq.core.buffers.ChannelBuffers;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.MessageHandler;
import org.hornetq.core.client.impl.ClientConsumerInternal;
import org.hornetq.core.client.impl.ClientMessageInternal;
import org.hornetq.core.client.impl.LargeMessageBufferImpl;
import org.hornetq.core.exception.HornetQException;
import org.hornetq.core.remoting.impl.wireformat.SessionReceiveContinuationMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionReceiveMessage;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.UnitTestCase;

/**
 * A LargeMessageBufferUnitTest
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
public class LargeMessageBufferTest extends UnitTestCase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Test Simple getBytes
   public void testGetBytes() throws Exception
   {
      LargeMessageBufferImpl buffer = create15BytesSample();

      for (int i = 1; i <= 15; i++)
      {
         try
         {
            assertEquals(i, buffer.readByte());
         }
         catch (Exception e)
         {
            throw new Exception("Exception at position " + i, e);
         }
      }

      try
      {
         buffer.readByte();
         fail("supposed to throw an exception");
      }
      catch (IndexOutOfBoundsException e)
      {
      }
   }

   // Test for void getBytes(final int index, final byte[] dst)
   public void testGetBytesIByteArray() throws Exception
   {
      LargeMessageBufferImpl buffer = create15BytesSample();

      byte[] bytes = new byte[15];
      buffer.getBytes(0, bytes);

      validateAgainstSample(bytes);

      try
      {
         buffer = create15BytesSample();

         bytes = new byte[16];
         buffer.getBytes(0, bytes);
         fail("supposed to throw an exception");
      }
      catch (java.lang.IndexOutOfBoundsException e)
      {
      }
   }

   // testing void getBytes(int index, ChannelBuffer dst, int dstIndex, int length)
   public void testGetBytesILChannelBufferII() throws Exception
   {
      LargeMessageBufferImpl buffer = create15BytesSample();

      ChannelBuffer dstBuffer = ChannelBuffers.buffer(20);

      dstBuffer.setIndex(0, 5);

      buffer.getBytes(0, dstBuffer);

      byte[] compareBytes = new byte[15];
      dstBuffer.getBytes(5, compareBytes);

      validateAgainstSample(compareBytes);
   }

   // testing void getBytes(int index, ChannelBuffer dst, int dstIndex, int length)
   public void testReadIntegers() throws Exception
   {
      LargeMessageBufferImpl buffer = createBufferWithIntegers(3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

      for (int i = 1; i <= 15; i++)
      {
         assertEquals(i, buffer.readInt());
      }

      try
      {
         buffer.readByte();
         fail("supposed to throw an exception");
      }
      catch (IndexOutOfBoundsException e)
      {
      }
   }

   // testing void getBytes(int index, ChannelBuffer dst, int dstIndex, int length)
   public void testReadLongs() throws Exception
   {
      LargeMessageBufferImpl buffer = createBufferWithLongs(3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

      for (int i = 1; i <= 15; i++)
      {
         assertEquals(i, buffer.readLong());
      }

      try
      {
         buffer.readByte();
         fail("supposed to throw an exception");
      }
      catch (IndexOutOfBoundsException e)
      {
      }
   }

   public void testReadData() throws Exception
   {
      ChannelBuffer dynamic = ChannelBuffers.dynamicBuffer(1);

      String str1 = RandomUtil.randomString();
      String str2 = RandomUtil.randomString();
      Double d1 = RandomUtil.randomDouble();
      float f1 = RandomUtil.randomFloat();

      dynamic.writeUTF(str1);
      dynamic.writeString(str2);
      dynamic.writeDouble(d1);
      dynamic.writeFloat(f1);

      LargeMessageBufferImpl readBuffer = splitBuffer(3, dynamic.array());

      assertEquals(str1, readBuffer.readUTF());
      assertEquals(str2, readBuffer.readString());
      assertEquals(d1, readBuffer.readDouble());
      assertEquals(f1, readBuffer.readFloat());
   }

   private File getTestFile()
   {
      return new File(getTestDir(), "temp.file");
   }
   
   public void testReadDataOverCached() throws Exception
   {
      clearData();
      
      ChannelBuffer dynamic = ChannelBuffers.dynamicBuffer(1);

      String str1 = RandomUtil.randomString();
      String str2 = RandomUtil.randomString();
      Double d1 = RandomUtil.randomDouble();
      float f1 = RandomUtil.randomFloat();

      dynamic.writeUTF(str1);
      dynamic.writeString(str2);
      dynamic.writeDouble(d1);
      dynamic.writeFloat(f1);

      LargeMessageBufferImpl readBuffer = splitBuffer(3, dynamic.array(), getTestFile());

      assertEquals(str1, readBuffer.readUTF());
      assertEquals(str2, readBuffer.readString());
      assertEquals(d1, readBuffer.readDouble());
      assertEquals(f1, readBuffer.readFloat());
      
      readBuffer.readerIndex(0);
      
      assertEquals(str1, readBuffer.readUTF());
      assertEquals(str2, readBuffer.readString());
      assertEquals(d1, readBuffer.readDouble());
      assertEquals(f1, readBuffer.readFloat());
      
      readBuffer.close();
   }

   public void testReadPartialData() throws Exception
   {

      final LargeMessageBufferImpl buffer = new LargeMessageBufferImpl(new FakeConsumerInternal(), 10, 10);

      buffer.addPacket(new SessionReceiveContinuationMessage(-1, new byte[] { 0, 1, 2, 3, 4 }, true, true));

      byte bytes[] = new byte[30];
      buffer.readBytes(bytes, 0, 5);

      for (byte i = 0; i < 5; i++)
      {
         assertEquals(i, bytes[i]);
      }

      final CountDownLatch latchGo = new CountDownLatch(1);

      final AtomicInteger errorCount = new AtomicInteger(0);

      Thread t = new Thread()
      {
         public void run()
         {

            try
            {
               latchGo.countDown();
               buffer.readBytes(new byte[5]);
            }
            catch (IndexOutOfBoundsException ignored)
            {
            }
            catch (Throwable e)
            {
               e.printStackTrace();
               errorCount.incrementAndGet();
            }
         }
      };

      t.start();

      latchGo.await();

      buffer.cancel();

      t.join();

      assertEquals(0, errorCount.get());

   }

   public void testInterruptData() throws Exception
   {
      LargeMessageBufferImpl readBuffer = splitBuffer(3, new byte[] { 0, 1, 2, 3, 4 });

      byte bytes[] = new byte[30];
      readBuffer.readBytes(bytes, 0, 5);

      for (byte i = 0; i < 5; i++)
      {
         assertEquals(i, bytes[i]);
      }
   }

   public void testStreamData() throws Exception
   {
      final LargeMessageBufferImpl outBuffer = new LargeMessageBufferImpl(new FakeConsumerInternal(),
                                                                          1024 * 11 + 123,
                                                                          1);

      final PipedOutputStream output = new PipedOutputStream();
      final PipedInputStream input = new PipedInputStream(output);

      final AtomicInteger errors = new AtomicInteger(0);

      // Done reading 3 elements
      final CountDownLatch done1 = new CountDownLatch(1);
      // Done with the thread
      final CountDownLatch done2 = new CountDownLatch(1);

      final AtomicInteger count = new AtomicInteger(0);
      final AtomicInteger totalBytes = new AtomicInteger(0);

      Thread treader = new Thread("treader")
      {
         public void run()
         {
            try
            {

               byte line[] = new byte[1024];
               int dataRead = 0;
               while (dataRead >= 0)
               {
                  dataRead = input.read(line);
                  if (dataRead > 0)
                  {
                     System.out.println("Read one line with " + dataRead + " bytes");
                     totalBytes.addAndGet(dataRead);
                     if (count.incrementAndGet() == 3)
                     {
                        done1.countDown();
                     }
                  }
               }
            }
            catch (Exception e)
            {
               e.printStackTrace();
               errors.incrementAndGet();
            }
            finally
            {
               done1.countDown();
               done2.countDown();
            }
         }
      };

      treader.setDaemon(true);
      treader.start();

      for (int i = 0; i < 3; i++)
      {
         outBuffer.addPacket(new SessionReceiveContinuationMessage(-1, new byte[1024], true, false));
      }

      outBuffer.setOutputStream(output);

      final CountDownLatch waiting = new CountDownLatch(1);

      Thread twaiter = new Thread("twaiter")
      {
         public void run()
         {
            try
            {
               outBuffer.waitCompletion(0);
               waiting.countDown();
            }
            catch (Exception e)
            {
               e.printStackTrace();
               errors.incrementAndGet();
            }
         }
      };

      twaiter.setDaemon(true);
      twaiter.start();

      assertTrue(done1.await(10, TimeUnit.SECONDS));

      assertEquals(3, count.get());
      assertEquals(1024 * 3, totalBytes.get());

      for (int i = 0; i < 8; i++)
      {
         outBuffer.addPacket(new SessionReceiveContinuationMessage(-1, new byte[1024], true, false));
      }

      assertEquals(1, waiting.getCount());

      outBuffer.addPacket(new SessionReceiveContinuationMessage(-1, new byte[123], false, false));

      assertTrue(done2.await(10, TimeUnit.SECONDS));

      assertTrue(waiting.await(10, TimeUnit.SECONDS));

      assertEquals(12, count.get());
      assertEquals(1024 * 11 + 123, totalBytes.get());

      treader.join();

      twaiter.join();

      assertEquals(0, errors.get());

   }

   public void testStreamDataWaitCompletionOnCompleteBuffer() throws Exception
   {
      final LargeMessageBufferImpl outBuffer = create15BytesSample();

      outBuffer.saveBuffer(new OutputStream()
      {
         @Override
         public void write(int b) throws IOException
         {
            // nohting to be done
         }
      });
   }

   public void testErrorOnSetStreaming() throws Exception
   {
      long start = System.currentTimeMillis();
      final LargeMessageBufferImpl outBuffer = new LargeMessageBufferImpl(new FakeConsumerInternal(), 5, 30);

      outBuffer.addPacket(new SessionReceiveContinuationMessage(-1, new byte[] { 0, 1, 2, 3, 4 }, true, false));

      final CountDownLatch latchBytesWritten1 = new CountDownLatch(5);
      final CountDownLatch latchBytesWritten2 = new CountDownLatch(10);

      outBuffer.setOutputStream(new OutputStream()
      {
         @Override
         public void write(int b) throws IOException
         {
            latchBytesWritten1.countDown();
            latchBytesWritten2.countDown();
         }
      });

      latchBytesWritten1.await();

      try
      {
         outBuffer.readByte();
         fail("supposed to throw an exception");
      }
      catch (IllegalAccessError ignored)
      {
      }

      assertTrue("It waited too much", System.currentTimeMillis() - start < 30000);

   }

   /**
    * @return
    */
   private LargeMessageBufferImpl create15BytesSample() throws Exception
   {
      return splitBuffer(5, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 });
   }

   private LargeMessageBufferImpl createBufferWithIntegers(int splitFactor, int... values) throws Exception
   {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream(values.length * 4);
      DataOutputStream dataOut = new DataOutputStream(byteOut);

      for (int value : values)
      {
         dataOut.writeInt(value);
      }

      return splitBuffer(splitFactor, byteOut.toByteArray());
   }

   private LargeMessageBufferImpl createBufferWithLongs(int splitFactor, long... values) throws Exception
   {
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream(values.length * 8);
      DataOutputStream dataOut = new DataOutputStream(byteOut);

      for (long value : values)
      {
         dataOut.writeLong(value);
      }

      return splitBuffer(splitFactor, byteOut.toByteArray());
   }

   private LargeMessageBufferImpl splitBuffer(int splitFactor, byte[] bytes) throws Exception
   {
      return splitBuffer(splitFactor, bytes, null);
   }

   private LargeMessageBufferImpl splitBuffer(int splitFactor, byte[] bytes, File file) throws Exception
   {
      LargeMessageBufferImpl outBuffer = new LargeMessageBufferImpl(new FakeConsumerInternal(), bytes.length, 5, file);

      ByteArrayInputStream input = new ByteArrayInputStream(bytes);

      while (true)
      {
         byte[] splitElement = new byte[splitFactor];
         int size = input.read(splitElement);
         if (size <= 0)
         {
            break;
         }

         if (size < splitFactor)
         {
            byte[] newSplit = new byte[size];
            System.arraycopy(splitElement, 0, newSplit, 0, size);

            outBuffer.addPacket(new SessionReceiveContinuationMessage(1, newSplit, input.available() > 0, false));
         }
         else
         {
            outBuffer.addPacket(new SessionReceiveContinuationMessage(1, splitElement, input.available() > 0, false));
         }
      }

      return outBuffer;

   }

   /**
       * @param bytes
       */
   private void validateAgainstSample(byte[] bytes)
   {
      for (int i = 1; i <= 15; i++)
      {
         assertEquals(i, bytes[i - 1]);
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   static class FakeConsumerInternal implements ClientConsumerInternal
   {

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#acknowledge(org.hornetq.core.client.ClientMessage)
       */
      public void acknowledge(ClientMessage message) throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#cleanUp()
       */
      public void cleanUp() throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#clear()
       */
      public void clear()
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#flowControl(int, boolean)
       */
      public void flowControl(int messageBytes, boolean isLargeMessage) throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#flushAcks()
       */
      public void flushAcks() throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#getBufferSize()
       */
      public int getBufferSize()
      {

         return 0;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#getClientWindowSize()
       */
      public int getClientWindowSize()
      {

         return 0;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#getID()
       */
      public long getID()
      {

         return 0;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#handleLargeMessage(org.hornetq.core.remoting.impl.wireformat.SessionReceiveMessage)
       */
      public void handleLargeMessage(SessionReceiveMessage largeMessageHeader) throws Exception
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#handleLargeMessageContinuation(org.hornetq.core.remoting.impl.wireformat.SessionReceiveContinuationMessage)
       */
      public void handleLargeMessageContinuation(SessionReceiveContinuationMessage continuation) throws Exception
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#handleMessage(org.hornetq.core.client.impl.ClientMessageInternal)
       */
      public void handleMessage(ClientMessageInternal message) throws Exception
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#isFileConsumer()
       */
      public boolean isFileConsumer()
      {

         return false;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#start()
       */
      public void start()
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.impl.ClientConsumerInternal#stop()
       */
      public void stop() throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#close()
       */
      public void close() throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#getLastException()
       */
      public Exception getLastException()
      {

         return null;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#getMessageHandler()
       */
      public MessageHandler getMessageHandler() throws HornetQException
      {

         return null;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#isClosed()
       */
      public boolean isClosed()
      {

         return false;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#receive()
       */
      public ClientMessage receive() throws HornetQException
      {

         return null;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#receive(long)
       */
      public ClientMessage receive(long timeout) throws HornetQException
      {

         return null;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#receiveImmediate()
       */
      public ClientMessage receiveImmediate() throws HornetQException
      {

         return null;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#setMessageHandler(org.hornetq.core.client.MessageHandler)
       */
      public void setMessageHandler(MessageHandler handler) throws HornetQException
      {

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#getLargeMessageCacheDir()
       */
      public File getLargeMessageCacheDir()
      {
          return null;
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.client.ClientConsumer#setLargeMessageCacheDir(java.io.File)
       */
      public void setLargeMessageCacheDir(File largeMessageCacheDir)
      {
      }

   }

}