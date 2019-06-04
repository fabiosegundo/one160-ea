/* 
 * Copyright 2011 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package core;

import java.util.Random;

import core.*;

/**
 * Energy model for routing modules. Handles power use from scanning (device
 * discovery), scan responses, and data transmission. If scanning is done more 
 * often than 1/s, constant scanning is assumed (and power consumption does not
 * increase from {@link #scanEnergy} value).
 */
public class EnergyModel { // implements ModuleCommunicationListener {
	/** Initial units of energy -setting id ({@value}). Can be either a 
	 * single value, or a range of two values. In the latter case, the used
	 * value is a uniformly distributed random value between the two values. */
	public static final String INIT_ENERGY_S = "initialEnergy";
	
	/** Base energy usage per second -setting id ({@value}). */
	public static final String BASE_ENERGY_S = "baseEnergy";
	
	/** Energy update warmup period -setting id ({@value}). Defines the 
	 * simulation time after which the energy level starts to decrease due to 
	 * scanning, transmissions, etc. Default value = 0. If value of "-1" is 
	 * defined, uses the value from the report warmup setting 
	 * {@link report.Report#WARMUP_S} from the namespace 
	 * {@value report.Report#REPORT_NS}. */
	public static final String WARMUP_S = "energyWarmup";

	/** {@link ModuleCommunicationBus} identifier for the "current amount of 
	 * energy left" variable. Value type: double */
	public static final String ENERGY_VALUE_ID = "Energy.value";
	
	/** Initial energy levels from the settings */
	private final double[] initEnergy;
	private final double[] baseEnergy;
	private double warmupTime;
	/** current energy level */
	private double fullCharge;
	/** current energy level */
	private double currentEnergy;
	/** used base energy */
	private double usedBaseEnergy;
	/** sim time of the last energy updated */
	private double lastUpdate;

