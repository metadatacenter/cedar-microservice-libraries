package org.metadatacenter.server.search.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueSetsExtractorTest {

  private static final String NS = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/";

  @TempDir
  Path tempDir;

  @Test
  void loadsDirectHierarchyAndEverySupportedAnnotationFromARealOntology() throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory data = manager.getOWLDataFactory();
    OWLOntology ontology = manager.createOntology();
    OWLClass base = data.getOWLClass(IRI.create(NS + "base"));
    OWLClass child = data.getOWLClass(IRI.create(NS + "child"));
    manager.addAxiom(ontology, data.getOWLDeclarationAxiom(base));
    manager.addAxiom(ontology, data.getOWLDeclarationAxiom(child));
    manager.addAxiom(ontology, data.getOWLSubClassOfAxiom(child, base));
    Map<IRI, String> literals = Map.of(
        IRI.create("http://purl.org/dc/terms/identifier"), "VS-17",
        IRI.create("http://purl.org/dc/terms/hasVersion"), "4.2",
        IRI.create("http://www.w3.org/2004/02/skos/core#notation"), "VS17",
        OWLRDFVocabulary.RDFS_COMMENT.getIRI(), "A curated value set",
        OWLRDFVocabulary.RDFS_LABEL.getIRI(), "Value set 17",
        IRI.create("https://schema.org/startTime"), "2024-01-01",
        IRI.create("https://schema.org/endTime"), "2025-01-01");
    literals.forEach((property, value) -> manager.addAxiom(ontology,
        data.getOWLAnnotationAssertionAxiom(data.getOWLAnnotationProperty(property), child.getIRI(),
            data.getOWLLiteral(value))));
    IRI related = IRI.create("https://terminology.example/value-set/17");
    manager.addAxiom(ontology, data.getOWLAnnotationAssertionAxiom(
        data.getOWLAnnotationProperty(SKOSVocabulary.RELATEDMATCH.getIRI()), child.getIRI(), related));

    ValueSetsExtractor extractor = load(manager, ontology, "complete.owl");

    assertEquals(Set.of(child.getIRI().toString()), extractor.getSubClassURIs(base.getIRI().toString()));
    assertEquals("VS-17", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.IDENTIFIER).orElseThrow());
    assertEquals("4.2", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.VERSION).orElseThrow());
    assertEquals("VS17", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.NOTATION).orElseThrow());
    assertEquals(related.toString(), extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.RELATED_MATCH).orElseThrow());
    assertEquals("A curated value set", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.COMMENT).orElseThrow());
    assertEquals("Value set 17", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.LABEL).orElseThrow());
    assertEquals("2024-01-01", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.START_TIME).orElseThrow());
    assertEquals("2025-01-01", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.END_TIME).orElseThrow());
  }

  @Test
  void extractsAnnotationsFromClassesUsedWithoutExplicitDeclarationAxioms() throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory data = manager.getOWLDataFactory();
    OWLOntology ontology = manager.createOntology();
    OWLClass base = data.getOWLClass(IRI.create(NS + "base"));
    OWLClass child = data.getOWLClass(IRI.create(NS + "implicit-child"));
    manager.addAxiom(ontology, data.getOWLSubClassOfAxiom(child, base));
    manager.addAxiom(ontology, data.getOWLAnnotationAssertionAxiom(data.getRDFSLabel(), child.getIRI(),
        data.getOWLLiteral("Implicitly declared child")));

    ValueSetsExtractor extractor = load(manager, ontology, "implicit.owl");

    assertEquals("Implicitly declared child", extractor.getAnnotation(child.getIRI().toString(),
        ValueSetsExtractor.Annotation.LABEL).orElseThrow());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ignoresAnonymousClassRelationshipsWithoutDiscardingValidHierarchy(boolean anonymousSuperclass)
      throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory data = manager.getOWLDataFactory();
    OWLOntology ontology = manager.createOntology();
    OWLClass base = data.getOWLClass(IRI.create(NS + "base"));
    OWLClass validChild = data.getOWLClass(IRI.create(NS + "valid-child"));
    OWLClass condition = data.getOWLClass(IRI.create(NS + "condition"));
    manager.addAxiom(ontology, data.getOWLSubClassOfAxiom(validChild, base));
    if (anonymousSuperclass) {
      manager.addAxiom(ontology, data.getOWLSubClassOfAxiom(validChild,
          data.getOWLObjectIntersectionOf(base, condition)));
    } else {
      manager.addAxiom(ontology, data.getOWLSubClassOfAxiom(
          data.getOWLObjectIntersectionOf(validChild, condition), base));
    }

    ValueSetsExtractor extractor = load(manager, ontology, "anonymous.owl");

    assertEquals(Set.of(validChild.getIRI().toString()),
        extractor.getSubClassURIs(base.getIRI().toString()));
    assertTrue(extractor.getBaseClassURIs().contains(base.getIRI().toString()));
  }

  private ValueSetsExtractor load(OWLOntologyManager manager, OWLOntology ontology, String fileName) throws Exception {
    Path ontologyPath = tempDir.resolve(fileName);
    manager.saveOntology(ontology, IRI.create(ontologyPath.toUri()));
    ValueSetsExtractor extractor = ValueSetsExtractor.getInstance();
    extractor.loadValueSetsOntology(ontologyPath.toString());
    return extractor;
  }
}
