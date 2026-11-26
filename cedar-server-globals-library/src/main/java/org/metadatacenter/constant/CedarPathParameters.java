package org.metadatacenter.constant;

public final class CedarPathParameters {

  private CedarPathParameters() {
  }

  // The identifier of the resource a path ends at.
  public static final String PP_ID = "id";
  public static final String PP_KIND = "kind";
  public static final String PP_ACRONYM = "acronym";
  public static final String PP_SERVER = "server";
  public static final String PP_INPUT = "input";

  // The identifier of a parent the path passes through, as in /users/{user_id}/api-keys/{id}.
  public static final String PP_USER_ID = "user_id";

  public static final String PP_JOB_ID = "jobId";
  public static final String PP_AUTHORITY = "authority";
  public static final String PP_ONTOLOGY = "ontology";
  public static final String PP_VS = "vs";
  public static final String PP_VS_COLLECTION = "vs_collection";
  public static final String PP_COLUMN = "column";

}
