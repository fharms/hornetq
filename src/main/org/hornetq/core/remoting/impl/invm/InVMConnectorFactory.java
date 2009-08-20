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
package org.hornetq.core.remoting.impl.invm;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import org.hornetq.core.remoting.spi.BufferHandler;
import org.hornetq.core.remoting.spi.ConnectionLifeCycleListener;
import org.hornetq.core.remoting.spi.Connector;
import org.hornetq.core.remoting.spi.ConnectorFactory;

/**
 * A InVMConnectorFactory
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class InVMConnectorFactory implements ConnectorFactory
{
   public Connector createConnector(final Map<String, Object> configuration,
                                    final BufferHandler handler,
                                    final ConnectionLifeCycleListener listener,
                                    final Executor threadPool, 
                                    final ScheduledExecutorService scheduledThreadPool)
   {      
      InVMConnector connector = new InVMConnector(configuration, handler, listener, threadPool);
      
      if (connector.getAcceptor() == null)
      {
         return null;
      }
      else
      {         
         return connector;
      }
   }

}