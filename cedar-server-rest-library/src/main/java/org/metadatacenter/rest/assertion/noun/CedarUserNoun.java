package org.metadatacenter.rest.assertion.noun;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.server.security.model.user.CedarUser;

public record CedarUserNoun(CedarUser user) implements CedarAssertionUser, CedarAssertionNoun {

}
