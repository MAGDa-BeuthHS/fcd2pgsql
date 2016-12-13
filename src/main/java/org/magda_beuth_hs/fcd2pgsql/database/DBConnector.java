package org.magda_beuth_hs.fcd2pgsql.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Authors: Manal Faraj, Felix Kunde
 */
public class DBConnector {

    private static Connection connection;
	
    public static Connection createConnection(String[] args) {
        //Load the JDBC driver and establish a connection.
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(
            		"jdbc:postgresql://" + args[0] + ":" + args[1] + "/" + args[2] + "?reWriteBatchedInserts=true",
            		args[3], args[4]);
            System.out.println("Opened database successfully");
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return connection;
    }
}
