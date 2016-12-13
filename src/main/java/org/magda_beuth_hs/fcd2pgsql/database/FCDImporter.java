package org.magda_beuth_hs.fcd2pgsql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.postgis.LineString;
import org.postgis.PGgeometry;

import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.GPXEntry;

import org.magda_beuth_hs.fcd2pgsql.matching.MapMatcher;
import org.magda_beuth_hs.fcd2pgsql.matching.FCDMatcher;
import org.magda_beuth_hs.fcd2pgsql.util.FCDEntry;

/**
 * Authors: Felix Kunde, Manal Faraj
 */
public class FCDImporter {
    private PreparedStatement insertStmt;
    private Connection connection;
    private MapMatcher mapMatcher;
    
    private List<Integer> senders;
    private int batchLimit = 20000;
    private int batchCounter = 0;

    public FCDImporter(Connection connection, MapMatcher mapMatcher) {
    	this.connection = connection;
    	this.mapMatcher = mapMatcher;
    	init();
    }
    
    public void init() {
        // create table where results are inserted
    	DBUtil.prepareDB(connection);
        
    	try {
    		// set variables for DB import
        	connection.setAutoCommit(false);
    		insertStmt = connection.prepareStatement("INSERT INTO matched_tracks (start_time, end_time, geom) VALUES (?, ?, ?)");
        
    		// fetch FCD senders
    		senders = fetchSenders();
    	}
    	catch (SQLException sqlEx) {
    		System.out.println("Could not initialize FCDImporter setup!");
    	}
    }
    
    public boolean isConnected() throws SQLException {
    	return !connection.isClosed();
    }
    
    public void start() throws SQLException {
        
    	// for each carID process all GPS points
        // TODO: parallelize here
    	for (int carID : senders) {
    		int importedTracks = processFCD(carID);
    	    // execute statements that are in the batch
    	    if (insertStmt != null)
    	    	executeBatch();
    		System.out.println("MapMatched and imported " + importedTracks + " tracks using car id " + carID);
    	}
    }
    
	public void executeBatch() throws SQLException {
		insertStmt.executeBatch();
		connection.commit();
		batchCounter = 0;
	}

	public void close() throws SQLException {
		insertStmt.close();
		connection.close();
	}

    private ArrayList<Integer> fetchSenders() throws SQLException {
    	
    	ArrayList<Integer> fcdSenders = new ArrayList<Integer>();
    	
    	// first fetch all car_ids in one list
        Statement sCarID = connection.createStatement();
        ResultSet result_carID = sCarID.executeQuery("SELECT DISTINCT car_id FROM floating_car_data ORDER BY car_id");

        // iterate over all car_ids
        while (result_carID.next())
        	fcdSenders.add((Integer)result_carID.getInt(1));
        
        // close result set
		if (result_carID != null) {
			try {
				result_carID.close();
			} catch (SQLException e) {
				throw e;
			}

			result_carID = null;
		}
		// close statement
		if (sCarID != null) {
			try {
				sCarID.close();
			} catch (SQLException e) {
				throw e;
			}

			sCarID = null;
		}
		
	    System.out.println("FCD senders fetched");
	    return fcdSenders;
	}
    
