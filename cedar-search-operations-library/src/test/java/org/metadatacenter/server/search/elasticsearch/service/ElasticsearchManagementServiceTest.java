package org.metadatacenter.server.search.elasticsearch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchManagementServiceTest {

  private RestHighLevelClient client;
  private IndicesClient indicesClient;
  private ElasticsearchManagementService managementService;

  @BeforeEach
  void setUp() {
    OpensearchConfig opensearchConfig = mock(OpensearchConfig.class);
    when(opensearchConfig.getClusterName()).thenReturn("test-cluster");
    client = mock(RestHighLevelClient.class);
    indicesClient = mock(IndicesClient.class);
    when(client.indices()).thenReturn(indicesClient);

    managementService = spy(new ElasticsearchManagementService(
        opensearchConfig, mock(CedarConfig.class, RETURNS_DEEP_STUBS)));
    doReturn(client).when(managementService).getClient();
  }

  @Test
  void aliasReplacementUsesOneAtomicRemoveAndAddRequest() throws Exception {
    when(indicesClient.updateAliases(any(), eq(RequestOptions.DEFAULT)))
        .thenReturn(new AcknowledgedResponse(true));
    ArgumentCaptor<IndicesAliasesRequest> requestCaptor = ArgumentCaptor.forClass(IndicesAliasesRequest.class);

    managementService.replaceAlias("cedar-search-new", "cedar-search");

    verify(indicesClient).updateAliases(requestCaptor.capture(), eq(RequestOptions.DEFAULT));
    List<IndicesAliasesRequest.AliasActions> actions = requestCaptor.getValue().getAliasActions();
    assertEquals(2, actions.size());
    assertEquals(IndicesAliasesRequest.AliasActions.Type.REMOVE, actions.get(0).actionType());
    assertArrayEquals(new String[]{"*"}, actions.get(0).indices());
    assertArrayEquals(new String[]{"cedar-search"}, actions.get(0).aliases());
    assertFalse(actions.get(0).mustExist());
    assertEquals(IndicesAliasesRequest.AliasActions.Type.ADD, actions.get(1).actionType());
    assertArrayEquals(new String[]{"cedar-search-new"}, actions.get(1).indices());
    assertArrayEquals(new String[]{"cedar-search"}, actions.get(1).aliases());
  }

  @Test
  void refreshAndCountTargetOnlyTheConcreteIndex() throws Exception {
    RefreshResponse refreshResponse = mock(RefreshResponse.class);
    when(refreshResponse.getFailedShards()).thenReturn(0);
    when(indicesClient.refresh(any(), eq(RequestOptions.DEFAULT))).thenReturn(refreshResponse);
    CountResponse countResponse = new CountResponse(42L, false,
        new CountResponse.ShardStats(1, 1, 0, new ShardSearchFailure[0]));
    when(client.count(any(), eq(RequestOptions.DEFAULT))).thenReturn(countResponse);
    ArgumentCaptor<RefreshRequest> refreshCaptor = ArgumentCaptor.forClass(RefreshRequest.class);
    ArgumentCaptor<CountRequest> countCaptor = ArgumentCaptor.forClass(CountRequest.class);

    managementService.refreshIndex("cedar-search-new");
    long count = managementService.countDocuments("cedar-search-new");

    verify(indicesClient).refresh(refreshCaptor.capture(), eq(RequestOptions.DEFAULT));
    verify(client).count(countCaptor.capture(), eq(RequestOptions.DEFAULT));
    assertArrayEquals(new String[]{"cedar-search-new"}, refreshCaptor.getValue().indices());
    assertArrayEquals(new String[]{"cedar-search-new"}, countCaptor.getValue().indices());
    assertEquals(42L, count);
  }

  @Test
  void unacknowledgedAliasReplacementFailsClosed() throws Exception {
    when(indicesClient.updateAliases(any(), eq(RequestOptions.DEFAULT)))
        .thenReturn(new AcknowledgedResponse(false));

    assertThrows(CedarProcessingException.class,
        () -> managementService.replaceAlias("cedar-search-new", "cedar-search"));
  }

  @Test
  void partialRefreshFailureFailsClosed() throws Exception {
    RefreshResponse refreshResponse = mock(RefreshResponse.class);
    when(refreshResponse.getFailedShards()).thenReturn(1);
    when(indicesClient.refresh(any(), eq(RequestOptions.DEFAULT))).thenReturn(refreshResponse);

    assertThrows(CedarProcessingException.class,
        () -> managementService.refreshIndex("cedar-search-new"));
  }

  @Test
  void partialCountFailureFailsClosed() throws Exception {
    CountResponse countResponse = mock(CountResponse.class);
    when(countResponse.getFailedShards()).thenReturn(1);
    when(client.count(any(), eq(RequestOptions.DEFAULT))).thenReturn(countResponse);

    assertThrows(CedarProcessingException.class,
        () -> managementService.countDocuments("cedar-search-new"));
  }
}
