package org.metadatacenter.util;

import org.metadatacenter.model.CedarResourceType;

import java.util.*;

public class CedarResourceTypeUtil {

  private static final List<CedarResourceType> validRestTypesList;
  private static final List<String> validRestTypeValuesList;
  private static final Set<CedarResourceType> validRestTypesSet;
  private static final List<String> validVersioningTypeValuesList;

  static {
    validRestTypesSet = new LinkedHashSet<>();
    validRestTypesSet.add(CedarResourceType.FOLDER);
    validRestTypesSet.add(CedarResourceType.FIELD);
    validRestTypesSet.add(CedarResourceType.ELEMENT);
    validRestTypesSet.add(CedarResourceType.TEMPLATE);
    validRestTypesSet.add(CedarResourceType.INSTANCE);

    validRestTypesList = new ArrayList<>();
    validRestTypeValuesList = new ArrayList<>();
    for (CedarResourceType nt : validRestTypesSet) {
      validRestTypesList.add(nt);
      validRestTypeValuesList.add(nt.getValue());
    }

    validVersioningTypeValuesList = new ArrayList<>();
    validVersioningTypeValuesList.add(CedarResourceType.ELEMENT.getValue());
    validVersioningTypeValuesList.add(CedarResourceType.TEMPLATE.getValue());
  }

  public static List<String> getValidResourceTypeValuesForRestCalls() {
    return validRestTypeValuesList;
  }

  public static List<String> getValidResourceTypeValuesForVersioning() {
    return validVersioningTypeValuesList;
  }

  public static List<CedarResourceType> getValidResourceTypesForRestCalls() {
    return validRestTypesList;
  }

  public static boolean isNotValidForRestCall(CedarResourceType resourceType) {
    return !validRestTypesSet.contains(resourceType);
  }
}
