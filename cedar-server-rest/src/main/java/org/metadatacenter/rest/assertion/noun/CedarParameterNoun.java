package org.metadatacenter.rest.assertion.noun;

import org.metadatacenter.rest.CedarAssertionNoun;

public abstract class CedarParameterNoun implements CedarParameter, CedarAssertionNoun {
  protected boolean trimmed;

  @Override
  public void trim() {
    trimmed = true;
  }


}
