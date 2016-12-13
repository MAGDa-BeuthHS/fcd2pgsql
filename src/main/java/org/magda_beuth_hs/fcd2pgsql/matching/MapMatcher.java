package org.magda_beuth_hs.fcd2pgsql.matching;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.weighting.FastestWeighting;
import com.graphhopper.util.GPXEntry;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import org.magda_beuth_hs.fcd2pgsql.util.FCDEntry;

/**
 * Authors: Felix Kunde, Manal Faraj
 */
public class MapMatcher {

	private GraphHopperOSM hopper;
	private CarFlagEncoder encoder;
	private AlgorithmOptions opts;
	
    private static String OSM;
    private static String GHLOCATION;
	
    public MapMatcher(String args[]) {
    	OSM = args[5];
    	GHLOCATION = args[6];
    	init();
    }
    
    private void init() {
    	File ghDirectory  = new File(GHLOCATION);
        //delete GH if it exists
        if(ghDirectory.exists() && ghDirectory.isDirectory()){
            try {

                FileUtils.deleteDirectory(ghDirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // import OpenStreetMap data
        hopper = new GraphHopperOSM();
        hopper.setDataReaderFile(OSM);

        hopper.setGraphHopperLocation(GHLOCATION);
        encoder = new CarFlagEncoder();
        hopper.setEncodingManager(new EncodingManager(encoder));
        hopper.getCHFactoryDecorator().setEnabled(false);
        hopper.importOrLoad();
        
        //for map matching core version 8.2
        opts = AlgorithmOptions.start()
        		.algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(hopper.getTraversalMode())
                .weighting(new FastestWeighting(encoder))
                .maxVisitedNodes(10000)
                .hints(new HintsMap().put("weighting", "fastest").put("vehicle", encoder.toString()))
        		.build();
    }

    public List<GPXEntry> doMapMatching(List<GPXEntry> gpxUnmatched) {
    	
    	List<GPXEntry> gpxMatched = new ArrayList<GPXEntry>();
        MapMatching mapMatching = new MapMatching(hopper, opts);
        mapMatching.setMeasurementErrorSigma(50);

        // perform map matching, return null if it fails
        MatchResult mr = null;
        try {
        	mr = mapMatching.doWork(gpxUnmatched);
        }
        catch (Exception ex) {
        	//System.out.println("MapMatching error: " + ex.getMessage());
        	return null;
        } 
        
        // get points of matched track
        Path path = mapMatching.calcPath(mr);
        PointList points = path.calcPoints();
        if (points != null && !points.isEmpty()) {
	        for (GHPoint pt : points) {
	        	// set elevation and time to zero for now
		      	gpxMatched.add(new FCDEntry(pt.lat, pt.lon, 0.0, 0, 0));
		    }
        }
        
	    return gpxMatched;
    }   
}
