package com.babylonhealth;

import com.babylonhealth.neo4j.GraphDB;
import com.babylonhealth.ontology.ElementNames;
import org.neo4j.graphdb.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by Ilya on 14-Oct-16
 */
public class OntologyReader {

    public static void main(String[] args) {
        GraphDatabaseService graphDb = GraphDB.getGraphDBInstance();
        printOntologies(graphDb);
    }

    private static void printOntologies(GraphDatabaseService graphDb) {
        try (Transaction tx = graphDb.beginTx()) {
            // search for ontologies
            ResourceIterator<Node> nodes = graphDb.findNodes(ElementNames.OntologyElements.ONTOLOGY);
            int i = 1;
            while (nodes.hasNext()) {
                System.out.println("Ontology #" + i);
                System.out.println("Annotations:");
                Node ontologyNode = nodes.next();
                Map<String, Object> allProperties = ontologyNode.getAllProperties();
                for (String s : allProperties.keySet()) {
                    System.out.println(s + ": " + allProperties.get(s));
                }

                Iterable<Relationship> relationships = ontologyNode.getRelationships(Direction.OUTGOING, ElementNames.RelTypes.HAS_CLASS);
                Iterator<Relationship> it = relationships.iterator();
                Set<Node> rootNodes = new HashSet<>();
                int count = 0;
                while (it.hasNext()) {
                    Node node = it.next().getEndNode();
                    //check if it is a top level node
                    Iterable<Relationship> r = node.getRelationships(Direction.INCOMING, ElementNames.RelTypes.IS_A);
                    if (!r.iterator().hasNext()) {
                        rootNodes.add(node);
                    }
                    count++;
                }
                System.out.println("Ontology has " + count + " classes out of which " + rootNodes.size()
                        + " are top level classes");
                System.out.println("Ontology structure");
                for (Node n : rootNodes) {
                    printNodeData(null, n);
                }

                i++;
            }
            nodes.close();

            tx.success();
        }

    }

    private static void printNodeData(Node parent, Node child) {
        System.out.println(child.getProperty(ElementNames.LABEL));
        if (parent != null) {
            System.out.println(" child of " + parent.getProperty(ElementNames.LABEL));
        }
        Iterable<Relationship> instances = child.getRelationships(Direction.OUTGOING, ElementNames.RelTypes.INSTANCE_OF);
        Iterator<Relationship> it = instances.iterator();
        if (it.hasNext()) {
            System.out.println("Instances:");
            while (it.hasNext()) {
                System.out.println(it.next().getEndNode().getProperty(ElementNames.LABEL));
            }
        } else {
            System.out.println("No instances");
        }

        System.out.println("\n\n");
        Iterable<Relationship> children = child.getRelationships(Direction.OUTGOING, ElementNames.RelTypes.IS_A);
        it = children.iterator();
        while (it.hasNext()) {
            printNodeData(child, it.next().getEndNode());
        }
    }

}
