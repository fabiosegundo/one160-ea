/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

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
 * Implementation of Density Aware based on Spray and wait router as depicted in 
 * <I>Density Aware DTN</I> by Fabio Segundo et al.
 *
 */
public class DensityAwareRouter extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String DENSITYAWARE_NS = "DensityAwareRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = DENSITYAWARE_NS + "." +
		"copies";

	/** The density limit parameter string*/
	public static final String DENSITYLIMIT_S = "densityLimit";
	/** The densitylimit variable, default = 3 contacts;*/
	private double densityLimit;
	/** The default value for densitylimit */
	public static final double DEFAULT_DENSITYLIMIT = 3.0;

	/** The time limit parameter string*/
	public static final String TIMELIMIT_S = "timeLimit";
	/** The time limit variable, default = 3 seconds;*/
	private double timeLimit;
	/** The default value for time limit*/
	public static final double DEFAULT_TIMELIMIT = 60;


	
	protected int initialNrofCopies;
	protected boolean isBinary;


	/** IDs of the messages that are known to have reached the final dst */
	private Set<String> ackedMessageIds;
	
	/** Map of which messages have been sent to which hosts from this host */
	private Map<DTNHost, Set<String>> sentMessages;
		
	/** Over how many samples the "average number of bytes transferred per
	 * transfer opportunity" is taken */
	public static int BYTES_TRANSFERRED_AVG_SAMPLES = 10;
	private int[] avgSamples;
	private int nextSampleIndex = 0;
	/** current value for the "avg number of bytes transferred per transfer
	 * opportunity"  */
	private int avgTransferredBytes = 0;
	
	/** quatity of contacts into the defined densityTime  */
	private int nrContactsIntoTime = 0;
	
	/** time of the last contact */
	private double lastContactTime = 0;



	/**
	 * Constructor. Creates a new prototype router based on the settings in
	 * the given Settings object.
	 * @param settings The settings object
	 */
	public DensityAwareRouter(Settings s) {
		super(s);
		Settings dawSettings = new Settings(DENSITYAWARE_NS);
		
		initialNrofCopies = dawSettings.getInt(NROF_COPIES);
		isBinary = dawSettings.getBoolean(BINARY_MODE);
		if (dawSettings.contains(DENSITYLIMIT_S)) {
			densityLimit = dawSettings.getInt(DENSITYLIMIT_S);
		} else {
			densityLimit = DEFAULT_DENSITYLIMIT;
		}
		if (dawSettings.contains(TIMELIMIT_S)) {
			timeLimit = dawSettings.getInt(TIMELIMIT_S);
		} else {
			timeLimit = DEFAULT_TIMELIMIT;
		}
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected DensityAwareRouter(DensityAwareRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.ackedMessageIds = new HashSet<String>();
		this.avgSamples = new int[BYTES_TRANSFERRED_AVG_SAMPLES];
		this.sentMessages = new HashMap<DTNHost, Set<String>>();
	}
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) { // new connection
			//this.costsForMessages = null; // invalidate old cost estimates
			
			// Increment counter and determine density per place and time
			//evaluateDensity(getHost(),SimClock.getTime());

 			if (con.isInitiator(getHost())) {
				/* initiator performs all the actions on behalf of the
				 * other node too (so that the meeting probs are updated
				 * for both before exchanging them) */
				DTNHost otherHost = con.getOtherNode(getHost());
				MessageRouter mRouter = otherHost.getRouter();

				assert mRouter instanceof DensityAwareRouter : "DensityAwareRouter only works "+ 
				" with other routers of same type";
				DensityAwareRouter otherRouter = (DensityAwareRouter)mRouter;
				
				/* exchange ACKed message data */
				this.ackedMessageIds.addAll(otherRouter.ackedMessageIds);
				otherRouter.ackedMessageIds.addAll(this.ackedMessageIds);
				deleteAckedMessages();
				otherRouter.deleteAckedMessages();
				
				/* update density counter and four? last contact time */
				/* contador para cada interface? */
				if (SimClock.getTime()>lastContactTime+timeLimit) {
					lastContactTime = SimClock.getTime();
					nrContactsIntoTime = 1;
				} else {
				    nrContactsIntoTime++;
				}
				
				if (nrContactsIntoTime>densityLimit) {
					//desliga iface wifi e liga bluetooh
					//poweroff wifi interface;
					//diminui a frequencia de scan da bt;
				}
				
				
				/* update both meeting probabilities */
				//probs.updateMeetingProbFor(otherHost.getAddress());
				//otherRouter.probs.updateMeetingProbFor(getHost().getAddress());
				
				/* exchange the transitive probabilities */
				//this.updateTransitiveProbs(otherRouter.allProbs);
				//otherRouter.updateTransitiveProbs(this.allProbs);
				//this.allProbs.put(otherHost.getAddress(),
				//		otherRouter.probs.replicate());
				//otherRouter.allProbs.put(getHost().getAddress(),
				//		this.probs.replicate());
			}
			
		}
		else {
			/* connection went down, update transferred bytes average */
			updateTransferredBytesAvg(con.getTotalBytesTransferred());
		}
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		if (isBinary) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}
	
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
	
	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; // nothing to transfer or is currently transferring 
		}

		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
		
		//getHost().sleepInterface(getContacts());
		
	}
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return list;
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
	
	
	/**
	 * Updates the average estimate of the number of bytes transferred per
	 * transfer opportunity.
	 * @param newValue The new value to add to the estimate
	 */
	private void updateTransferredBytesAvg(int newValue) {
		int realCount = 0;
		int sum = 0;
		
		this.avgSamples[this.nextSampleIndex++] = newValue;
		if(this.nextSampleIndex >= BYTES_TRANSFERRED_AVG_SAMPLES) {
			this.nextSampleIndex = 0;
		}
		
		for (int i=0; i < BYTES_TRANSFERRED_AVG_SAMPLES; i++) {
			if (this.avgSamples[i] > 0) { // only values above zero count
				realCount++;
				sum += this.avgSamples[i];
			}
		}
		
		if (realCount > 0) {
			this.avgTransferredBytes = sum / realCount;
		}
		else { // no samples or all samples are zero
			this.avgTransferredBytes = 0;
		}
	}
	

	
	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) { 
			nrofCopies /= 2;
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}
	
	@Override
	public DensityAwareRouter replicate() {
		return new DensityAwareRouter(this);
	}
}
