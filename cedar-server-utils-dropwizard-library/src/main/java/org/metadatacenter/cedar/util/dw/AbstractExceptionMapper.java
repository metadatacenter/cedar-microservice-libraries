package org.metadatacenter.cedar.util.dw;

import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.constant.CedarQueryParameters;
import org.metadatacenter.error.CedarErrorPack;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;

public abstract class AbstractExceptionMapper {

  protected
  @Context
  HttpServletRequest request;

  protected boolean hideExceptionConditionally(CedarErrorPack errorPack) {
    if ("false".equals(request.getParameter(CedarQueryParameters.QP_DEBUG)) ||
        "false".equals(request.getHeader(CedarHeaderParameters.DEBUG))) {
      errorPack.resetSourceException();
      return true;
    } else {
      return false;
    }
  }

}
