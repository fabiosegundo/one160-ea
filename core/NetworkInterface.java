/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import interfaces.ConnectivityGrid;
import interfaces.ConnectivityOptimizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import util.ActivenessHandler;

import java.awt.Color;


/**
 * Network interface of a DTNHost. Takes care of connectivity among hosts.
 */
abstract public class NetworkInterface implements ModuleCommunicationListener {
	/** transmit range -setting id ({@value})*/
	public static final String TRANSMIT_RANGE_S = "transmitRange";
	/** range color -setting id ({@value})*/
	public static final String RANGE_COLOR_S = "rangeColor";
	/** transmit speed -setting id ({@value})*/
	public static final String TRANSMIT_SPEED_S = "transmitSpeed";
	/** scanning interval -setting id ({@value})*/
	public static final String SCAN_INTERVAL_S = "scanInterval";

	/** Energy usage per scanning (device discovery) for each operation -setting id ({@value}). */
	public static final String SCAN_ENERGY_S = "scanEnergy";	
	/** Energy usage per scanning (device discovery) for each operation response -setting id 
	 * ({@value}). */
	public static final String SCAN_RSP_ENERGY_S = "scanResponseEnergy";	
	/** Energy usage per second when sending -setting id ({@value}). */
	public static final String TRANSMIT_ENERGY_S = "transmitEnergy";
	/** Energy usage per second when receiving -setting id ({@value}). */
	public static final String RECEIVE_ENERGY_S = "receiveEnergy";
	/** Interface base energy usage per second -setting id ({@value}). */
	public static final String IBASE_ENERGY_S = "iBaseEnergy";
	/** Energy usage per second when sleeping -setting id ({@value}). */
	public static final String SLEEP_ENERGY_S = "sleepEnergy";
	
	/** SyncIS total awake time and total time -setting id ({@value}). */
	public static final String SYNC_IS_S = "syncIS";

	/** Destroy all connection on interface when not isActive() -setting id ({@value}). */
	public static final String DISCONNECT_NOT_ACTIVE_S = "disconnectWhenNotActive";

	/** 
	 * Sub-namespace for the network related settings in the Group namespace
	 * ({@value}) 
	 */
	public static final String NET_SUB_NS = "net";
	
	/** Activeness offset jitter -setting id ({@value})
	 * The maximum amount of random offset for the offset */
	public static final String ACT_JITTER_S = "activenessOffsetJitter";
	
	/** {@link ModuleCommunicationBus} identifier for the "scanning interval" 
    variable. */
	public static final String SCAN_INTERVAL_ID = "Network.scanInterval";
	/** {@link ModuleCommunicationBus} identifier for the "radio range" 
	variable. Value type: double */
	public static final String RANGE_ID = "Network.radioRange";
	/** {@link ModuleCommunicationBus} identifier for the "transmission speed" 
    variable. Value type: integer */
	public static final String SPEED_ID = "Network.speed";
	
	public static final String SCAN_ENERGY_ID = "Network.scanEnergy";
	public static final String SCAN_RSP_ENERGY_ID = "Network.scanResponseEnergy";
	public static final String TRANSMIT_ENERGY_ID = "Network.transmitEnergy";
	public static final String RECEIVE_ENERGY_ID = "Network.receiveEnergy";
	public static final String IBASE_ENERGY_ID = "Network.iBaseEnergy";
	public static final String SLEEP_ENERGY_ID = "Network.sleepEnergy";


	private static final int CON_UP = 1;
	private static final int CON_DOWN = 2;

	private static Random rng;
	protected DTNHost host = null;

