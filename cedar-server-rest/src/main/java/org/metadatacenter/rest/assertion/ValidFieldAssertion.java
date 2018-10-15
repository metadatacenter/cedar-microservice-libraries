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

public class ValidFieldAssertion implements CedarAssertion {

  ValidFieldAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (!(target instanceof CedarRequestNoun)) {
      return new CedarAssertionResult("Only instances of CedarRequestNoun can be checked with this assertion");
    }
    String payload = null;
    try {
      payload = requestContext.request().getRequestBody().asJsonString();
      ValidationReport validationReport = validateFieldPayload(payload);
      String validationStatus = validationReport.getValidationStatus();
      if (CedarValidationReport.IS_VALID.equals(validationStatus)) {
        return null;
      }
      return new CedarAssertionResult("The uploaded template field is invalid")
          .badRequest()
          .errorKey(CedarErrorKey.CONTENT_NOT_VALID)
          .errorType(CedarErrorType.VALIDATION_ERROR)
          .entity("warning", validationReport.getWarnings())
          .entity("error", validationReport.getErrors());
    } catch (CedarBadRequestException | CedarProcessingException e) {
      return new CedarAssertionResult("Unexpected error when validating the template field")
          .badRequest()
          .message(e.getMessage());
    }
  }

  private static ValidationReport validateFieldPayload(String payload) throws CedarProcessingException {
    try {
      JsonNode fieldObject = JsonMapper.MAPPER.readTree(payload);
      return new CedarValidator().validateTemplateField(fieldObject);
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    return new CedarAssertionResult("Not implemented for Objects");
  }
}
