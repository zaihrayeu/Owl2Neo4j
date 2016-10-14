package com.babylonhealth.ontology;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

/**
 * Created by Ilya on 13-Oct-16
 */
public class ElementNames {

    public final static String LABEL = "label";

    public enum RelTypes implements RelationshipType {
        HAS_CLASS, IS_A, INSTANCE_OF
    }

    public enum OntologyElements implements Label {ONTOLOGY, CLASS, INSTANCE}
}