    private int processFCD(int carID) throws SQLException {
    	List<FCDEntry> fcdEntryList = new ArrayList<FCDEntry>();
    	DistanceCalc distanceCalc = new DistancePlaneProjection();
        long startTime = 0;
        long endTime = 0;
        int prevIndex = 0;
        double gpxLength = 0;
        long timeLength = 0;
        int importedTracks = 0;
        
        // get all GPS points by the given car_id  
        PreparedStatement sGPX = connection.prepareStatement(
        		"SELECT longitude, latitude, gps_time, speed FROM floating_car_data WHERE car_id = ? ORDER BY gps_time"
        		);
        sGPX.setInt(1, carID);
        ResultSet result_GPX = sGPX.executeQuery();
        
        // build up a GPX track
        while (result_GPX.next()) {
            double longitude = result_GPX.getDouble(1);
            double latitude = result_GPX.getDouble(2);
            Timestamp timestamp = result_GPX.getTimestamp(3);
            int speed = result_GPX.getInt(4);
            
            endTime = timestamp.getTime();
                        
            // start new list if empty
            if (fcdEntryList.isEmpty()) {
            	startTime = endTime;
            	fcdEntryList.add(new FCDEntry(latitude, longitude, 0, startTime, speed));
            }
            else {
            	// get previous entry
            	GPXEntry prevEntry = fcdEntryList.get(prevIndex++);

            	// calculate spatial and temporal offset
                gpxLength = distanceCalc.calcDist(prevEntry.lat, prevEntry.lon, latitude, longitude);
                timeLength = endTime - prevEntry.getTime();
                
                // check if offset is too big to continue track
                if (gpxLength > 1000 || timeLength > 60000) {
                	if (fcdEntryList.size() > 2) {
                		endTime = prevEntry.getTime();
	                	importedTracks += doImport(startTime, endTime, fcdEntryList) ? 1 : 0;
                	}
                	// 3. start new track
                	fcdEntryList.clear();
                	prevIndex = 0;
                	startTime = endTime;
                }
                // add new gpx point to list
                fcdEntryList.add(new FCDEntry(latitude, longitude, 0, endTime, speed));
            }
        }
        
        // import last existing track in pipeline
        if (fcdEntryList.size() > 1) {
        	importedTracks += doImport(startTime, endTime, fcdEntryList) ? 1 : 0;
    	}
        
        // close result set
		if (result_GPX != null) {
			try {
				result_GPX.close();
			} catch (SQLException e) {
				throw e;
			}

			result_GPX = null;
		}
		// close prepared statement
		if (sGPX != null) {
			try {
				sGPX.close();
			} catch (SQLException e) {
				throw e;
			}

			sGPX = null;
		}
        
        return importedTracks;
    }


    private boolean doImport(long startTime, long endTime, List<FCDEntry> fcdPoints) throws SQLException {
    	
    	List<GPXEntry> unmatchedPoints = new ArrayList<GPXEntry>();
    	List<FCDEntry> matchedPoints = new ArrayList<FCDEntry>();
    	
    	// prepare GPX list for MapMatcher
    	for (FCDEntry fcdEntry : fcdPoints)
    		unmatchedPoints.add(fcdEntry.toGPXEntry());
    	
    	// first: map match the FCD subset
    	List<GPXEntry> matchedGPX = mapMatcher.doMapMatching(unmatchedPoints);
    	
    	if (matchedGPX == null)
    		return false;
    	else    	
    		// convert GPX list to FCDEntry list for TimeMatcher
    		for (GPXEntry gpxEntry : matchedGPX)
    			matchedPoints.add(new FCDEntry(gpxEntry,0));
    	
    	// second: transfer timestamp of gpxPoints to matchedPoints
    	matchedPoints = FCDMatcher.doFCDMatching(fcdPoints, matchedPoints);
    	
    	// third: fill gaps in linestring
    	matchedPoints = FCDMatcher.fillGaps(matchedPoints);
        
    	// if there are still enough points after filling the gaps proceed with database import
    	if (matchedPoints.size() > 1) {
	    	// fourth: transform list of FCD entries into a PostGIS LineString
	    	LineString lineString = DBUtil.createLinestring(matchedPoints);
	    	
	    	// fifth: prepare insert statement
	        PGgeometry lineStringGeo = new PGgeometry(lineString.toString());
	        insertStmt.setTimestamp(1, new Timestamp(startTime));
	        insertStmt.setTimestamp(2, new Timestamp(endTime));
	        insertStmt.setObject(3, lineStringGeo);
	
	        // sixth: add statement to batch
	        insertStmt.addBatch();        
			if (++batchCounter == batchLimit)
				executeBatch();
		
			return true;
    	}
    	else
    		return false;
	}
}