	protected String interfacetype;
	protected List<Connection> connections; // connected hosts
	private List<ConnectionListener> cListeners = null; // list of listeners
	private int address; // network interface address
	private Color rangeColor;
	protected double transmitRange;
	protected double oldTransmitRange;
	protected int transmitSpeed;
	protected ConnectivityOptimizer optimizer = null;
	/** scanning interval, or 0.0 if n/a */
	private double scanInterval;
	private double lastScanTime;
	/** energy usage per scan */
	public double scanEnergy;
	/** energy usage in device discovery response per operation */
	public double scanResponseEnergy;
	/** energy usage in transmittion per second */
	public double transmitEnergy;
	/** energy usage in reception per second */
	public double receiveEnergy;
	/** energy usage for interface in base operation per second */
	public double iBaseEnergy;
	/** energy usage sleeping per second */
	public double sleepEnergy;
	/** sleep mode on/off */
	private boolean sleep;
	/** if false does not destroy connection when isActive() (sleep, energy and active movement) is false */
	public boolean disconnectWhenNotActive;
	/** Update interval of sim time */
	public double updateInterval;

	public int[] ctContactsByTime;  // contacts by hour in this interface
	
	/** activeness handler for the node group */
	private ActivenessHandler ah;
	/** maximum activeness jitter value for the node group */
	private int activenessJitterMax;
	/** this interface's activeness jitter value */
	private int activenessJitterValue;

	/** set syncIS values: [0] is the awake time, [1] is the total time. [1]-[0] is the sleep time */
	public double[] syncIS = {0,0};

	static {
		DTNSim.registerForReset(NetworkInterface.class.getCanonicalName());
		reset();
	}
	
	/**
	 * Resets the static fields of the class
	 */
	public static void reset() {
		rng = new Random(0);
	}
	
	/**
	 * For creating an empty class of a specific type
	 */
	public NetworkInterface(Settings s) {
		this.interfacetype = s.getNameSpace();
		this.connections = new ArrayList<Connection>();

		this.transmitRange = s.getDouble(TRANSMIT_RANGE_S);
		this.transmitSpeed = s.getInt(TRANSMIT_SPEED_S);
		ensurePositiveValue(transmitRange, TRANSMIT_RANGE_S);
		ensurePositiveValue(transmitSpeed, TRANSMIT_SPEED_S);

		if (s.contains(SCAN_INTERVAL_S)) {
			this.scanInterval = s.getDouble(SCAN_INTERVAL_S);
		} else {
			this.scanInterval = 0.0;
		}
		if (s.contains(SCAN_ENERGY_S)) {
			this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
			this.scanResponseEnergy = s.getDouble(SCAN_RSP_ENERGY_S);
			this.transmitEnergy = s.getDouble(TRANSMIT_ENERGY_S);
			this.receiveEnergy = s.getDouble(RECEIVE_ENERGY_S);
			this.iBaseEnergy = s.getDouble(IBASE_ENERGY_S);
			this.sleepEnergy = s.getDouble(SLEEP_ENERGY_S);
		} else {
			this.scanEnergy = 0.0;
			this.scanResponseEnergy = 0.0;
			this.transmitEnergy = 0.0;
			this.receiveEnergy = 0.0;
			this.iBaseEnergy = 0.0;
			this.sleepEnergy = 0.0;
		}
		
		if (s.contains(SYNC_IS_S)) {
			this.syncIS = s.getCsvDoubles(SYNC_IS_S,2);
		    if (this.syncIS[0]>=this.syncIS[1]) {
				throw new SettingsError(SYNC_IS_S + " setting must have " + 
						"the first value (awake time) greater than the second value (total time)");
			}
		}

		if (s.contains(DISCONNECT_NOT_ACTIVE_S)) {
			this.disconnectWhenNotActive = s.getBoolean(DISCONNECT_NOT_ACTIVE_S);
		} else {
			this.disconnectWhenNotActive = true;
		}

		/*if (s.contains(RANGE_COLOR_S)) {
			rangeColor = Color.getColor(s.getSetting(RANGE_COLOR_S));	
		} else {
			rangeColor = Color.green;
		}*/
		
	}
	
	/**
	 * For creating an empty class of a specific type
	 */
	public NetworkInterface() {
		this.interfacetype = "Default";
		this.syncIS[0]=0;
		this.syncIS[1]=0;		
		this.sleep = false;
		this.disconnectWhenNotActive = true;
		this.connections = new ArrayList<Connection>();
		this.ctContactsByTime = new int[24]; 
		for (int i=0; i<24; i++)
			this.ctContactsByTime[i]=0;
	}
	
