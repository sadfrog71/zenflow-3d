package com.deepdimen.matrix.knowledge.support.provider;

import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.JsonWithInt;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QdrantDenseCompatibilityClientTest {

	@Test
	void modernDenseRetainsPayloadScoreAndIdentity() {
		var vector = Points.VectorOutput.newBuilder()
			.setDense(Points.DenseVector.newBuilder().addAllData(List.of(.1f, .2f, .3f)))
			.build();
		var point = point(vector);
		var adapted = QdrantDenseCompatibilityClient.legacyDenseOutput(point);
		assertEquals(List.of(.1f, .2f, .3f), adapted.getVectors().getVector().getDataList());
		assertEquals(point.getVectors().getVector().getDense(), adapted.getVectors().getVector().getDense());
		assertEquals(point.getPayloadMap(), adapted.getPayloadMap());
		assertEquals(point.getId(), adapted.getId());
		assertEquals(point.getScore(), adapted.getScore());
	}

	@Test
	void existingLegacyDataIsUnchanged() {
		var point = point(Points.VectorOutput.newBuilder().addAllData(List.of(.2f, .3f)).build());
		assertSame(point, QdrantDenseCompatibilityClient.legacyDenseOutput(point));
	}

	@Test
	void sparseAndAbsentVectorsAreNotReinterpretedAsDense() {
		var sparse = point(Points.VectorOutput.newBuilder()
			.setSparse(Points.SparseVector.newBuilder().addIndices(2).addValues(.4f))
			.build());
		assertSame(sparse, QdrantDenseCompatibilityClient.legacyDenseOutput(sparse));
		var absent = Points.ScoredPoint.getDefaultInstance();
		assertSame(absent, QdrantDenseCompatibilityClient.legacyDenseOutput(absent));
	}

	private static Points.ScoredPoint point(Points.VectorOutput vector) {
		return Points.ScoredPoint.newBuilder()
			.setId(Points.PointId.newBuilder().setNum(123))
			.setScore(.9f)
			.putPayload("companionRevisionId", JsonWithInt.Value.newBuilder().setStringValue("frozen-revision").build())
			.setVectors(Points.VectorsOutput.newBuilder().setVector(vector))
			.build();
	}

}
