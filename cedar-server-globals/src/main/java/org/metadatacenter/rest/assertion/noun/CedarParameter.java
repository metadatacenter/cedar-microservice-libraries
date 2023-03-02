package org.metadatacenter.rest.assertion.noun;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.context.CedarParameterSource;

public interface CedarParameter extends CedarAssertionNoun {

  String stringValue();

  boolean booleanValue();

  String getName();

  CedarParameterSource getSource();

  boolean isNull();

  boolean isPresentAndNull();

  boolean isMissing();

  boolean isEmpty();

  void trim();
}
