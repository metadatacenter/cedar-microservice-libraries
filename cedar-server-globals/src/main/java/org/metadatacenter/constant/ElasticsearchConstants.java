package org.metadatacenter.constant;

import org.metadatacenter.model.ModelNodeNames;

public final class ElasticsearchConstants {


  private ElasticsearchConstants() {
  }

  private static final String ES_INFO_PREFIX = "info.";

  // Searchable fields
  public static final String DOCUMENT_CEDAR_ID = "cid";
  public static final String SUMMARY_TEXT = "summaryText";
  public static final String SUMMARY_RAW_TEXT = "summaryText.raw";
  public static final String POSSIBLE_VALUES = "possibleValues";
  public static final String VALUE_LABELS = POSSIBLE_VALUES + "." + "valueLabels";
  public static final String VALUE_CONCEPTS = POSSIBLE_VALUES + "." + "valueConcepts";
  public static final float POSSIBLE_VALUES_BOOST = 0.01f;
  public static final String USERS = "users";
  public static final String GROUPS = "groups";
  public static final String CATEGORIES = "categories";
  public static final String INFO_SCHEMA_NAME = ES_INFO_PREFIX + ModelNodeNames.SCHEMA_ORG_NAME;
  public static final String RESOURCE_TYPE = ES_INFO_PREFIX + "resourceType";
  public static final String INFO_IS_LATEST_VERSION = ES_INFO_PREFIX + "isLatestVersion";
  public static final String INFO_IS_LATEST_PUBLISHED_VERSION = ES_INFO_PREFIX + "isLatestPublishedVersion";
  public static final String INFO_IS_LATEST_DRAFT_VERSION = ES_INFO_PREFIX + "isLatestDraftVersion";
  public static final String INFO_BIBO_STATUS = ES_INFO_PREFIX + ModelNodeNames.BIBO_STATUS;
  public static final String INFO_PAV_LAST_UPDATED_ON = ES_INFO_PREFIX + ModelNodeNames.PAV_LAST_UPDATED_ON;
  public static final String INFO_PAV_CREATED_ON = ES_INFO_PREFIX + ModelNodeNames.PAV_CREATED_ON;
  public static final String INFO_IS_BASED_ON = ES_INFO_PREFIX + ModelNodeNames.SCHEMA_IS_BASED_ON;
  public static final String COMPUTED_EVERYBODY_PERMISSION = "computedEverybodyPermission";
  public static final String INFO_FIELDS = "infoFields";
  public static final String INFO_FIELDS_FIELD_NAME = INFO_FIELDS + "." + "fieldName";
  public static final String INFO_FIELDS_FIELD_PREFERRED_LABEL = INFO_FIELDS + "." + "fieldPrefLabel";
  public static final String INFO_FIELDS_FIELD_VALUE = INFO_FIELDS + "." + "fieldValue";
  public static final String INFO_FIELDS_FIELD_VALUE_URI = INFO_FIELDS + "." + "fieldValueUri";

  // Sortable fields
  public static final String SORT_BY_NAME = "name";
  public static final String SORT_CREATED_ON_FIELD = "createdOnTS";
  public static final String SORT_LAST_UPDATED_ON_FIELD = "lastUpdatedOnTS";

  // Other
  public static final String ES_SORT_DESC_PREFIX = "-";
  public static final String TEMPLATE_ID = "templateId";
  public static final String ANY_STRING = "_any_";
  public static final String FIELD_NAME_VALUE_SEPARATOR = ":";
  public static final String POSSIBLE_VALUES_PREFIX = "[pv]";
  public static final String POSSIBLE_VALUES_PREFIX_ENCODED = "_pv_";


}
