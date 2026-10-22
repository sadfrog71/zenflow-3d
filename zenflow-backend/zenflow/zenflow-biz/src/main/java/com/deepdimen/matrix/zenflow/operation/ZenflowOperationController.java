package com.deepdimen.matrix.zenflow.operation;

import com.deepdimen.matrix.common.core.util.R;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnExpression("${zenflow.garden.enabled:false} or ${zenflow.media.enabled:false}")
public class ZenflowOperationController {
    private final ZenflowOperationReceipts receipts;
    public ZenflowOperationController(ZenflowOperationReceipts receipts){this.receipts=receipts;}
    @GetMapping("/zenflow/v1/operations/{clientRequestId}")
    public ResponseEntity<R<?>> get(@PathVariable String clientRequestId,@RequestParam String operationType){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(receipts.get(operationType,clientRequestId)));
    }
}