	/**
	 * copy constructor
	 */
	public NetworkInterface(NetworkInterface ni) {
		this.connections = new ArrayList<Connection>();
		this.host = ni.host;
		this.cListeners = ni.cListeners;
		this.interfacetype = ni.interfacetype;
		this.transmitRange = ni.transmitRange;
		this.transmitSpeed = ni.transmitSpeed;
		this.scanEnergy = ni.scanEnergy;
		this.scanResponseEnergy = ni.scanResponseEnergy;
		this.transmitEnergy = ni.transmitEnergy;
		this.receiveEnergy = ni.receiveEnergy;
		this.iBaseEnergy = ni.iBaseEnergy;
		this.sleepEnergy = ni.sleepEnergy;
		this.syncIS = ni.syncIS;
		this.sleep = ni.sleep;
		this.disconnectWhenNotActive = ni.disconnectWhenNotActive;		
		this.ctContactsByTime = new int[24]; 
		for (int i=0; i<24; i++)
			this.ctContactsByTime[i]=0;
		
		this.ah = ni.ah;
		if (ni.activenessJitterMax > 0) {
			this.activenessJitterValue = rng.nextInt(ni.activenessJitterMax);
		} else {
			this.activenessJitterValue = 0;
		}
		
		this.scanInterval = ni.scanInterval;
		/* draw lastScanTime of [0 -- scanInterval] */
		this.lastScanTime = rng.nextDouble() * this.scanInterval;

		this.updateInterval = ni.updateInterval;
	}

	public void setUpdateInterval(double updi) {
		this.updateInterval = updi;
	}

	/**
	 * Replication function
	 */
	abstract public NetworkInterface replicate();

	/**
	 * For setting the host - needed when a prototype is copied for several
	 * hosts
	 * @param host The host where the network interface is
	 */
	public void setHost(DTNHost host) {
		this.host = host;
		ModuleCommunicationBus comBus = host.getComBus();
		
		
		if (!comBus.containsProperty(getInterfaceType()+"."+SCAN_INTERVAL_ID) &&
		    !comBus.containsProperty(getInterfaceType()+"."+RANGE_ID)) {
			// add properties and subscriptions only for each interface //
			// TODO: support for multiple interfaces of the same type (if necessary) //
			//System.out.println(this.scanEnergy);
			comBus.addProperty(getInterfaceType()+"."+SCAN_INTERVAL_ID, this.scanInterval);
			/*comBus.addProperty(getInterfaceType()+"."+RANGE_ID, this.transmitRange);
			comBus.addProperty(getInterfaceType()+"."+SPEED_ID, this.transmitSpeed);
			comBus.addProperty(getInterfaceType()+"."+SCAN_ENERGY_ID, this.scanEnergy);
			comBus.addProperty(getInterfaceType()+"."+SCAN_RSP_ENERGY_ID, this.scanResponseEnergy);
			comBus.addProperty(getInterfaceType()+"."+TRANSMIT_ENERGY_ID, this.transmitEnergy);
			*/
			comBus.subscribe(getInterfaceType()+"."+SCAN_INTERVAL_ID, this);
			/*comBus.subscribe(getInterfaceType()+"."+RANGE_ID, this);
			comBus.subscribe(getInterfaceType()+"."+SPEED_ID, this);
			comBus.subscribe(getInterfaceType()+"."+SCAN_ENERGY_ID, this);
			comBus.subscribe(getInterfaceType()+"."+SCAN_RSP_ENERGY_ID, this);
			comBus.subscribe(getInterfaceType()+"."+TRANSMIT_ENERGY_ID, this);
			*/
			//System.out.println(this.scanEnergy);
		}
		
		
		if (transmitRange > 0) {
			optimizer = ConnectivityGrid.ConnectivityGridFactory(
					this.interfacetype.hashCode(), transmitRange);
			optimizer.addInterface(this);
		} else {
			optimizer = null;
		}
	}
	
