package org.magda_beuth_hs.fcd2pgsql.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.magda_beuth_hs.fcd2pgsql.util.FCDEntry;
import org.postgis.LineString;
import org.postgis.Point;

/**
 * Authors: Manal Faraj, Felix Kunde
 */
public class DBUtil {
	
    public static void prepareDB(Connection connection) {

    	Statement createSeq = null;
    	Statement createTable = null;
    	
        try {
        	// first create a sequence for the new table
            createSeq = connection.createStatement();            
            createSeq.executeUpdate("CREATE SEQUENCE matched_tracks_occupied_seq");
            System.out.println("Sequence 'matched_tracks_seq' successfully created.");
        } catch (SQLException e) {
        	System.out.println("Sequence 'matched_tracks_seq' already exists.");
        }
        
        try {
            // now create the new table
            createTable = connection.createStatement();
            StringBuilder table = new StringBuilder()
            		.append("CREATE TABLE matched_tracks_occupied (")
            		.append("id INT PRIMARY KEY NOT NULL DEFAULT nextval('matched_tracks_occupied_seq'),")
            		.append("start_time TIMESTAMP WITHOUT TIME ZONE,")
            		.append("end_time TIMESTAMP WITHOUT TIME ZONE,")
            		.append("geom GEOMETRY(LineStringZM,4326)")
                    .append(")");
            createTable.executeUpdate(table.toString());
            System.out.println("Table 'matched_tracks' successfully created.");
        } catch (SQLException e) {
        	System.out.println("Table 'matched_tracks' already exists.");
        }
        
        finally {
    		// closing statements
    		if (createSeq != null) {
    			try {
    				createSeq.close();
    			} catch (SQLException e) {
    				//;
    			}
    			createSeq = null;
    		}
    		if (createTable != null) {
    			try {
    				createTable.close();
    			} catch (SQLException e) {
    				//;
    			}
    			createTable = null;
    		}
        }
    }
	
	public static LineString createLinestring(List<FCDEntry> fcdEntries) {
        List<Point> points = new ArrayList<Point>();
        for(FCDEntry fcdEntry : fcdEntries) {
            Point point = new Point(fcdEntry.lon, fcdEntry.lat, (double) fcdEntry.getSpeed());
            point.setM(fcdEntry.getTime()/1000); // we don't need milliseconds
            point.setSrid(4326);
            points.add(point);
        }
        LineString lineString = new LineString(points.toArray(new Point[]{}));
        lineString.srid = 4326;
        return lineString;
    }
	
}
