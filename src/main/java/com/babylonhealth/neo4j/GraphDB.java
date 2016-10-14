package com.babylonhealth.neo4j;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.File;

/**
 * Created by Ilya on 13-Oct-16
 */
public class GraphDB {

    private static final String DB_PATH = ".\\db";

    private static GraphDatabaseService graphDb = null;

    public static synchronized GraphDatabaseService getGraphDBInstance() {
        if (graphDb == null) {
            graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File(DB_PATH));
            registerShutdownHook(graphDb);
        }

        return graphDb;
    }


    private static void registerShutdownHook(final GraphDatabaseService graphDb) {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }
}
