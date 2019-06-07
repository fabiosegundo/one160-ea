/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package input;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import java.math.BigDecimal;

import util.Tuple;

import core.Coord;
import core.SettingsError;


/**
 * Reader for ExternalMovement movement model's time-location tuples.
 * <P>
 * First line of the file should be the offset header. Syntax of the header
 * should be:<BR>
 * <CODE>normalize gpsCoord minTime maxTime minY maxY minX maxX minZ maxZ</CODE>
 * <BR>
 * Fields are separated by a space.
 * nomalize is boolean: to normalize or not beginning at 0,0.
 * convGpsCoord is boolean: to convert or not lat, long to y, x position in meters.
 * Last two values (Z-axis) are ignored at the moment but can be present 
 * in the file.
 * <P>
 * Following lines' syntax should be:<BR>
 * <CODE>time id xPos yPos</CODE><BR>
 * where <CODE>time</CODE> is the time when a node with <CODE>id</CODE> should
 * be at location <CODE>(yPos, xPos)</CODE>.
 * </P>
 * <P>
 * All lines must be sorted by time. Sampling interval (time difference between
 * two time instances) must be same for the whole file.
 * </P>
 */
public class ExternalMovementReader {
	/* Prefix for comment lines (lines starting with this are ignored) */
	public static final String COMMENT_PREFIX = "#";
	private Scanner scanner;
	private double lastTimeStamp = -1;
	private String lastLine;
	private double minTime;
	private double maxTime;
	private double minX;
	private double maxX;
	private double minY;
	private double maxY;
	private double gpsWidth;
	private double gpsHeight;
	private boolean normalize;
	private boolean convGpsCoord;
    	static int precisonFloating = 3; // use 3 decimals after comma for rounding
		
	/**
	 * Constructor. Creates a new reader that reads the data from a file.
	 * @param inFilePath Path to the file where the data is read
	 * @throws SettingsError if the file wasn't found
	 */
	public ExternalMovementReader(String inFilePath) {
		this.normalize = true;
		this.convGpsCoord  = true;
		this.gpsWidth  = 0;
		this.gpsHeight = 0;
		File inFile = new File(inFilePath);
		try {
			scanner = new Scanner(inFile);
		} catch (FileNotFoundException e) {
			throw new SettingsError("Couldn't find external movement input " +
					"file " + inFile);
		}
		
		String offsets = scanner.nextLine();
	
		try {
			Scanner lineScan = new Scanner(offsets);
			normalize = lineScan.nextBoolean();
			convGpsCoord = lineScan.nextBoolean();
			minTime = lineScan.nextDouble();
			maxTime = lineScan.nextDouble();
			minY = lineScan.nextDouble();
			maxY = lineScan.nextDouble();
			minX = lineScan.nextDouble();
			maxX = lineScan.nextDouble();
		} catch (Exception e) {
			throw new SettingsError("Invalid offset line '" + offsets + "'");
		}

		if (convGpsCoord) {
		    	gpsWidth = geoDistance(minY, minX, minY, maxX);
		    	gpsHeight  = geoDistance(minY, minX, maxY, minX);
			System.out.println("Geographic area dimensions are: (lat) height (Y) " 
				+ gpsHeight + "m, (lon) width (X) " + gpsWidth + "m");
		}
		
		lastLine = scanner.nextLine();
	}
	
