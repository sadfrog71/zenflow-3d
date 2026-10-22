package com.deepdimen.matrix.zenflow.garden;

import com.deepdimen.matrix.common.core.util.R;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow/v1")
@ConditionalOnProperty(prefix="zenflow.garden",name="enabled",havingValue="true")
public class ZenflowGardenController {
    private final ZenflowGardenFacade garden;
    public ZenflowGardenController(ZenflowGardenFacade garden){this.garden=garden;}
    @GetMapping("/garden/state") public ResponseEntity<R<?>> get(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(garden.get()));}
    @PatchMapping("/garden/state") public ResponseEntity<R<?>> patch(@RequestBody JsonNode body){
        var receipt=garden.patch(body);
        HttpStatus status="SUCCEEDED".equals(receipt.state())?HttpStatus.OK:
            "GARDEN_VERSION_CONFLICT".equals(receipt.safeErrorCode())?HttpStatus.CONFLICT:HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(R.ok(receipt));
    }
}
