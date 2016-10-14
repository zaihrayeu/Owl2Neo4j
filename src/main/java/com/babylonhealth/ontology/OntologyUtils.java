package com.babylonhealth.ontology;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.File;

/**
 * Created by Ilya on 13-Oct-16
 */
public class OntologyUtils {

    public static OWLOntology loadOWLOntologyFromFile(String path) throws OWLOntologyCreationException {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) throw new IllegalArgumentException("Invalid path to OWL file");
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        return manager.loadOntologyFromOntologyDocument(file);
    }
}
