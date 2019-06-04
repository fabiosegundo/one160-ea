/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import core.Settings;
import core.SettingsError;

/**
 * Message creation -external events generator. Creates one message from
 * source node/nodes (defined with {@link MessageEventGenerator#HOST_RANGE_S})
 * to each destination nodes (defined with
 * {@link MessageEventGenerator#TO_HOST_RANGE_S}). 
 * The message size, first messages time and the intervals between creating 
 * messages can be configured like with {@link MessageEventGenerator}. End
 * time is not respected, but messages are created until there's a message for
 * every destination node. 
 * It creates messages regarding interval and continuously
 * @see MessageEventGenerator
 */
public class OneFromToEachMessageGenerator extends MessageEventGenerator {
	private List<Integer> toIds;
	private List<Integer> fromIds;
	private boolean endToIds = false;
	private boolean endFromIds = false;
	
	public OneFromToEachMessageGenerator(Settings s) {
		super(s);
		this.toIds = new ArrayList<Integer>();
		this.fromIds = new ArrayList<Integer>();
		
		if (toHostRange == null) {
			throw new SettingsError("Destination host (" + TO_HOST_RANGE_S + 
					") must be defined");
		}
		
		for (int i = hostRange[0]; i < hostRange[1]; i++) {
			fromIds.add(i);
		}
		//Collections.shuffle(fromIds, rng);		
		for (int i = toHostRange[0]; i < toHostRange[1]; i++) {
			toIds.add(i);
		}
		//Collections.shuffle(toIds, rng);
		endToIds=false;
		endFromIds=false;
	}
	
	/** 
	 * Returns the next message creation event
	 * @see input.EventQueue#nextEvent()
	 */
	public ExternalEvent nextEvent() {
		int responseSize = 0; // zero stands for one way messages -  no responses requested
		int msgSize;
		int interval;
		int from;
		int to;
		
		/* Get two nodes from the lits */
		if (this.toIds.size() == 0) {    // oops, no more to addresses
			endToIds=true;
			for (int i = toHostRange[0]; i < toHostRange[1]; i++) {
				toIds.add(i);
			}				
		}
		to = this.toIds.remove(0);

		if (this.fromIds.size() == 0) {  // oops, no more from addresses
			endFromIds=true;
			for (int i = hostRange[0]; i < hostRange[1]; i++) {
				fromIds.add(i);
			}
		}
		from = this.fromIds.remove(0);
		
		if (to == from) { // skip self
			if (fromIds.size()>toIds.size()) { // in this case, get the next id from who has more ids
				from = this.fromIds.remove(0);
			} else {
				if (toIds.size()==0) { // if toIDs and fromIDs size igual to 0, reload toIDs
					endFromIds=true;
					for (int i = toHostRange[0]; i < toHostRange[1]; i++) {
						toIds.add(i);
					}				
				}
				to = this.toIds.remove(0);
			}  
		}

		/*if (endToIds && endFromIds) { // two list were empty almost one time
			//this.nextEventsTime = Double.MAX_VALUE;  // no messages lef
			endFromIds=false;
			endToIds=false;

		}*/
				
		msgSize = drawMessageSize();
		
		/* Create event and advance to next event */
		MessageCreateEvent mce = new MessageCreateEvent(from, to, this.getID(), 
				msgSize, responseSize, this.nextEventsTime);
		//System.out.println(from+" "+to+" "+this.getID()+" "+interval+" "+this.nextEventsTime);
		interval = drawNextEventTimeDiff();
		this.nextEventsTime += interval;	

		if (this.msgTime != null && this.nextEventsTime > this.msgTime[1]) {
			/* next event would be later than the end time */
			this.nextEventsTime = Double.MAX_VALUE;
		}
		
		return mce;
	}

}