	/**
	 * Sets normalizing of read values on/off. If on, values returned by 
	 * {@link #readNextMovements()} are decremented by minimum values of the
	 * offsets. Default is on (normalize).
	 * @param normalize If true, normalizing is on (false -> off).
	 */
	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}
	
	/**
	 * Reads all new id-coordinate tuples that belong to the same time instance
	 * @return A list of tuples or empty list if there were no more moves
	 * @throws SettingError if an invalid line was read
	 */
	public List<Tuple<String, Coord>> readNextMovements() {
		
		ArrayList<Tuple<String, Coord>> moves = new ArrayList<Tuple<String, Coord>>();
		
		if (!scanner.hasNextLine()) {
			return moves;
		}
		
		Scanner lineScan = new Scanner(lastLine);
		double time = lineScan.nextDouble();
		String id = lineScan.next();
		double y = lineScan.nextDouble();
		double x = lineScan.nextDouble();
		double ox=x, oy=y;
		double xx, yy;
		
		if (convGpsCoord) {
			xx = geoDistance(y, x, y, minX);
			yy = geoDistance(y, x, minY, x);
		} else {
			xx=x;
			yy=y;
		}
		
		if (normalize) {
			time -= minTime;
			if (convGpsCoord) {
				x = xx; // - gpsWidt;
				y = yy; // - gpsHeighth;
			} else {
			    x -= minX;
			    y -= minY;
			}
		}
		
		lastTimeStamp = time;
		
		while (scanner.hasNextLine() && lastTimeStamp == time) {
			lastLine = scanner.nextLine();
			
			if (lastLine.trim().length() == 0 || 
					lastLine.startsWith(COMMENT_PREFIX)) {
				continue; /* skip empty and comment lines */
			}
						
			// add previous line's tuple only if in the boundaries
			if (normalize && convGpsCoord) {
				if (ox<minX || ox>maxX || oy<minY || oy>maxY) {
					System.out.print("Excluded. ");
					System.out.println("Geographic point (Lon="+ox+",Lat="+oy+") converted is: (widht x="+x+"m, height y="+y+"m).");
				} else {
					moves.add(new Tuple<String, Coord>(id, new Coord(x,y)));
				}
			} else {
				moves.add(new Tuple<String, Coord>(id, new Coord(x,y)));
			}
			
			
			lineScan = new Scanner(lastLine);
			
			try {
				time = lineScan.nextDouble();
				id = lineScan.next();
				y = lineScan.nextDouble();
				x = lineScan.nextDouble();
				ox=x; oy=y;
			} catch (Exception e) {
				throw new SettingsError("Invalid line '" + lastLine + "'");
			}
			
			if (convGpsCoord) {
				xx = geoDistance(y, x, y, minX);
				yy = geoDistance(y, x, minY, x);
			} else {
				xx=x;
				yy=y;
			}
			
			if (normalize) {
				time -= minTime;
				if (convGpsCoord) {
					x = xx; // - gpsWidth;
					y = yy; // - gpsHeight;
				} else {
					x -= minX;
					y -= minY;
				}
			}
			
		}
		
		// add previous line's tuple only if in the boundaries
		if (normalize && convGpsCoord) {
			if (ox<minX || ox>maxX || oy<minY || oy>maxY) {
				System.out.print("Excluded. ");
				System.out.println("GGeographic point (Lon="+ox+",Lat="+oy+") converted is: (widht x="+x+"m, height y="+y+"m).");
			} else {
				if (!scanner.hasNextLine()) {	// add the last tuple of the file
					moves.add(new Tuple<String, Coord>(id, new Coord(x,y)));
				}
			}
		} else {
			if (!scanner.hasNextLine()) {	// add the last tuple of the file
				moves.add(new Tuple<String, Coord>(id, new Coord(x,y)));
			}
		}
		
		return moves;
	}
	
	/**
	 * Returns the time stamp where the last moves read with 
	 * {@link #readNextMovements()} belong to.
	 * @return The time stamp
	 */
	public double getLastTimeStamp() {
		return lastTimeStamp;
	}

	/**
	 * Returns offset maxTime
	 * @return the maxTime
	 */
	public double getMaxTime() {
		return maxTime;
	}

	/**
	 * Returns offset maxX
	 * @return the maxX
	 */
	public double getMaxX() {
		return maxX;
	}

	/**
	 * Returns offset maxY
	 * @return the maxY
	 */
	public double getMaxY() {
		return maxY;
	}

	/**
	 * Returns offset minTime
	 * @return the minTime
	 */
	public double getMinTime() {
		return minTime;
	}

	/**
	 * Returns offset minX
	 * @return the minX
	 */
	public double getMinX() {
		return minX;
	}

	/**
	 * Returns offset minY
	 * @return the minY
	 */
	public double getMinY() {
		return minY;
	}

	/**
	 * Returns the distance between two gps fixes in meters
	 * @return the distance between two gps fixes in meters
	 */
	public double geoDistance(double lat1, double lon1, double lat2, double lon2){
		double R = 6371; 
		double dLat = Math.toRadians(lat2-lat1);
		double dLon = Math.toRadians(lon2-lon1); 
		double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * 
		Math.sin(dLon/2) * Math.sin(dLon/2); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		double distance = (R * c * 1000.0d);
		distance = round(distance, precisonFloating);
		return distance;
	}

	/**
	 * Returns rounding with bigDecimal
	 * @return the rounding with bigDecimal
	 */
	public static double round(double d, int decimalPlace){
		BigDecimal bd = new BigDecimal(Double.toString(d));
		bd = bd.setScale(decimalPlace,BigDecimal.ROUND_HALF_UP);
		return bd.doubleValue();
	}
	
}
