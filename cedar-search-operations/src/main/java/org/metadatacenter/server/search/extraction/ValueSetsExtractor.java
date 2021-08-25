package org.metadatacenter.server.search.extraction;

import org.metadatacenter.exception.CedarProcessingException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ValueSetsExtractor
{
  public enum Annotation {
    IDENTIFIER, VERSION, NOTATION, RELATED_MATCH, COMMENT, LABEL, START_TIME, END_TIME
  }

  private final String CDE_VALUESETS_ONTOLOGY_ID = "CADSR-VS";
  private final String CDE_VALUESETS_ONTOLOGY_IRI = "https://cadsr.nci.nih.gov/metadata/" + CDE_VALUESETS_ONTOLOGY_ID + "/";

  // Schema.org URIs
  private final String SCHEMAORG_URI = "https://schema.org/";
  private final String SCHEMAORG_STARTTIME_URI = SCHEMAORG_URI + "startTime";
  private final String SCHEMAORG_ENDTIME_URI = SCHEMAORG_URI + "endTime";
  // Dublic Core URIs
  private final String DUBLINCORE_URI = "http://purl.org/dc/terms/";
  private final String DUBLINCORE_IDENTIFIER_URI = DUBLINCORE_URI + "identifier";
  private final String DUBLINCORE_VERSION_URI = DUBLINCORE_URI + "hasVersion";
  // SKOS URIs
  private  final String SKOS_URI = "http://www.w3.org/2004/02/skos/core#";
  private  final String SKOS_NOTATION_URI = SKOS_URI + "notation";

  private IRI IDENTIFIER_IRI = IRI.create(DUBLINCORE_IDENTIFIER_URI);
  private IRI VERSION_IRI = IRI.create(DUBLINCORE_VERSION_URI);
  private IRI NOTATION_IRI = IRI.create(SKOS_NOTATION_URI);
  private IRI RELATED_MATCH_IRI = SKOSVocabulary.RELATEDMATCH.getIRI();
  private IRI COMMENT_IRI = OWLRDFVocabulary.RDFS_COMMENT.getIRI();
  private IRI LABEL_IRI = OWLRDFVocabulary.RDFS_LABEL.getIRI();
  private IRI START_TIME_IRI = IRI.create(SCHEMAORG_STARTTIME_URI);
  private IRI END_TIME_IRI = IRI.create(SCHEMAORG_ENDTIME_URI);

  private final Logger logger = LoggerFactory.getLogger(ValueSetsExtractor.class);

  private Map<String, Set<String>> classHierarchy = new HashMap<>();
  private Map<Annotation, Map<String, String>> annotations = new HashMap<>();

  private static ValueSetsExtractor singleInstance;

  public static ValueSetsExtractor getInstance() {
    if (singleInstance == null) {
      singleInstance = new ValueSetsExtractor();
    }
    return singleInstance;
  }

  private ValueSetsExtractor()
  {
  }

  public synchronized void loadValueSetsOntology(String ontologyFilePath) throws CedarProcessingException
  {
    try {
      OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
      OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyFilePath));

      logger.info("Reading OWL value sets ontology " + ontologyFilePath + " ...");

      classHierarchy = getClassHierarchy(ontology);
      annotations = getAnnotations(ontology);

      logger.info("Finished processing OWL value sets ontology");
    } catch (OWLOntologyCreationException e) {
      throw new CedarProcessingException("Error while reading OWL value sets ontology: " + e);
    }
  }

  public Set<String> getBaseClassURIs()
  {
    return classHierarchy.keySet();
  }

  public Set<String> getSubClassURIs(String superclassURI)
  {
    if (classHierarchy.containsKey(superclassURI))
      return classHierarchy.get(superclassURI);
    else
      return Collections.EMPTY_SET;
  }

  public Optional<String> getAnnotation(String classURI, Annotation annotation)
  {
    if (annotations.containsKey(annotation) && annotations.get(annotation).containsKey(classURI))
      return Optional.of(annotations.get(annotation).get(classURI));
    else
      return Optional.empty();
  }

  private Map<String, Set<String>> getClassHierarchy(OWLOntology ontology)
  {
    Map<String, Set<String>> classHierarchy = new HashMap<>();

    for (OWLSubClassOfAxiom subClassOfAxiom : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
      OWLClassExpression superClassExpression = subClassOfAxiom.getSuperClass();
      OWLClassExpression subClassExpression = subClassOfAxiom.getSubClass();

      if (superClassExpression.isAnonymous()) {
        String message = "Anonymous superclass " + superClassExpression + " found on ontology. Expecting named classes only";
        logger.error(message);
      }

      if (subClassExpression.isAnonymous()) {
        String message = "Anonymous subclass " + subClassExpression + " found on ontology. Expecting named classes only";
        logger.error(message);
      }

      OWLClass superClass = superClassExpression.asOWLClass();
      OWLClass subClass = subClassExpression.asOWLClass();

      IRI superClassIRI = superClass.getIRI();
      IRI subClassIRI = subClass.asOWLClass().getIRI();

      String superClassURI = superClassIRI.toString();
      String subClassURI = subClassIRI.toString();

      if (!superClassIRI.getNamespace().equals(CDE_VALUESETS_ONTOLOGY_IRI)) {
        String message = "Invalid superclass IRI namespace " + superClassIRI.getNamespace() + " found in ontology;" +
          " expecting " + CDE_VALUESETS_ONTOLOGY_IRI;
        logger.error(message);
      }

//      if (!subClassIRI.getNamespace().equals(CDE_VALUESETS_ONTOLOGY_IRI)) {
//        String message = "Invalid subclass IRI namespace " + subClassIRI.getNamespace() + " found in ontology;" +
//          " expecting " + CDE_VALUESETS_ONTOLOGY_IRI;
//        logger.error(message);
//      }

      if (!subClassURI.equals(superClassURI)) {
        if (classHierarchy.containsKey(superClassURI)) {
          classHierarchy.get(superClassURI).add(subClassURI);
        } else {
          Set<String> subClassURIs = new HashSet<>();
          subClassURIs.add(subClassURI);
          classHierarchy.put(superClassURI, subClassURIs);
        }
      }
    }
    return classHierarchy;
  }

  private Map<Annotation, Map<String, String>> getAnnotations(OWLOntology ontology)
  {
    Map<Annotation, Map<String, String>> annotations = new HashMap<>();

    for (Annotation annotation : Annotation.values())
      annotations.put(annotation, new HashMap<>());

    for (OWLDeclarationAxiom declarationAxiom : ontology.getAxioms(AxiomType.DECLARATION)) {
      if (declarationAxiom.getEntity().isType(EntityType.CLASS)) {
        IRI classIRI = declarationAxiom.getEntity().getIRI();
        String classURI = classIRI.toString();

        for (OWLAnnotationAssertionAxiom annotation : ontology.getAnnotationAssertionAxioms(classIRI)) {
          IRI annotationPropertyIRI = annotation.getProperty().getIRI();
          OWLAnnotationValue annotationValue = annotation.annotationValue();

          if (annotationPropertyIRI.equals(IDENTIFIER_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.IDENTIFIER).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for identifier annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(VERSION_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.VERSION).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for version annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(NOTATION_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.NOTATION).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for notation annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(RELATED_MATCH_IRI)) {
            if (annotationValue.isIRI())
              annotations.get(Annotation.RELATED_MATCH).put(classURI, annotationValue.toString());
            else
              logger.error("Expecting IRI value for related match annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(COMMENT_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.COMMENT).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for comment annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(LABEL_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.LABEL).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for label annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(START_TIME_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.START_TIME).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for start time annotation for class " + classIRI);
          } else if (annotationPropertyIRI.equals(END_TIME_IRI)) {
            if (annotationValue.isLiteral())
              annotations.get(Annotation.END_TIME).put(classURI, annotationValue.asLiteral().get().getLiteral());
            else
              logger.error("Expecting literal value for end time annotation for class " + classIRI);
          }
        }
      }
    }
    return annotations;
  }
}
