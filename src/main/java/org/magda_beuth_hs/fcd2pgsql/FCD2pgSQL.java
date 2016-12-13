package org.magda_beuth_hs.fcd2pgsql;

import java.sql.SQLException;

import org.magda_beuth_hs.fcd2pgsql.database.DBConnector;
import org.magda_beuth_hs.fcd2pgsql.database.FCDImporter;
import org.magda_beuth_hs.fcd2pgsql.matching.MapMatcher;

/**
 * Authors: Felix Kunde
 */
public class FCD2pgSQL {
    
	private MapMatcher mapMatcher;
	private FCDImporter fcdImporter;
	
	public static void main(String[] args) {
		new FCD2pgSQL().run(args);
	}
	
	private void run(String[] args) {
		
		// create new MapMatcher
		try {
			mapMatcher = new MapMatcher(args);
		}
		catch (Exception ex) {
			System.out.println("Could not intialize MapMatcher!");
			ex.printStackTrace();
		}
		
		
		try {
			// create new FCDImporter
			fcdImporter = new FCDImporter(
					DBConnector.createConnection(args),
					mapMatcher);
			
			// perform matching and import
			if (fcdImporter.isConnected()) {
				fcdImporter.start();
				fcdImporter.close();
			}
			
			System.out.println("Process finished.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
}
