package com.deepdimen.matrix.zenflow.capsule;

import com.deepdimen.matrix.common.core.util.R;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow/v1/memories")
public class ZenflowMemoryController {
    private final ZenflowMemoryFacade memories;
    public ZenflowMemoryController(ZenflowMemoryFacade memories){this.memories=memories;}
    @GetMapping public R<?> list(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit){return R.ok(memories.list(cursor,limit));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public R<?> create(@RequestBody Map<String,Object> body,@RequestHeader("Idempotency-Key") String key){return R.ok(memories.create(body,key));}
    @PatchMapping("/{id}") public R<?> patch(@PathVariable String id,@RequestBody Map<String,Object> body){return R.ok(memories.patch(id,body));}
    @DeleteMapping("/{id}") public R<?> delete(@PathVariable String id,@RequestParam int expectedVersion,@RequestHeader("Idempotency-Key") String key){return R.ok(memories.delete(id,expectedVersion,key));}
}
