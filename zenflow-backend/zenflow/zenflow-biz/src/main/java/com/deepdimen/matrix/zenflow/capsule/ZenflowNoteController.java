package com.deepdimen.matrix.zenflow.capsule;

import com.deepdimen.matrix.common.core.util.R;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow/v1/notes")
public class ZenflowNoteController {
    private final ZenflowCapsuleFacade notes;
    public ZenflowNoteController(ZenflowCapsuleFacade notes){this.notes=notes;}
    @GetMapping public R<?> list(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String kind,@RequestParam(required=false) String status,@RequestParam(required=false) Boolean favorite,@RequestParam(required=false) String keyword){return R.ok(notes.list(cursor,limit,kind,status,favorite,keyword));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public R<?> create(@RequestBody Map<String,Object> body){return R.ok(notes.create(body));}
    @GetMapping("/{id}") public R<?> detail(@PathVariable String id){return R.ok(notes.detail(id));}
    @PatchMapping("/{id}") public R<?> patch(@PathVariable String id,@RequestBody Map<String,Object> body){return R.ok(notes.patch(id,body));}
    @PatchMapping("/{id}/state") public R<?> state(@PathVariable String id,@RequestBody Map<String,Object> body){return R.ok(notes.state(id,body));}
    @GetMapping("/{id}/versions") public R<?> versions(@PathVariable String id,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit){return R.ok(notes.versions(id,cursor,limit));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.ACCEPTED) public R<?> delete(@PathVariable String id,@RequestParam int expectedVersion,@RequestHeader("Idempotency-Key") String key){return R.ok(notes.delete(id,expectedVersion,key));}
    @GetMapping("/{id}/consents") public R<?> consents(@PathVariable String id){return R.ok(notes.consents(id));}
    @PostMapping("/{id}/consents") public R<?> grant(@PathVariable String id,@RequestBody Map<String,Object> body){return R.ok(notes.grant(id,body));}
    @DeleteMapping("/{id}/consents/{consentId}") public R<?> revoke(@PathVariable String id,@PathVariable String consentId,@RequestParam int expectedConsentVersion,@RequestHeader("Idempotency-Key") String key){return R.ok(notes.revoke(id,consentId,expectedConsentVersion,key));}
}