	/**
	 * Sets group-based settings for the network interface
	 * @param s The settings object using the right group namespace
	 */
	public void setGroupSettings(Settings s) {
		s.setSubNameSpace(NET_SUB_NS);	
		ah = new ActivenessHandler(s);
		
		if (s.contains(SCAN_INTERVAL_S)) {
			this.scanInterval =  s.getDouble(SCAN_INTERVAL_S);
			//System.out.println(this.scanInterval-10);
		//} else {
		//	this.scanInterval = 0;
		}
		if (s.contains(SCAN_ENERGY_S)) {
			this.scanEnergy =  s.getDouble(SCAN_ENERGY_S);
			//System.out.println(this.scanEnergy-20);
		//} else {
		//	this.scanEnergy = 0;
		}
		if (s.contains(SCAN_RSP_ENERGY_S)) {
			this.scanResponseEnergy =  s.getDouble(SCAN_RSP_ENERGY_S);
		//} else {
		//	this.scanResponseEnergy = 0;
		}
		if (s.contains(TRANSMIT_ENERGY_S)) {
			this.transmitEnergy =  s.getDouble(TRANSMIT_ENERGY_S);
		//} else {
		//	this.transmitEnergy = 0;
		}
		if (s.contains(RECEIVE_ENERGY_S)) {
			this.receiveEnergy =  s.getDouble(RECEIVE_ENERGY_S);
		//} else {
		//	this.transmitEnergy = 0;
		}
		if (s.contains(IBASE_ENERGY_S)) {
			this.iBaseEnergy =  s.getDouble(IBASE_ENERGY_S);
		//} else {
		//	this.transmitEnergy = 0;
		}
		if (s.contains(SLEEP_ENERGY_S)) {
			this.sleepEnergy =  s.getDouble(SLEEP_ENERGY_S);
		//} else {
		//	this.transmitEnergy = 0;
		}
		if (s.contains(DISCONNECT_NOT_ACTIVE_S)) {
			this.disconnectWhenNotActive = s.getBoolean(DISCONNECT_NOT_ACTIVE_S);
		//} else {
		//	this.disconnectWhenNotActive = true;
		}
		if (s.contains(ACT_JITTER_S)) {
			this.activenessJitterMax = s.getInt(ACT_JITTER_S);
		}
		/*if (s.contains(RANGE_COLOR_S)) {
			rangeColor = Color.getColor(s.getSetting(RANGE_COLOR_S));	
		}*/
		
		s.restoreSubNameSpace();
	}

	/**
	 * For checking what interface type this interface is
	 */
	public String getInterfaceType() {
		return interfacetype;
	}
	
	/**
	 * Check if interface is sleeping / Put interface to sleep / Wake up interface
	 */
	public boolean isSleeping() {
		return sleep;
	}

	public void wakeup() { 
		sleep = false;
	}
	
	/**
	 * For Synchronized Intermittent Sleeping mode
	 * @param forceSleep depending or not of is transfering entre in sleep mode
	 * @param forceDestroyConnections if not transfering destroy the connections in the iinterface
	 */
	// TODO: a mode of sleep that wait to connections and and sleep interface
	public boolean sleep(boolean forceSleep, boolean forceDesconnections) { 
		// if not transferring data go to sleep mode
		if (forceSleep || !isTransferring()) {
			this.sleep = true;
			if (forceDesconnections) 
				destroyAllConnections();
		}
		return sleep;
	}
	
	/**
	 * For Synchronized Intermittent Sleeping mode
	 * @param total time
	 * @param part of the total time to be awake
	 */
	public void syncIS() {
		if (this.syncIS[1]-this.syncIS[0]>0) {
			int secsDay = (int)SimClock.getTime()%86400;
			// 8/3 - sleep 5 (3-7), wakeup 3 (0-2)
			// 10/5 - sleep 5 (5-9), wakeup 5 (0-4)
			// 15/5 - sleep 10 (5-14), wakeup 5 (0-4)
			/*
			if ((secDay%2)==0) return true; // sleep 1 wakeup 1
			return false;
			*/
			/*
			if (((secDay/5)%2)==0) return true; // sleep 5 wakeup 5
			return false;
			*/
			if ((secsDay%(this.syncIS[1]-this.syncIS[0]))<this.syncIS[0]) { 
				this.wakeup();
			} else {
				this.sleep(false, false);
			}
		}
	}

