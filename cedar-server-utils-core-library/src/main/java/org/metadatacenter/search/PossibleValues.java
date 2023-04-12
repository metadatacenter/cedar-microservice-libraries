package org.metadatacenter.search;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class PossibleValues {

  private Set<String> valueLabels;
  private Set<String> valueConcepts;

  public PossibleValues() {
    this.valueLabels = new HashSet<>();
    this.valueConcepts = new HashSet<>();
  }

  public PossibleValues(Set<String> valueLabels, Set<String> valueConcepts) {
    this.valueLabels = new HashSet<>(valueLabels);
    this.valueConcepts = new HashSet<>(valueConcepts);
  }

  public Set<String> getValueLabels()
  {
    return valueLabels;
  }

  public void setValueLabels(Set<String> valueLabels)
  {
    this.valueLabels = valueLabels;
  }

  public Set<String> getValueConcepts()
  {
    return valueConcepts;
  }

  public void setValueConcepts(Set<String> valueConcepts)
  {
    this.valueConcepts = valueConcepts;
  }

  @Override public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    PossibleValues that = (PossibleValues)o;
    return getValueLabels().equals(that.getValueLabels()) && getValueConcepts().equals(that.getValueConcepts());
  }

  @Override public int hashCode()
  {
    return Objects.hash(getValueLabels(), getValueConcepts());
  }
}
