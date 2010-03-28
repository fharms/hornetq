/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.core.persistence.config;

import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.journal.EncodingSupport;

/**
 * A ConfiguredRoles
 *
 * @author <mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
public class PersistedRoles implements EncodingSupport
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private long storeId;

   private SimpleString addressMatch;

   private SimpleString sendRoles;

   private SimpleString consumeRoles;

   private SimpleString createDurableQueueRoles;

   private SimpleString deleteDurableQueueRoles;

   private SimpleString createTempQueueRoles;

   private SimpleString deleteTempQueueRoles;

   private SimpleString manageRoles;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public PersistedRoles()
   {
   }

   /**
    * @param address
    * @param addressMatch
    * @param sendRoles
    * @param consumeRoles
    * @param createDurableQueueRoles
    * @param deleteDurableQueueRoles
    * @param createTempQueueRoles
    * @param deleteTempQueueRoles
    * @param manageRoles
    */
   public PersistedRoles(final String addressMatch,
                         final String sendRoles,
                         final String consumeRoles,
                         final String createDurableQueueRoles,
                         final String deleteDurableQueueRoles,
                         final String createTempQueueRoles,
                         final String deleteTempQueueRoles,
                         final String manageRoles)
   {
      super();
      this.addressMatch = SimpleString.toSimpleString(addressMatch);
      this.sendRoles = SimpleString.toSimpleString(sendRoles);
      this.consumeRoles = SimpleString.toSimpleString(consumeRoles);
      this.createDurableQueueRoles = SimpleString.toSimpleString(createDurableQueueRoles);
      this.deleteDurableQueueRoles = SimpleString.toSimpleString(deleteDurableQueueRoles);
      this.createTempQueueRoles = SimpleString.toSimpleString(createTempQueueRoles);
      this.deleteTempQueueRoles = SimpleString.toSimpleString(deleteTempQueueRoles);
      this.manageRoles = SimpleString.toSimpleString(manageRoles);
   }

   // Public --------------------------------------------------------

   public long getStoreId()
   {
      return storeId;
   }

   public void setStoreId(final long id)
   {
      storeId = id;
   }

   /**
    * @return the addressMatch
    */
   public SimpleString getAddressMatch()
   {
      return addressMatch;
   }

   /**
    * @return the sendRoles
    */
   public String getSendRoles()
   {
      return sendRoles.toString();
   }

   /**
    * @return the consumeRoles
    */
   public String getConsumeRoles()
   {
      return consumeRoles.toString();
   }

   /**
    * @return the createDurableQueueRoles
    */
   public String getCreateDurableQueueRoles()
   {
      return createDurableQueueRoles.toString();
   }

   /**
    * @return the deleteDurableQueueRoles
    */
   public String getDeleteDurableQueueRoles()
   {
      return deleteDurableQueueRoles.toString();
   }

   /**
    * @return the createTempQueueRoles
    */
   public String getCreateTempQueueRoles()
   {
      return createTempQueueRoles.toString();
   }

   /**
    * @return the deleteTempQueueRoles
    */
   public String getDeleteTempQueueRoles()
   {
      return deleteTempQueueRoles.toString();
   }

   /**
    * @return the manageRoles
    */
   public String getManageRoles()
   {
      return manageRoles.toString();
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.EncodingSupport#encode(org.hornetq.api.core.HornetQBuffer)
    */
   public void encode(final HornetQBuffer buffer)
   {
      buffer.writeSimpleString(addressMatch);
      buffer.writeNullableSimpleString(sendRoles);
      buffer.writeNullableSimpleString(consumeRoles);
      buffer.writeNullableSimpleString(createDurableQueueRoles);
      buffer.writeNullableSimpleString(deleteDurableQueueRoles);
      buffer.writeNullableSimpleString(createTempQueueRoles);
      buffer.writeNullableSimpleString(deleteTempQueueRoles);
      buffer.writeNullableSimpleString(manageRoles);
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.EncodingSupport#getEncodeSize()
    */
   public int getEncodeSize()
   {
      return addressMatch.sizeof() + SimpleString.sizeofNullableString(sendRoles) +
             SimpleString.sizeofNullableString(consumeRoles) +
             SimpleString.sizeofNullableString(createDurableQueueRoles) +
             SimpleString.sizeofNullableString(deleteDurableQueueRoles) +
             SimpleString.sizeofNullableString(createTempQueueRoles) +
             SimpleString.sizeofNullableString(deleteTempQueueRoles) +
             SimpleString.sizeofNullableString(manageRoles);

   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.EncodingSupport#decode(org.hornetq.api.core.HornetQBuffer)
    */
   public void decode(final HornetQBuffer buffer)
   {
      addressMatch = buffer.readSimpleString();
      sendRoles = buffer.readNullableSimpleString();
      consumeRoles = buffer.readNullableSimpleString();
      createDurableQueueRoles = buffer.readNullableSimpleString();
      deleteDurableQueueRoles = buffer.readNullableSimpleString();
      createTempQueueRoles = buffer.readNullableSimpleString();
      deleteTempQueueRoles = buffer.readNullableSimpleString();
      manageRoles = buffer.readNullableSimpleString();
   }

   /* (non-Javadoc)
    * @see java.lang.Object#hashCode()
    */
   @Override
   public int hashCode()
   {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((addressMatch == null) ? 0 : addressMatch.hashCode());
      result = prime * result + ((consumeRoles == null) ? 0 : consumeRoles.hashCode());
      result = prime * result + ((createDurableQueueRoles == null) ? 0 : createDurableQueueRoles.hashCode());
      result = prime * result + ((createTempQueueRoles == null) ? 0 : createTempQueueRoles.hashCode());
      result = prime * result + ((deleteDurableQueueRoles == null) ? 0 : deleteDurableQueueRoles.hashCode());
      result = prime * result + ((deleteTempQueueRoles == null) ? 0 : deleteTempQueueRoles.hashCode());
      result = prime * result + ((manageRoles == null) ? 0 : manageRoles.hashCode());
      result = prime * result + ((sendRoles == null) ? 0 : sendRoles.hashCode());
      result = prime * result + (int)(storeId ^ (storeId >>> 32));
      return result;
   }

   /* (non-Javadoc)
    * @see java.lang.Object#equals(java.lang.Object)
    */
   @Override
   public boolean equals(Object obj)
   {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      PersistedRoles other = (PersistedRoles)obj;
      if (addressMatch == null)
      {
         if (other.addressMatch != null)
            return false;
      }
      else if (!addressMatch.equals(other.addressMatch))
         return false;
      if (consumeRoles == null)
      {
         if (other.consumeRoles != null)
            return false;
      }
      else if (!consumeRoles.equals(other.consumeRoles))
         return false;
      if (createDurableQueueRoles == null)
      {
         if (other.createDurableQueueRoles != null)
            return false;
      }
      else if (!createDurableQueueRoles.equals(other.createDurableQueueRoles))
         return false;
      if (createTempQueueRoles == null)
      {
         if (other.createTempQueueRoles != null)
            return false;
      }
      else if (!createTempQueueRoles.equals(other.createTempQueueRoles))
         return false;
      if (deleteDurableQueueRoles == null)
      {
         if (other.deleteDurableQueueRoles != null)
            return false;
      }
      else if (!deleteDurableQueueRoles.equals(other.deleteDurableQueueRoles))
         return false;
      if (deleteTempQueueRoles == null)
      {
         if (other.deleteTempQueueRoles != null)
            return false;
      }
      else if (!deleteTempQueueRoles.equals(other.deleteTempQueueRoles))
         return false;
      if (manageRoles == null)
      {
         if (other.manageRoles != null)
            return false;
      }
      else if (!manageRoles.equals(other.manageRoles))
         return false;
      if (sendRoles == null)
      {
         if (other.sendRoles != null)
            return false;
      }
      else if (!sendRoles.equals(other.sendRoles))
         return false;
      if (storeId != other.storeId)
         return false;
      return true;
   }
   
   
   
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}