	/**
	 * For creating an empty class of a specific type
	 */
	public Double getScanInterval() {
		return this.scanInterval;
	}

	public void setScanInterval(Double scanInterval) {
		ModuleCommunicationBus comBus = this.host.getComBus();
		this.scanInterval = scanInterval;
		comBus.updateProperty(getInterfaceType()+"."+SCAN_INTERVAL_ID, this.scanInterval);
	}


	/**
	 * For setting the connectionListeners
	 * @param cListeners List of connection listeners
	 */
	public void setClisteners(List<ConnectionListener> cListeners) {
		this.cListeners = cListeners;
	}

	/**
	 * Returns the transmit range of this network layer
	 * @return the transmit range
	 */
	public double getTransmitRange() {
		return this.transmitRange;
	}

	/**
	 * Returns the color to draw the range of this network interface
	 * @return the color range
	 */
	public Color getRangeColor() {
		return this.rangeColor;
	}

	/**
	 * Returns the transmit speed of this network layer with respect to the
	 * another network interface
	 * @param ni The other network interface
	 * @return the transmit speed
	 */
	public int getTransmitSpeed(NetworkInterface ni) {
		return this.transmitSpeed;
	}

	/**
	 * Returns a list of currently connected connections
	 * @return a list of currently connected connections
	 */
	public List<Connection> getConnections() {
		return this.connections;
	}
	
	/**
	 * Returns true if the interface is on at the moment (false if not)
	 * @return true if the interface is on at the moment (false if not)
	 */
	public boolean isActive() {
		//boolean active;

		// if iface is sleeping or host has no active movement or host has no energy remain
		if (isSleeping() || !this.host.isMovementActive() || !host.hasEnergy())
			return false;

		if (ah == null)
			return true; /* no activeness handler setted: active */
		
		return ah.isActive(this.activenessJitterValue);

		// Não gostei dessa solucao
		/*if (active == false && this.transmitRange > 0) {
			// not active -> make range 0
			this.oldTransmitRange = this.transmitRange;
			host.getComBus().updateProperty(getInterfaceType()+"."+RANGE_ID, 0.0);
		} else if (active == true && this.transmitRange == 0.0) {
			// active, but range == 0 -> restore range
			host.getComBus().updateProperty(getInterfaceType()+"."+RANGE_ID, 
					this.oldTransmitRange);
		}*/
		//return active;
	}
	
	/**
	 * Checks if this interface is currently in the scanning mode 
	 * and update lastScanTime
	 * @return True if the interface is scanning; false if not
	 */
	public boolean updateScanning() {
		double simTime = SimClock.getTime();
		
		if (!isActive()) {
			return false;
		}
		
		if (scanInterval > 0.0) {
			double delta = simTime - lastScanTime;
			if (delta < 0) {
				return false;   // not time for the first scan
			} 
			double nextScanTime = lastScanTime + scanInterval;
			if (simTime > nextScanTime) {
				//lastScanTime = simTime; /* time to start the next scan round case next update interval*/
				//lastScanTime += scanInterval; /* time to start the next scan round case on the update interval*/
				lastScanTime = (delta > updateInterval) ? simTime : nextScanTime;
				return true;
			}
			if (simTime != lastScanTime ) {
				return false;   // not in the scan round
			}
		}
		return true;   // interval == 0 or still in the same scan round as when last time asked
	}
	
