/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.test.messaging.jms.server.connectionmanager;

import java.util.Iterator;
import java.util.Map;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;

import org.jboss.jms.client.JBossConnection;
import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.client.api.ClientSession;
import org.jboss.jms.delegate.ConnectionEndpoint;
import org.jboss.jms.delegate.IDBlock;
import org.jboss.jms.delegate.SessionDelegate;
import org.jboss.jms.server.ConnectionManager;
import org.jboss.jms.server.connectionmanager.SimpleConnectionManager;
import org.jboss.jms.tx.MessagingXid;
import org.jboss.jms.tx.TransactionRequest;
import org.jboss.messaging.core.impl.server.MessagingServerImpl;
import org.jboss.test.messaging.jms.JMSTestCase;

/**
 * 
 * A SimpleConnectionManagerTest.
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class SimpleConnectionManagerTest extends JMSTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   // Constructors --------------------------------------------------

   public SimpleConnectionManagerTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------
     
   public void testWithRealServer() throws Exception
   {
      ConnectionFactory cf = (JBossConnectionFactory)ic.lookup("/ConnectionFactory");
      
      JBossConnection conn1 = null;
      
      JBossConnection conn2 = null;
      
      
      try
      {
      
	      conn1 = (JBossConnection)cf.createConnection();
	      Session sess1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      
	      conn2 = (JBossConnection)cf.createConnection();
	      Session sess2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
	      
	      MessagingServerImpl peer = (MessagingServerImpl) getJmsServer();
	      
	      SimpleConnectionManager cm = (SimpleConnectionManager)peer.getConnectionManager();
	      
	      //Simulate failure on connection
	      
	      Map jmsClients = cm.getClients();
	      assertEquals(1, jmsClients.size());
	      
	      Map endpoints = (Map)jmsClients.values().iterator().next();
	      
	      assertEquals(2, endpoints.size());
	      
	      Iterator iter = endpoints.entrySet().iterator();
	            
	      Map.Entry entry = (Map.Entry)iter.next();
	      
	      String sessId1 = (String)entry.getKey();
	       
	      entry = (Map.Entry)iter.next();
	      
	      //Simulate failure of connection
	      
	      cm.handleClientFailure(sessId1, true);
	      
	      //both connections should be shut
	      
	      jmsClients = cm.getClients();
	      assertEquals(0, jmsClients.size());        
      }
      finally
      {
      	if (conn1 != null)
      	{
      		conn1.close();
      	}
      	if (conn2 != null)
      	{
      		conn2.close();
      	}
      }
   }
   

   public void testWithMock() throws Exception
   {
      SimpleConnectionManager cm = new SimpleConnectionManager();

      SimpleConnectionEndpoint e1 = new SimpleConnectionEndpoint(cm, "jvm1", "sessionid1");
      SimpleConnectionEndpoint e2 = new SimpleConnectionEndpoint(cm, "jvm1", "sessionid2");
      SimpleConnectionEndpoint e3 = new SimpleConnectionEndpoint(cm, "jvm2", "sessionid3");
      SimpleConnectionEndpoint e4 = new SimpleConnectionEndpoint(cm, "jvm2", "sessionid4");
      SimpleConnectionEndpoint e5 = new SimpleConnectionEndpoint(cm, "jvm3", "sessionid5");
      SimpleConnectionEndpoint e6 = new SimpleConnectionEndpoint(cm, "jvm3", "sessionid6");

      assertFalse(e1.isClosed());
      assertFalse(e2.isClosed());
      assertFalse(e3.isClosed());
      assertFalse(e4.isClosed());
      assertFalse(e5.isClosed());
      assertFalse(e6.isClosed());

      cm.registerConnection("jvm1", "sessionid1", e1);
      cm.registerConnection("jvm1", "sessionid2", e2);
      cm.registerConnection("jvm2", "sessionid3", e3);
      cm.registerConnection("jvm2", "sessionid4", e4);
      cm.registerConnection("jvm3", "sessionid5", e5);
      cm.registerConnection("jvm3", "sessionid6", e6);

      assertTrue(cm.containsRemotingSession("sessionid1"));
      assertTrue(cm.containsRemotingSession("sessionid2"));
      assertTrue(cm.containsRemotingSession("sessionid3"));
      assertTrue(cm.containsRemotingSession("sessionid4"));
      assertTrue(cm.containsRemotingSession("sessionid5"));
      assertTrue(cm.containsRemotingSession("sessionid6"));

      ConnectionEndpoint r1 = cm.unregisterConnection("jvm3", "sessionid6");
      assertEquals(e6, r1);
      assertFalse(e6.isClosed());

      assertNull(cm.unregisterConnection("blah", "blah"));

      assertFalse(cm.containsRemotingSession("sessionid6"));

      ConnectionEndpoint r2 = cm.unregisterConnection("jvm3", "sessionid5");
      assertEquals(e5, r2);
      assertFalse(e5.isClosed());

      assertFalse(cm.containsRemotingSession("sessionid5"));

      cm.handleClientFailure("sessionid4", true);

      assertNull(cm.unregisterConnection("jvm2", "sessionid4"));
      assertNull(cm.unregisterConnection("jvm2", "sessionid3"));

      assertFalse(cm.containsRemotingSession("sessionid4"));
      assertFalse(cm.containsRemotingSession("sessionid3"));

      assertTrue(e3.isClosed());
      assertTrue(e4.isClosed());

      ConnectionEndpoint r3 = cm.unregisterConnection("jvm1", "sessionid1");
      assertEquals(e1, r3);
      assertFalse(e1.isClosed());

      ConnectionEndpoint r4 = cm.unregisterConnection("jvm1", "sessionid2");
      assertEquals(e2, r4);
      assertFalse(e2.isClosed());

      assertFalse(cm.containsRemotingSession("sessionid2"));
      assertFalse(cm.containsRemotingSession("sessionid1"));

   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   class SimpleConnectionEndpoint implements ConnectionEndpoint
   {
      public boolean closed;
      
      private ConnectionManager cm;
      
      private String jvmId;
      
      private String sessionID;

      SimpleConnectionEndpoint(ConnectionManager cm, String jvmId, String sessionID)
      {
         closed = false;
         
         this.cm = cm;
         
         this.jvmId = jvmId;
         
         this.sessionID = sessionID;
      }

      public boolean isClosed()
      {
         return closed;
      }

      public ClientSession createSessionDelegate(boolean transacted, int acknowledgmentMode, boolean isXA) throws JMSException
      {
         return null;
      }

      public String getClientID() throws JMSException
      {
         return null;
      }

      public MessagingXid[] getPreparedTransactions()
      {
         return null;
      }

      public void sendTransaction(TransactionRequest request) throws JMSException
      {
      }

      public void setClientID(String id) throws JMSException
      {
      }

      public void start() throws JMSException
      {
      }

      public void stop() throws JMSException
      {
      }

      public void close() throws JMSException
      {         
         cm.unregisterConnection(jvmId, sessionID);
         
         closed = true;
      }

      public long closing(long seq) throws JMSException
      {
         return -1;
      }

      public IDBlock getIdBlock(int size) throws JMSException
      {
         return null;
      }
   }
}

