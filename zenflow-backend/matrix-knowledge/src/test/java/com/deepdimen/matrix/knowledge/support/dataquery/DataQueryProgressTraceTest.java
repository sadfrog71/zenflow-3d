package com.deepdimen.matrix.knowledge.support.dataquery;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DataQueryProgressTraceTest {
 @Test void progressObservesRealStepsAfterPersistenceWithoutReplacingAudit() {
  var trace = new DataQueryExecutionTrace();
  var audits = new ArrayList<DataQueryExecutionTrace.Snapshot>();
  var events = new ArrayList<DataQueryExecutionTrace.Snapshot>();
  trace.attach(audits::add);
  trace.observe(events::add);
  int step = trace.start("JDBC_QUERY", 1, null);
  trace.success(step);
  assertThat(audits).hasSize(3);
  assertThat(events).hasSize(3);
  assertThat(events.get(1).steps().get(0).status()).isEqualTo("RUNNING");
  assertThat(events.get(2).steps().get(0).status()).isEqualTo("SUCCEEDED");
 }
}
