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

@Deprecated
public class ValidTemplateAssertion implements CedarAssertion {

  ValidTemplateAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (!(target instanceof CedarRequestNoun)) {
      return new CedarAssertionResult("Only instances of CedarRequestNoun can be checked with this assertion");
    }
    String payload = null;
    try {
      payload = requestContext.request().getRequestBody().asJsonString();
      ValidationReport validationReport = validateTemplatePayload(payload);
      String validationStatus = validationReport.getValidationStatus();
      if (CedarValidationReport.IS_VALID.equals(validationStatus)) {
        return null;
      }
      return new CedarAssertionResult("The uploaded template is invalid")
          .badRequest()
          .errorKey(CedarErrorKey.CONTENT_NOT_VALID)
          .errorType(CedarErrorType.VALIDATION_ERROR)
          .entity("warning", validationReport.getWarnings())
          .entity("error", validationReport.getErrors());
    } catch (CedarBadRequestException | CedarProcessingException e) {
      return new CedarAssertionResult("Unexpected error when validating the template")
          .badRequest()
          .message(e.getMessage());
    }
  }

  private static ValidationReport validateTemplatePayload(String payload) throws CedarProcessingException {
    try {
      JsonNode templateObject = JsonMapper.MAPPER.readTree(payload);
      return new CedarValidator().validateTemplate(templateObject);
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    return new CedarAssertionResult("Not implemented for Objects");
  }
}
