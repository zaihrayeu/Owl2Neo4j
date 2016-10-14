package com.babylonhealth;

import com.babylonhealth.neo4j.GraphDB;
import com.babylonhealth.ontology.ElementNames;
import com.babylonhealth.ontology.OntologyUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
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

    private static int classCount = 0;
    private static int isaCount = 0;
    private static int disjointCount = 0;
    private static int instanceOfCount = 0;
    private static int objPropertyCount = 0;
    private static int domainLinksCount = 0;
    private static int rangeLinksCount = 0;
    private static int subPropertyLinksCount = 0;
    private static int inversePropertyLinksCount = 0;
    private static int someValueFromRestrictionsCount = 0;


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
            // Reset counters
            classCount = 0;
            isaCount = 0;
            disjointCount = 0;
            instanceOfCount = 0;
            objPropertyCount = 0;
            domainLinksCount = 0;
            rangeLinksCount = 0;
            subPropertyLinksCount = 0;

            // create a node to represent the ontology
            Node ontologyNode = createOntologyNode(ontology, graphDb);

            // maps OWL entities to neo4j nodes
            Map<OWLClass, Node> nodeMap = createNodesForClasses(ontology, ontologyNode, graphDb);

            // Create IS-A links between node classes
            createIsaHierarchy(ontology, nodeMap);

            // Create disjoint links between node classes
            createDisjointClassesLinks(ontology, nodeMap);

            // Create nodes for instances and link then to class nodes
            createInstances(ontology, graphDb, nodeMap);

            // Object property definitions
            // NOTE: only simple definitions are supported, when both domains and ranges are specified as classes
            // maps properties to noe4j nodes
            Map<OWLObjectProperty, Node> propMap = createObjectPropertyDefinitions(ontology, graphDb, nodeMap);

            // Object property hierarchy construction
            createObjectPropertyHierarchy(ontology, propMap);

            // Inverse links for Object properties
            createInverseObjectPropertyLinks(propMap);

            // Some values from restrictions
            createSomeValueFromRestrictions(ontology, nodeMap, propMap);

            tx.success();

            System.out.println("Nodes created: " + classCount);
            System.out.println("IS_A links created: " + isaCount);
            System.out.println("Disjoint classes links created: " + disjointCount);
            System.out.println("INSTANCE_OF links created: " + instanceOfCount);
            System.out.println("Object property definitions created: " + objPropertyCount);
            System.out.println("Domain links created: " + domainLinksCount);
            System.out.println("Range links created: " + rangeLinksCount);
            System.out.println("Sub property links created: " + subPropertyLinksCount);
            System.out.println("Inverse property links created: " + inversePropertyLinksCount);
            System.out.println("Some values from restriction links created: " + someValueFromRestrictionsCount);
        }
    }

    private static Node createOntologyNode(OWLOntology ontology, GraphDatabaseService graphDb) {
        Node ontologyNode = graphDb.createNode(ElementNames.OntologyElements.ONTOLOGY);
        for (OWLAnnotation annotation : ontology.getAnnotations()) {
            ontologyNode.setProperty(annotation.getProperty().toString(),
                    annotation.getValue().toString());
        }
        return ontologyNode;
    }

    private static Map<OWLClass, Node> createNodesForClasses(OWLOntology ontology, Node ontologyNode,
                                                             GraphDatabaseService graphDb) {
        // maps OWL entities to neo4j nodes
        Map<OWLClass, Node> nodeMap = new HashMap<>();
        // Create nodes for classes from the ontology
        // Use the class name as the label for the node
        for (OWLClass c : ontology.getClassesInSignature()) {
            String className = getClassName(c);
            Node node = graphDb.createNode(ElementNames.OntologyElements.CLASS);
            node.setProperty(ElementNames.URI, c.getIRI().toString());
            node.setProperty(ElementNames.LABEL, className);
            nodeMap.put(c, node);
            ontologyNode.createRelationshipTo(node, ElementNames.RelTypes.HAS_CLASS);
            classCount++;
        }
        return nodeMap;
    }

    private static void createIsaHierarchy(OWLOntology ontology, Map<OWLClass, Node> nodeMap) {
        // Create IS-A links between node classes
        for (OWLClass c : ontology.getClassesInSignature()) {
            Set<OWLSubClassOfAxiom> subClassOfAxioms = ontology.getSubClassAxiomsForSuperClass(c);
            for (OWLSubClassOfAxiom axiom : subClassOfAxioms) {
                if (!axiom.getSubClass().isAnonymous()) {
                    OWLClass child = axiom.getSubClass().asOWLClass();
                    nodeMap.get(c).createRelationshipTo(nodeMap.get(child), ElementNames.RelTypes.IS_A);
                    isaCount++;
                }
            }
        }
    }

    private static void createDisjointClassesLinks(OWLOntology ontology, Map<OWLClass, Node> nodeMap) {
        for (OWLClass c : ontology.getClassesInSignature()) {
            Set<OWLDisjointClassesAxiom> disjointClassesAxioms = ontology.getDisjointClassesAxioms(c);
            for (OWLDisjointClassesAxiom axiom : disjointClassesAxioms) {
                for (OWLClassExpression e : axiom.getClassExpressions()) {
                    if (!e.isAnonymous() && !e.asOWLClass().equals(c)) {
                        nodeMap.get(c).createRelationshipTo(nodeMap.get(e.asOWLClass()),
                                ElementNames.RelTypes.DISJOINT);
                        disjointCount++;
                    }
                }
            }
        }
    }

    private static void createInstances(OWLOntology ontology, GraphDatabaseService graphDb,
                                        Map<OWLClass, Node> nodeMap) {
        // Create nodes for instances and link then to class nodes
        for (OWLNamedIndividual individual : ontology.getIndividualsInSignature()) {
            String name = getClassName(individual);
            Node instanceNode = graphDb.createNode(ElementNames.OntologyElements.INSTANCE);
            instanceNode.setProperty(ElementNames.URI, individual.getIRI().toString());
            instanceNode.setProperty(ElementNames.LABEL, name);
            for (OWLClassExpression type : EntitySearcher.getTypes(individual, ontology)) {
                Node instanceClassNode = nodeMap.get(type.asOWLClass());
                if (instanceClassNode != null) {
                    instanceNode.createRelationshipTo(instanceClassNode, ElementNames.RelTypes.INSTANCE_OF);
                    instanceOfCount++;
                }
            }
        }
    }

    private static Map<OWLObjectProperty, Node> createObjectPropertyDefinitions(OWLOntology ontology, GraphDatabaseService graphDb,
                                                                                Map<OWLClass, Node> nodeMap) {
        // Object property definitions
        // NOTE: only simple definitions are supported, when both domains and ranges are specified as classes
        Map<OWLObjectProperty, Node> propMap = new HashMap<>(); // maps properties to noe4j nodes
        for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
            String name = getClassName(property);
            Node node = graphDb.createNode(ElementNames.OntologyElements.OBJECT_PROPERTY);
            node.setProperty(ElementNames.URI, property.getIRI().toString());
            node.setProperty(ElementNames.LABEL, name);
            propMap.put(property, node);
            // add domain links
            Set<OWLObjectPropertyDomainAxiom> objectPropertyDomainAxioms = ontology.getObjectPropertyDomainAxioms(property);
            for (OWLObjectPropertyDomainAxiom a : objectPropertyDomainAxioms) {
                if (!a.getDomain().isAnonymous()) { // i.e., it is a named OWL class
                    node.createRelationshipTo(nodeMap.get(a.getDomain().asOWLClass()),
                            ElementNames.RelTypes.HAS_DOMAIN);
                    domainLinksCount++;
                }
            }
            // add range links
            Set<OWLObjectPropertyRangeAxiom> objectPropertyRangeAxioms = ontology.getObjectPropertyRangeAxioms(property);
            for (OWLObjectPropertyRangeAxiom a : objectPropertyRangeAxioms) {
                if (!a.getRange().isAnonymous()) { // i.e., it is a named OWL class
                    node.createRelationshipTo(nodeMap.get(a.getRange().asOWLClass()),
                            ElementNames.RelTypes.HAS_RANGE);
                    rangeLinksCount++;
                }
            }
            objPropertyCount++;
        }
        return propMap;
    }

    private static void createObjectPropertyHierarchy(OWLOntology ontology, Map<OWLObjectProperty, Node> propMap) {
        // Object property hierarchy construction
        for (OWLObjectProperty property : propMap.keySet()) {
            Set<OWLSubObjectPropertyOfAxiom> subProperties =
                    ontology.getObjectSubPropertyAxiomsForSuperProperty(property);
            for (OWLSubObjectPropertyOfAxiom axiom : subProperties) {
                OWLObjectProperty p = axiom.getSubProperty().asOWLObjectProperty();
                propMap.get(property).createRelationshipTo(propMap.get(p), ElementNames.RelTypes.SUB_OBJECT_PROPERTY);
                subPropertyLinksCount++;
            }
        }
    }

    private static void createInverseObjectPropertyLinks(Map<OWLObjectProperty, Node> propMap) {
        for (OWLObjectProperty property : propMap.keySet()) {
            OWLObjectPropertyExpression inverseProperty = property.getInverseProperty();
            if (inverseProperty != null && !inverseProperty.isAnonymous()) {
                OWLObjectProperty p = inverseProperty.getNamedProperty();
                propMap.get(property).createRelationshipTo(propMap.get(p), ElementNames.RelTypes.INVERSE_OBJECT_PROPERTY);
                inversePropertyLinksCount++;
            }
        }
    }

    /**
     * creates some value from restriction links as follows: the link is created from the class node that has the
     * restriction and the link points to the object property node. the link type is SOME_VALUES_FROM_RESTRICTION.
     * the link is annotated with a property that sotres the URI of the target class for the restriction (owl:someValuesFrom)
     *
     * @param ontology input ontology
     * @param nodeMap  map from OWL classes to neo4j nodes
     * @param propMap  map from OWL object properties to neo4j nodes
     */
    private static void createSomeValueFromRestrictions(OWLOntology ontology, Map<OWLClass, Node> nodeMap,
                                                        Map<OWLObjectProperty, Node> propMap) {
        for (OWLClass c : ontology.getClassesInSignature()) {
            Set<OWLClassAxiom> tempAx = ontology.getAxioms(c);
            for (OWLClassAxiom ax : tempAx) {
                for (OWLClassExpression nce : ax.getNestedClassExpressions())
                    if (nce.getClassExpressionType() != ClassExpressionType.OWL_CLASS) {
                        // not very elegant, as a last minute solution..
                        if (ax instanceof OWLSubClassOfAxiom &&
                                ((OWLSubClassOfAxiom) ax).getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                            OWLObjectSomeValuesFrom ax1 = (OWLObjectSomeValuesFrom) ((OWLSubClassOfAxiom) ax).getSuperClass();
                            Relationship rel = nodeMap.get(c).createRelationshipTo(propMap.get(ax1.getProperty().asOWLObjectProperty()),
                                    ElementNames.RelTypes.SOME_VALUES_FROM_RESTRICTION);
                            OWLClassExpression filler = ax1.getFiller();
                            if (!filler.isAnonymous()) {
                                Node target = nodeMap.get(filler.asOWLClass());
                                rel.setProperty(ElementNames.SOME_VALUES_FROM_RESTRICTION_TARGET_CLASS_URI,
                                        target.getProperty(ElementNames.URI));
                                someValueFromRestrictionsCount++;
                            }
                        }
                    }
            }
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
