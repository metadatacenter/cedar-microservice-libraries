package org.metadatacenter.model.request;

import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The format an instance is served in, from the {@code format} query parameter the artifact and
 * resource servers accept. No format means JSON-LD, and a format nobody serves is the caller's
 * mistake, so it is a 400 naming the formats there are rather than a 500.
 */
class OutputFormatTypeDetectorTest {

  @Test
  void noFormatMeansJsonLdAndEachServedFormatIsRecognized() throws Exception {
    assertSame(OutputFormatType.JSONLD, OutputFormatTypeDetector.detectFormat(Optional.empty()));
    assertSame(OutputFormatType.JSONLD, OutputFormatTypeDetector.detectFormat(Optional.of("")));
    assertSame(OutputFormatType.JSONLD, OutputFormatTypeDetector.detectFormat(null));
    assertSame(OutputFormatType.JSON, OutputFormatTypeDetector.detectFormat(Optional.of("json")));
    assertSame(OutputFormatType.RDF_NQUAD, OutputFormatTypeDetector.detectFormat(Optional.of("rdf-nquad")));
  }

  @Test
  void anUnservedFormatIsTheCallersMistake() {
    CedarException refused = assertThrows(CedarException.class,
        () -> OutputFormatTypeDetector.detectFormat(Optional.of("xml")));

    assertSame(CedarErrorKey.UNKNOWN_INSTANCE_OUTPUT_FORMAT, refused.getErrorPack().getErrorKey());
    assertSame(CedarResponseStatus.BAD_REQUEST, refused.getErrorPack().getStatus());
  }
}
