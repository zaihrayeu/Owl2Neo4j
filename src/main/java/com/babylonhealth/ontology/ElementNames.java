package com.babylonhealth.ontology;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

/**
 * Created by Ilya on 13-Oct-16
 */
public class ElementNames {

    public final static String URI = "uri";
    public final static String LABEL = "label";

    public enum RelTypes implements RelationshipType {
        HAS_CLASS, IS_A, DISJOINT, INSTANCE_OF, HAS_DOMAIN, HAS_RANGE, SUB_OBJECT_PROPERTY, INVERSE_OBJECT_PROPERTY
    }

    public enum OntologyElements implements Label {ONTOLOGY, CLASS, INSTANCE, OBJECT_PROPERTY}
}
