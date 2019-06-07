/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.HashSet;
import java.util.List;

import core.DTNHost;
import core.Settings;
import core.SimError;
import core.UpdateListener;

import core.EnergyModel;

/**
 * Node energy level report. Reports the energy level of all (or only some) 
 * nodes every configurable-amount-of seconds. Writes reports only after
 * the warmup period.
 */
public class EnergyLevelReport extends Report implements UpdateListener {
	/** Reporting granularity -setting id ({@value}). 
	 * Defines the interval how often (seconds) a new snapshot of energy levels
	 * is created */
	public static final String GRANULARITY = "granularity";
	/** Optional reported nodes (comma separated list of network addresses). 
	 * By default all nodes are reported. */
	public static final String REPORTED_NODES = "nodes";

	/** value of the granularity setting */
	protected final int granularity;
	/** time of last update*/
	protected double lastUpdate; 
	/** Networks addresses (integers) of the nodes which are reported */
	protected HashSet<Integer> reportedNodes;

	private EnergyModel energy;	

	//public double[] initEnergy;	

	/**
	 * Constructor. Reads the settings and initializes the report module.
	 */
	public EnergyLevelReport() {
		Settings settings = getSettings();
		this.lastUpdate = 0;	
		this.granularity = settings.getInt(GRANULARITY);
		
		if (settings.contains(REPORTED_NODES)) {
			this.reportedNodes = new HashSet<Integer>();
			for (Integer nodeId : settings.getCsvInts(REPORTED_NODES)) {
				this.reportedNodes.add(nodeId);
			}
		}
		else {
			this.reportedNodes = null;
		}
		
		init();
	}

	/**
	 * Creates a new snapshot of the energy levels if "granularity" 
	 * seconds have passed since the last snapshot. 
	 * @param hosts All the hosts in the world
	 */
	public void updated(List<DTNHost> hosts) {
		double simTime = getSimTime();
		if (isWarmup()) {
			return; /* warmup period is on */
		}
		/* creates a snapshot once every granularity seconds */
		if (simTime - lastUpdate >= granularity) {
			createSnapshot(hosts);
			this.lastUpdate = simTime - simTime % granularity;
		}
	}
	
	/**
	 * Creates a snapshot of energy levels 
	 * @param hosts The list of hosts in the world
	 */
	private void createSnapshot(List<DTNHost> hosts) {
		write ("[" + (int)getSimTime() + "]"); /* simulation time stamp */
		for (DTNHost h : hosts) {
			//if (this.reportedNodes != null && 
			//	!this.reportedNodes.contains(h.getAddress())) {
					
			if ((h.getAddress()<0) || (h.getAddress()>392)) {
				continue; /* node not in the list */
			}

			// TODO: verify why energy model is not configured before isMovementActivated is working.
			Double energyIbase = 0.0;
			Double energyScan = 0.0;
			Double energyTransmit = 0.0;
			Double energyReceive = 0.0;
			Double energySleep = 0.0;
			Double energyBase = (Double)h.getComBus().getProperty(EnergyModel.ENERGY_BASE_ID);
			if (energyBase != null) {
				//energy = new EnergyModel(getSettings());
				//value = energy.getEnergy(this.initEnergy); // indicating energy model is not initialized
				//value = -1.0;
				energyIbase    = (Double)h.getComBus().getProperty(EnergyModel.ENERGY_IBASE_ID);
				energyScan     = (Double)h.getComBus().getProperty(EnergyModel.ENERGY_SCAN_ID);
				energyTransmit = (Double)h.getComBus().getProperty(EnergyModel.ENERGY_TRANSMIT_ID);
				energyReceive  = (Double)h.getComBus().getProperty(EnergyModel.ENERGY_RECEIVE_ID);
				energySleep    = (Double)h.getComBus().getProperty(EnergyModel.ENERGY_SLEEP_ID);				
			} else {
				energyBase = 0.0;
			}

			//Double value = h.energy.getFullEnergy();
			//if (h.energy==null) {
			//if (h.isMovementActive()) {
			//		throw new SimError("Host " + h + 
			//				" is not using energy model");
			//}
			if (h.energy!=null)
				write(h.toString() + " " +  format(h.energy.getEnergy()) +" "+ 
					energyBase +" "+ energyIbase +" "+ energySleep +" "+ energyScan +" "+ 
					energyTransmit +" "+ energyReceive);
		}
	
	}
	
}
