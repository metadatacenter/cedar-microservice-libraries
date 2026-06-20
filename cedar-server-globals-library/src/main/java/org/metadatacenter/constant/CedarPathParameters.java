package org.metadatacenter.constant;

public final class CedarPathParameters {

  private CedarPathParameters() {
  }

  public static final String PP_ID = "id";
  public static final String PP_SERVER = "server";
  public static final String PP_INPUT = "input";

  // Descriptive per-artifact path-parameter names. Used in place of the generic PP_ID on the
  // resource endpoints so the generated Swagger spec documents which identifier each path expects.
  // These only rename the JAX-RS URI-template variable (a label); the actual request URLs are unchanged.
  public static final String PP_TEMPLATE_ID = "template_id";
  public static final String PP_TEMPLATE_FIELD_ID = "template_field_id";
  public static final String PP_TEMPLATE_ELEMENT_ID = "template_element_id";
  public static final String PP_TEMPLATE_INSTANCE_ID = "template_instance_id";
  public static final String PP_FOLDER_ID = "folder_id";
  public static final String PP_CATEGORY_ID = "category_id";

}
