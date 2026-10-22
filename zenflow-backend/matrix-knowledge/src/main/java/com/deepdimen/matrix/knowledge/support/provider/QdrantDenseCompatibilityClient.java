package com.deepdimen.matrix.knowledge.support.provider;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points;
import java.util.List;

/** Bridges modern dense output to the field read by the existing LangChain4j adapter. */
public class QdrantDenseCompatibilityClient extends QdrantClient {

	public QdrantDenseCompatibilityClient(QdrantGrpcClient client) {
		super(client);
	}

	@Override
	public ListenableFuture<List<Points.ScoredPoint>> searchAsync(Points.SearchPoints request) {
		return Futures.transform(super.searchAsync(request),
				points -> points.stream().map(QdrantDenseCompatibilityClient::legacyDenseOutput).toList(),
				MoreExecutors.directExecutor());
	}

	static Points.ScoredPoint legacyDenseOutput(Points.ScoredPoint point) {
		var vector = point.getVectors().getVector();
		if (vector.getDataCount() != 0 || !vector.hasDense())
			return point;
		var normalized = vector.toBuilder().addAllData(vector.getDense().getDataList()).build();
		return point.toBuilder().setVectors(point.getVectors().toBuilder().setVector(normalized)).build();
	}

}
