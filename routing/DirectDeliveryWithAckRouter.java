/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

//import core.Settings;

//import core.Connection;
//import util.Tuple;
//import java.util.List;
//import core.Message;


import java.util.ArrayList;
import java.util.List;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import routing.util.RoutingInfo;
import util.Tuple;

import core.Connection;
import core.DTNHost;
import core.SimClock;
import core.Message;
import core.Settings;




/**
 * Router that will deliver messages only to the final recipient.
 */
public class DirectDeliveryWithAckRouter extends ActiveRouter {

	/** IDs of the messages that are known to have reached the final dst */
	private Set<String> ackedMessageIds;
	
	/** Map of which messages have been sent to which hosts from this host */
	private Map<DTNHost, Set<String>> sentMessages;

	public DirectDeliveryWithAckRouter(Settings s) {
		super(s);
	}
	
	protected DirectDeliveryWithAckRouter(DirectDeliveryWithAckRouter r) {
		super(r);
		this.ackedMessageIds = new HashSet<String>();
		this.sentMessages = new HashMap<DTNHost, Set<String>>();
	}

	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) { // new connection
			//this.costsForMessages = null; // invalidate old cost estimates
			
			if (con.isInitiator(getHost())) {
				/* initiator performs all the actions on behalf of the
				 * other node too (so that the meeting probs are updated
				 * for both before exchanging them) */
				DTNHost otherHost = con.getOtherNode(getHost());
				MessageRouter mRouter = otherHost.getRouter();

				assert mRouter instanceof DirectDeliveryWithAckRouter : "DirectDeliveryWithAckRouter only works "+ 
				" with other routers of same type";
				DirectDeliveryWithAckRouter otherRouter = (DirectDeliveryWithAckRouter)mRouter;
				
				/* exchange ACKed message data */
				this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
				otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
				deleteAckedMessages();
				otherRouter.deleteAckedMessages();				
			}
		}
	}



	/**
	 * Exchanges deliverable (to final recipient) messages between this host
	 * and all hosts this host is currently connected to. First all messages
	 * from this host are checked and then all other hosts are asked for
	 * messages to this host. If a transfer is started, the search ends.
	 * @return A connection that started a transfer or null if no transfer
	 * was started
	 */
	@Override
	protected Connection exchangeDeliverableMessages() {
		List<Connection> connections = getConnections();

		if (connections.size() == 0) {
			return null;
		}
		
		@SuppressWarnings(value = "unchecked")
		Tuple<Message, Connection> t =
			tryMessagesForConnected(sortByQueueMode(getMessagesForConnected()));

		if (t != null) {
			System.out.println(getHost()+" -> t: "+t.getValue());
			return t.getValue(); // started transfer
		}
		
		// didn't start transfer to any node -> ask messages from connected
		for (Connection con : connections) {
			System.out.println(getHost()+" -> OtherNode "+con.getOtherNode(getHost()));
			if (con.getOtherNode(getHost()).requestDeliverableMessages(con)) {
				return con;
			}
		}
		
		return null;
	}

	
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	
	/**
	 * Deletes the messages from the message buffer that are known to be ACKed
	 */
	@Override
	protected void deleteAckedMessages() {
		for (String id : this.ackedMessageIds) {
			if (this.hasMessage(id) && !isSending(id)) {
				this.deleteMessage(id, false);
			}
		}
	}
	
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		//this.costsForMessages = null; // new message -> invalidate costs
		Message m = super.messageTransferred(id, from);
		/* was this node the final recipient of the message? */
		if (isDeliveredMessage(m)) {
			this.ackedMessageIds.add(id);
		}
		return m;
	}

	/**
	 * Method is called just before a transfer is finalized 
	 * at {@link ActiveRouter#update()}. MaxProp (it was copied from) makes book keeping of the
	 * delivered messages so their IDs are stored.
	 * @param con The connection whose transfer was finalized
	 */
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		String id = m.getId();
		DTNHost recipient = con.getOtherNode(getHost());
		Set<String> sentMsgIds = this.sentMessages.get(recipient);
		
		/* was the message delivered to the final recipient? */
		if (m.getTo() == recipient) { 
			this.ackedMessageIds.add(m.getId()); // yes, add to ACKed messages
			this.deleteMessage(m.getId(), false); // delete from buffer
		}
		
		/* update the map of where each message is already sent */
		if (sentMsgIds == null) {
			sentMsgIds = new HashSet<String>();
			this.sentMessages.put(recipient, sentMsgIds);
		}		
		sentMsgIds.add(id);
	}
		
	
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // can't start a new transfer
		}
		
		// Try only the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer
		}
	}
	
	@Override
	public DirectDeliveryWithAckRouter replicate() {
		return new DirectDeliveryWithAckRouter(this);
	}
}
