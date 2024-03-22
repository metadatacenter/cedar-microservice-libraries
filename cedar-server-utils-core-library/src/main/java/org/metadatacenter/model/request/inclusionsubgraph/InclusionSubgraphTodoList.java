package org.metadatacenter.model.request.inclusionsubgraph;

import java.util.ArrayList;
import java.util.List;

public class InclusionSubgraphTodoList {

  private List<InclusionSubgraphTodoElement> todoList = new ArrayList<>();

  public void addTodoElement(InclusionSubgraphTodoElement element) {
    todoList.add(element);
  }

  public List<InclusionSubgraphTodoElement> getTodoList() {
    return todoList;
  }
}

