package org.metadatacenter.rest.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.exception.CedarBadRequestException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.validation.CedarValidator;
import org.metadatacenter.model.validation.report.CedarValidationReport;
import org.metadatacenter.model.validation.report.ValidationReport;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarRequestNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

public class ValidInstanceAssertion implements CedarAssertion {

  private final String templateString;

  public ValidInstanceAssertion(String templateString) {
    this.templateString = templateString;
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (!(target instanceof CedarRequestNoun)) {
      return new CedarAssertionResult("Only instances of CedarRequestNoun can be checked with this assertion");
    }
    String payload;
    try {
      payload = requestContext.request().getRequestBody().asJsonString();
      ValidationReport validationReport = validateInstancePayload(payload);
      String validationStatus = validationReport.getValidationStatus();
      if (CedarValidationReport.IS_VALID.equals(validationStatus)) {
        return null;
      }
      return new CedarAssertionResult("The uploaded metadata instance is invalid")
          .badRequest()
          .errorKey(CedarErrorKey.CONTENT_NOT_VALID)
          .errorType(CedarErrorType.VALIDATION_ERROR)
          .entity("warning", validationReport.getWarnings())
          .entity("error", validationReport.getErrors());
    } catch (CedarBadRequestException | CedarProcessingException e) {
      return new CedarAssertionResult("Unexpected error when validating the metadata instance")
          .badRequest()
          .message(e.getMessage());
    }
  }

  private ValidationReport validateInstancePayload(String payload) throws CedarProcessingException {
    try {
      JsonNode metadata = JsonMapper.MAPPER.readTree(payload);
      JsonNode template = JsonMapper.MAPPER.readTree(templateString);
      return new CedarValidator().validateTemplateInstance(metadata, template);
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    return new CedarAssertionResult("Not implemented for Objects");
  }
}
