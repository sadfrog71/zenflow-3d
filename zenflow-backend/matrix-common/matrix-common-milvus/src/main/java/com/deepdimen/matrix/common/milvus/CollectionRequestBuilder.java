package com.deepdimen.matrix.common.milvus;

import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.param.dml.InsertParam;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;

import java.util.List;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

class CollectionRequestBuilder {

    static FlushReq buildFlushRequest(String collectionName) {
        return FlushReq.builder().collectionNames(singletonList(collectionName)).build();
    }

    static HasCollectionReq buildHasCollectionRequest(String collectionName) {
        return HasCollectionReq.builder().collectionName(collectionName).build();
    }

    static DropCollectionReq buildDropCollectionRequest(String collectionName) {
        return DropCollectionReq.builder().collectionName(collectionName).build();
    }

    static InsertReq buildInsertRequest(String collectionName, List<InsertParam.Field> fields) {
        return InsertReq.builder()
                .collectionName(collectionName)
                // .data(fields)
                .build();
    }

    static LoadCollectionReq buildLoadCollectionInMemoryRequest(String collectionName) {
        return LoadCollectionReq.builder().collectionName(collectionName).build();
    }

    static SearchReq buildSearchRequest(String collectionName, FieldDefinition fieldDefinition, List<Float> vectorList,
                                        Filter filter, int maxResults, IndexParam.MetricType metricType, ConsistencyLevel consistencyLevel) {
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(collectionName)
                .topK(maxResults)
                .metricType(metricType)
                .data(singletonList(new FloatVec(vectorList)))
                .annsField(fieldDefinition.getVectorFieldName())
                .outputFields(List.of(fieldDefinition.getVectorFieldName(), fieldDefinition.getMetadataFieldName(), fieldDefinition.getTextFieldName()))
                .consistencyLevel(consistencyLevel);

        if (filter != null) {
            builder.filter(MilvusMetadataFilterMapper.map(filter, fieldDefinition.getMetadataFieldName()));
        }

        return builder.build();
    }

    static QueryReq buildQueryRequest(String collectionName, FieldDefinition fieldDefinition, List<String> rowIds,
                                      ConsistencyLevel consistencyLevel) {
        return QueryReq.builder()
                .collectionName(collectionName)
                .filter(buildQueryExpression(rowIds, fieldDefinition.getIdFieldName()))
                .consistencyLevel(consistencyLevel)
                .outputFields(singletonList(fieldDefinition.getVectorFieldName()))
                .build();
    }

    static DeleteReq buildDeleteRequest(String collectionName, String expr) {
        return DeleteReq.builder().collectionName(collectionName).filter(expr).build();
    }

    private static String buildQueryExpression(List<String> rowIds, String idFieldName) {
        return rowIds.stream().map(id -> format("%s == '%s'", idFieldName, id)).collect(joining(" || "));
    }

}
