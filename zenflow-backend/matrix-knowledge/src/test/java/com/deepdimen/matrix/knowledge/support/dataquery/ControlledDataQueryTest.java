package com.deepdimen.matrix.knowledge.support.dataquery;

import org.junit.jupiter.api.Test;

/** Exposes local behavioral suites to the repository's JUnit/Surefire runner. */
class ControlledDataQueryTest {

	@Test
	void compilerChecks() {
		PatrolQueryCompilerChecks.main(new String[0]);
	}

	@Test
	void serviceAndJdbcChecks() throws Exception {
		ControlledDataQueryChecks.main(new String[0]);
	}

}
