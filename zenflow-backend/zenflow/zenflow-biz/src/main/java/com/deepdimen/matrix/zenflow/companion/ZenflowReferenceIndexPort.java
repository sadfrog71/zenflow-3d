package com.deepdimen.matrix.zenflow.companion;

/** Fixed public snapshot indexing. Implementations must heartbeat between bounded external batches. */
public interface ZenflowReferenceIndexPort {
    void prepare(String revisionId,ZenflowReferenceSnapshots.Frozen references,Runnable heartbeat);
}
