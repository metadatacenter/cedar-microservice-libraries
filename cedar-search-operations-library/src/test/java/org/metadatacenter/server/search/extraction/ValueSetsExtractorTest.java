package org.metadatacenter.server.search.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.exception.CedarProcessingException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueSetsExtractorTest {

  private static final String NS = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/";

  @TempDir
  Path tempDir;

  @Test
  void returnsSameInstance() {
    assertSame(ValueSetsExtractor.getInstance(), ValueSetsExtractor.getInstance());
  }

  @Test
  void firstProjectionLoadsOntologyAndSubsequentProjectionsUseTheSnapshot() throws Exception {
    Path file = writeYesNoOntology("Yes");
    ValueSetsExtractor extractor = new ValueSetsExtractor();
    var values = extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString());
    assertEquals(Set.of("Yes", "Y"), values.getValueLabels());
    assertEquals(Set.of("C49488"), values.getValueConcepts());
    Files.delete(file);
    assertEquals(values, extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString()));
  }

  @Test
  void unrelatedFieldsDoNotNeedTheCadsrOntology() throws Exception {
    ValueSetsExtractor extractor = new ValueSetsExtractor();
    assertTrue(extractor.extractPossibleValues(Set.of(), null).getValueLabels().isEmpty());
    assertTrue(extractor.extractPossibleValues(Set.of("https://example.org/another-valueset"), null)
        .getValueLabels().isEmpty());
  }

  @Test
  void missingOntologyRefusesProjectionAndCanRecoverWhenFileArrives() throws Exception {
    ValueSetsExtractor extractor = new ValueSetsExtractor();
    Path file = tempDir.resolve("yes-no.owl");
    assertThrows(CedarProcessingException.class,
        () -> extractor.extractPossibleValues(Set.of(NS + "yes-no"), null));
    assertThrows(CedarProcessingException.class,
        () -> extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString()));
    writeYesNoOntology("Yes");
    assertEquals(Set.of("Yes", "Y"),
        extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString()).getValueLabels());
  }

  @Test
  void explicitReloadUpdatesTheSnapshotAndFailedReloadPreservesIt() throws Exception {
    Path file = writeYesNoOntology("Yes");
    ValueSetsExtractor extractor = new ValueSetsExtractor();
    extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString());
    writeYesNoOntology("Affirmative");
    extractor.loadValueSetsOntology(file.toString());
    assertEquals(Set.of("Affirmative", "Y"),
        extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString()).getValueLabels());
    Files.delete(file);
    assertThrows(CedarProcessingException.class, () -> extractor.loadValueSetsOntology(file.toString()));
    assertThrows(CedarProcessingException.class, () -> extractor.extractPossibleValues(Set.of(NS + "yes-no"),
        tempDir.resolve("different.owl").toString()));
    assertEquals(Set.of("Affirmative", "Y"),
        extractor.extractPossibleValues(Set.of(NS + "yes-no"), file.toString()).getValueLabels());
  }

  private Path writeYesNoOntology(String label) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory data = manager.getOWLDataFactory();
    OWLOntology ontology = manager.createOntology();
    OWLClass base = data.getOWLClass(IRI.create(NS + "yes-no"));
    OWLClass value = data.getOWLClass(IRI.create(NS + "yes"));
    manager.addAxiom(ontology, data.getOWLSubClassOfAxiom(value, base));
    manager.addAxiom(ontology, data.getOWLAnnotationAssertionAxiom(data.getRDFSLabel(), value.getIRI(),
        data.getOWLLiteral(label)));
    manager.addAxiom(ontology, data.getOWLAnnotationAssertionAxiom(
        data.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#notation")),
        value.getIRI(), data.getOWLLiteral("Y")));
    manager.addAxiom(ontology, data.getOWLAnnotationAssertionAxiom(
        data.getOWLAnnotationProperty(SKOSVocabulary.RELATEDMATCH.getIRI()), value.getIRI(),
        IRI.create("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C49488")));
    Path path = tempDir.resolve("yes-no.owl");
    manager.saveOntology(ontology, IRI.create(path.toUri()));
    return path;
  }

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
    assertThrows(UnsupportedOperationException.class, () -> extractor.getBaseClassURIs().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> extractor.getSubClassURIs(base.getIRI().toString()).add("https://example.org/injected"));
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
    ValueSetsExtractor extractor = new ValueSetsExtractor();
    extractor.loadValueSetsOntology(ontologyPath.toString());
    return extractor;
  }
}
