package com.babylonhealth;

import com.babylonhealth.neo4j.GraphDB;
import com.babylonhealth.ontology.ElementNames;
import com.babylonhealth.ontology.OntologyUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by Ilya on 13-Oct-16
 */
public class OntologyLoader {

    public static void main(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("OWL file location not specified");
        GraphDatabaseService graphDb = GraphDB.getGraphDBInstance();
        try {
            OWLOntology owlOntology = OntologyUtils.loadOWLOntologyFromFile(args[0]);
            loadOntology(owlOntology, graphDb);
            System.out.println("FINISHED: Ontology loaded in neo4j successfully");
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

    private static void loadOntology(OWLOntology ontology, GraphDatabaseService graphDb) {
        try (Transaction tx = graphDb.beginTx()) {

            int isaCount = 0;
            int instanceOfCount = 0;

            // create a node to represent an ontology
            Node ontologyNode = graphDb.createNode(ElementNames.OntologyElements.ONTOLOGY);
            for (OWLAnnotation annotation : ontology.getAnnotations()) {
                ontologyNode.setProperty(annotation.getProperty().toString(),
                        annotation.getValue().toString());
            }

            // maps OWL classes to neo4j nodes
            Map<OWLClass, Node> nodeMap = new HashMap<>();

            // Create nodes for classes from the ontology
            // Use the class name as the label for the node
            for (OWLClass c : ontology.getClassesInSignature()) {
                String className = getClassName(c);
                Node node = graphDb.createNode(ElementNames.OntologyElements.CLASS);
                node.setProperty(ElementNames.LABEL, className);
                nodeMap.put(c, node);
                ontologyNode.createRelationshipTo(node, ElementNames.RelTypes.HAS_CLASS);
            }

            // Create IS-A links between node classes
            for (OWLClass c : ontology.getClassesInSignature()) {
                Set<OWLSubClassOfAxiom> subClassOfAxioms = ontology.getSubClassAxiomsForSuperClass(c);
                for (OWLSubClassOfAxiom axiom : subClassOfAxioms) {
                    // it should be an OWLClass otherwise an exception will be thrown in the next line
                    OWLClass child = axiom.getSubClass().asOWLClass();
                    nodeMap.get(c).createRelationshipTo(nodeMap.get(child), ElementNames.RelTypes.IS_A);
                    isaCount++;
                }
            }

            // Create nodes for instances and link then to class nodes
            for (OWLNamedIndividual individual : ontology.getIndividualsInSignature()) {
                String name = getClassName(individual);
                Node instanceNode = graphDb.createNode(ElementNames.OntologyElements.INSTANCE);
                instanceNode.setProperty(ElementNames.LABEL, name);
                for (OWLClassExpression type : EntitySearcher.getTypes(individual, ontology)) {
                    Node instanceClassNode = nodeMap.get(type.asOWLClass());
                    if (instanceClassNode != null) {
                        instanceNode.createRelationshipTo(instanceClassNode, ElementNames.RelTypes.INSTANCE_OF);
                        instanceOfCount++;
                    }
                }
            }

            // Object properties
//                for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
//                property.
//            }


            tx.success();

            System.out.println("Nodes created: " + nodeMap.size());
            System.out.println("IS_A links created: " + isaCount);
            System.out.println("INSTANCE_OF links created: " + instanceOfCount);

        }
    }


    private static String getClassName(OWLEntity entity) {
        String entityClassName = entity.toString();
        if (entityClassName.contains("#")) {
            entityClassName = entityClassName.substring(
                    entityClassName.indexOf("#") + 1, entityClassName.lastIndexOf(">"));
        }
        return entityClassName;
    }
}