	private static Random rng = null;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public EnergyModel(Settings s) {
		this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
		if (this.initEnergy.length != 1 && this.initEnergy.length != 2) {
			throw new SettingsError(INIT_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		}
		
		this.baseEnergy = s.getCsvDoubles(BASE_ENERGY_S);
		if (this.baseEnergy.length != 1 && this.baseEnergy.length != 2) {
			throw new SettingsError(BASE_ENERGY_S + " setting must have " + 
					"either a single value or two comma separated values");
		}
		
		if (s.contains(WARMUP_S)) {
			this.warmupTime = s.getInt(WARMUP_S);
			if (this.warmupTime == -1) {
				this.warmupTime = new Settings(report.Report.REPORT_NS).
					getInt(report.Report.WARMUP_S);
			}
		}
		else {
			this.warmupTime = 0;
		}
	}
	
	/**
	 * Copy constructor.
	 * @param proto The model prototype where setting values are copied from
	 */
	protected EnergyModel(EnergyModel proto) {
		this.initEnergy = proto.initEnergy;
		this.currentEnergy = getRandomEnergy(this.initEnergy);
		this.baseEnergy = proto.baseEnergy;
		this.usedBaseEnergy = getRandomEnergy(this.baseEnergy);
		this.fullCharge = getFullCharge(this.initEnergy);
		this.warmupTime  = proto.warmupTime;
		this.lastUpdate = 0;
	}
	
	public EnergyModel replicate() {
		return new EnergyModel(this);
	}
	
	/**
	 * returns the higuest initial energy value.
	 * @param range The min and max values of the range, or if only one value
	 * is given, that is used as the energy level
	 */
	protected double getRandomEnergy(double range[]) {
		return (range.length == 1) ? range[0] : range [1];
	}
	
	/**
	 * Sets the current energy level into the given range using uniform 
	 * random distribution.
	 * @param range The min and max values of the range, or if only one value
	 * is given, that is used as the energy level
	 */
	protected double getFullCharge(double range[]) {
		if (range.length == 1) {
			return range[0];
		} else {
			if (rng == null) {
				rng = new Random((int)(range[0] + range[1]));
			}
			return range[0] + 
				rng.nextDouble() * (range[1] - range[0]);
		}
	}
	
	/**
	 * Sets and Returns the current energy level
	 * @param range The min and max values of the range, or if only one value
	 * @return the current energy level
	 */
	//public double getEnergy(double range[]) {
	//	this.currentEnergy = getRandomEnergy(range);
	//	return this.currentEnergy;
	//}
	
	/**
	 * Returns the current energy level
	 * @return the current energy level
	 */
	public double getEnergy() {
		return this.currentEnergy;
	}
	
	/**
	 * Updates the current energy so that the given amount is reduced from it.
	 * If the energy level goes below zero, sets the level to zero.
	 * Does nothing if the warmup time has not passed.
	 * @param amount The amount of energy to reduce
	 */
	protected void reduceEnergy(double amount) {
		if (SimClock.getTime() < this.warmupTime) {
			return;
		}
		
		if (amount >= this.currentEnergy) {
			this.currentEnergy = 0.0;
		} else {
			this.currentEnergy -= amount;
		}
		
		/*if (comBus == null) {
			return; // model not initialized (via update) yet
		}
		
		if (amount >= this.currentEnergy) {
			comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
		} else {
			comBus.updateDouble(ENERGY_VALUE_ID, -amount);
		}
		*/
		
	}
	
	/**
	 * Reduces the energy reserve for the amount that is used when another
	 * host connects (does device discovery)
	 */
	public void reduceDiscoveryEnergy(NetworkInterface iface) {
		//reduceEnergy(this.scanResponseEnergy);
		reduceEnergy(iface.scanResponseEnergy);
		//List<NetworkInterface> ifaceList = getHost().getInterfaces();
		//int i=1;
		//for (i=1; i<=ifaceList.size(); i++) {
		//	NetworkInterface iface = getHost().getInterface(i);
		//	reduceEnergy(iface.scanResponseEnergy);	
		//}
	}
	
	/**
	 * Reduces the energy reserve for the amount that is used by sending data
	 * , base operation and scanning for the other nodes. 
	 */
	public void update(DTNHost host) {
		double simTime = SimClock.getTime();
		double delta = simTime - this.lastUpdate;
		
		if (delta>0) {
			if (host.isMovementActive()) {
				if (usedBaseEnergy>0) {
					reduceEnergy(delta * usedBaseEnergy);
				}

				// TODO: add support for other interfaces of the same type in the host
				// do not use updateScanning or isScanning (it apply scan and it is miss next scan opportunity)
				for (NetworkInterface iface : host.getInterfaces()) {
					if (iface.isActive() && iface.getTransmitRange() > 0) {
						if (iface.isTransferring()) {
							// TODO: simplify usiing isInitiator and assume the same characteristic of the net interface to the other side of connection
							for (Connection c : iface.getConnections()) {
								if (c.getFromInterface() == iface) {							
									//System.out.println(simTime+" "+this.lastUpdate+" "+delta+" "+iface.getScanInterval()+" "+scansQtd);
									// reduce energy for sending data
									reduceEnergy(delta * iface.transmitEnergy);
									// reduce energy for reception in other node
									NetworkInterface oiface = c.getToInterface();
									DTNHost oh = oiface.getHost();
									if (oh.energy!=null && oiface.isActive() && oiface.getTransmitRange() > 0) {
										oh.energy.reduceEnergy(delta * oiface.receiveEnergy);
									}
								}
							}
						} else {
							// scanning at this update round
							// scan quantity = delta / iface.getScanInterval();
							reduceEnergy(delta * iface.scanEnergy / iface.getScanInterval());
						}
					}
					if (iface.isSleeping()) {
						// reduce energy in sleeping for network interface 
						reduceEnergy(delta * iface.sleepEnergy);
					} else {
						// reduce base energy for network interface 
						reduceEnergy(delta * iface.iBaseEnergy);
					}
				}

				// New verson with send and receive energy reduction, by connection
				/*for (Connection c : host.getConnections()) {
					NetworkInterface iface = c.getFromInterface();
					if (iface.isActive() && c.isTransferring()) {
						// reduce energy if sending data
						reduceEnergy(delta * iface.transmitEnergy);
						// reduce energy for reception in other node 
						DTNHost oh = c.getOtherNode(host); 
						NetworkInterface oiface = c.getToInterface();
						if (oh.energy!=null && oiface.isActive()) {
							oh.energy.reduceEnergy(delta * oiface.receiveEnergy);
						}
					}
				}*/
				
			}
		}
			
		this.lastUpdate = simTime;
	}
		
	/**
	 * Reduces the base energy
	 */
	/*public void updateBase(double Delta) {
		double simTime = SimClock.getTime();
		double delta = simTime - this.lastUpdate;
		
		
		this.lastUpdate = simTime;
	}*/
		
	/**
	 * Called by the combus if the energy value is changed
	 * @param key The energy ID
	 * @param newValue The new energy value
	 */
	/* public void moduleValueChanged(String key, Object newValue) {
		this.currentEnergy = (Double)newValue;
	}*/
	
}