	/**
	 * Returns true if one of the connections of this interface is transferring
	 * data // todo: how to know who is receiving or transmiting?
	 * @return true if the interface transferring
	 */
	public boolean isTransferring() {
		for (Connection c : this.connections) {
			if (c.isTransferring()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if one of the connections of this interface is receiving
	 * data
	 * @return true if the interface transferring
	 */
	public boolean isReceiving() {
		for (Connection c : this.connections) {
			if (c.isTransferring() && !c.isInitiator(host)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if one of the connections of this interface is transmiting
	 * data
	 * @return true if the interface transferring
	 */
	public boolean isTransmiting() {
		for (Connection c : this.connections) {
			if (c.isTransferring() && c.isInitiator(host)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Connects the interface to another interface.
	 * 
	 * Overload this in a derived class.  Check the requirements for
	 * the connection to work in the derived class, then call 
	 * connect(Connection, NetworkInterface) for the actual connection.
	 * @param anotherInterface The interface to connect to
	 */
	public abstract void connect(NetworkInterface anotherInterface);

	/**
	 * Connects this host to another host. The derived class should check 
	 * that all pre-requisites for making a connection are satisfied before 
	 * actually connecting.
	 * @param con The new connection object
	 * @param anotherInterface The interface to connect to
	 */
	protected void connect(Connection con, NetworkInterface anotherInterface) {

		this.connections.add(con);
		notifyConnectionListeners(CON_UP, anotherInterface.getHost());

		// set up bidirectional connection
		anotherInterface.getConnections().add(con);

		// inform routers about the connection
		this.host.connectionUp(con);
		anotherInterface.getHost().connectionUp(con);

	}

	/**
	 * Disconnects this host from another host.  The derived class should
	 * make the decision whether to disconnect or not
	 * @param con The connection to tear down
	 */
	protected void disconnect(Connection con, 
			NetworkInterface anotherInterface) {
		con.setUpState(false);
		notifyConnectionListeners(CON_DOWN, anotherInterface.getHost());

		// tear down bidirectional connection
		if (!anotherInterface.getConnections().remove(con)) {
			throw new SimError("No connection " + con + " found in " +
					anotherInterface);	
			//System.out.println("No connection " + con + " found in " +
			//		anotherInterface);	
		}

		this.host.connectionDown(con);
		anotherInterface.getHost().connectionDown(con);
	}

	/**
	 * Returns true if another interface is within radio range of this interface
	 * and this interface is also within radio range of the another interface.
	 * @param anotherInterface The another interface
	 * @return True if the interface is within range, false if not
	 */
	protected boolean isWithinRange(NetworkInterface anotherInterface) {
		double smallerRange = anotherInterface.getTransmitRange();
		double myRange = getTransmitRange();
		if (myRange < smallerRange) {
			smallerRange = myRange;
		}

		return this.host.getLocation().distance(
				anotherInterface.getHost().getLocation()) <= smallerRange;
	}
	
	/**
	 * Returns true if the given NetworkInterface is connected to this host. 
	 * @param netinterface The other NetworkInterface to check 
	 * @return True if the two hosts are connected
	 */
	protected boolean isConnected(NetworkInterface netinterface) {
		for (int i = 0; i < this.connections.size(); i++) {
			if (this.connections.get(i).getOtherInterface(this) == 
				netinterface) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Makes sure that a value is positive
	 * @param value Value to check
	 * @param settingName Name of the setting (for error's message)
	 * @throws SettingsError if the value was not positive
	 */
	protected void ensurePositiveValue(double value, String settingName) {
		if (value < 0) {
			throw new SettingsError("Negative value (" + value + 
					") not accepted for setting " + settingName);
		}
	}
	
	/**
	 * Updates the state of current connections (ie tears down connections
	 * that are out of range, recalculates transmission speeds etc.).
	 */
	abstract public void update();

	/**
	 * Notifies all the connection listeners about a change in connections.
	 * @param type Type of the change (e.g. {@link #CON_DOWN} )
	 * @param otherHost The other host on the other end of the connection.
	 */
	private void notifyConnectionListeners(int type, DTNHost otherHost) {
		if (this.cListeners == null) {
			return;
		}
		for (ConnectionListener cl : this.cListeners) {
			switch (type) {
			case CON_UP:
				cl.hostsConnected(this.getInterfaceType(), this.host, otherHost);
				break;
			case CON_DOWN:
				cl.hostsDisconnected(this.getInterfaceType(), this.host, otherHost);
				break;
			default:
				assert false : type;	// invalid type code
			}
		}
	}
	
	/**
	 * This method is called by the {@link ModuleCommunicationBus} when/if
	 * someone changes the scanning interval, transmit speed, or range
	 * @param key Identifier of the changed value
	 * @param newValue New value for the variable
	 */
	
	public void moduleValueChanged(String key, Object newValue) {
		if (key.equals(getInterfaceType()+"."+SCAN_INTERVAL_ID)) {
			this.scanInterval = (Double)newValue;	
		}
		/*else if (key.equals(getInterfaceType()+"."+SPEED_ID)) {
			this.transmitSpeed = (Integer)newValue;	
		}
		else if (key.equals(getInterfaceType()+"."+RANGE_ID)) {
			this.transmitRange = (Double)newValue;	
		}
		else if (key.equals(getInterfaceType()+"."+SCAN_ENERGY_ID)) {
			this.scanEnergy = (Double)newValue;	
		}
		else if (key.equals(getInterfaceType()+"."+SCAN_RSP_ENERGY_ID)) {
			this.scanResponseEnergy = (Double)newValue;	
		}
		else if (key.equals(getInterfaceType()+"."+TRANSMIT_ENERGY_ID)) {
			this.transmitEnergy = (Double)newValue;	
		}
		else {
			throw new SimError("Unexpected combus ID " + key);
		}*/

	}
	

	/** 
	 * Creates a connection to another host. This method does not do any checks
	 * on whether the other node is in range or active 
	 * (cf. {@link #connect(NetworkInterface)}).
	 * @param anotherInterface The interface to create the connection to
	 */
	public abstract void createConnection(NetworkInterface anotherInterface);


	/**
	 * Disconnect a connection between this and another host.
	 * @param anotherInterface The other host's network interface to disconnect 
	 * from this host
	 */
	public void destroyAllConnections() {
		for (int i=0; i<this.connections.size(); i++) {
			Connection con = this.connections.get(i);
			NetworkInterface anotherInterface = con.getOtherInterface(this);
			disconnect(con,anotherInterface);
			connections.remove(i);
		}
	}
	
	/**
	 * Disconnect a connection between this and another host.
	 * @param anotherInterface The other host's network interface to disconnect 
	 * from this host
	 */
	public void destroyConnection(NetworkInterface anotherInterface) {
		DTNHost anotherHost = anotherInterface.getHost();
		for (int i=0; i < this.connections.size(); i++) {
			if (this.connections.get(i).getOtherNode(this.host) == anotherHost && 
			    anotherInterface.getInterfaceType().equals(interfacetype)){
				removeConnectionByIndex(i, anotherInterface);
			}
		}
		// the connection didn't exist, do nothing
	}

	/**
	 * Removes a connection by its position (index) in the connections array
	 * of the interface
	 * @param index The array index of the connection to be removed
	 * @param anotherInterface The interface of the other host
	 */
	private void removeConnectionByIndex(int index, 
			NetworkInterface anotherInterface) {
		Connection con = this.connections.get(index);
		DTNHost anotherNode = anotherInterface.getHost();
		con.setUpState(false);
		notifyConnectionListeners(CON_DOWN, anotherNode);

		// tear down bidirectional connection
		if (!anotherInterface.getConnections().remove(con)) {
			throw new SimError("No connection " + con + " found in " +
					anotherNode);   
			//System.out.println("No connection " + con + " found in " +
			//		anotherNode);   
		}

		this.host.connectionDown(con);
		anotherNode.connectionDown(con);

		connections.remove(index);
	}

	/**
	 * Returns the DTNHost of this interface
	 */
	public DTNHost getHost() {
		return host;
	}

	/**
	 * Returns the current location of the host of this interface. 
	 * @return The location
	 */
	public Coord getLocation() {
		return host.getLocation();
	}

	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 */
	public String toString() {
		return this.address + " of " + this.host + 
			". Connections: " +	this.connections;
	}

}
