package com.deepdimen.matrix.zenflow.conversation;

import com.deepdimen.matrix.common.core.util.R;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow/v1/conversations")
public class ZenflowConversationController {
    private final ZenflowConversationFacade conversations;
    private final ZenflowCitations citations;
    public ZenflowConversationController(ZenflowConversationFacade conversations,ZenflowCitations citations){this.conversations=conversations;this.citations=citations;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public R<?> create(@RequestBody Map<String,Object> body){return R.ok(conversations.create(body));}
    @GetMapping public R<?> list(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String status){return R.ok(conversations.list(cursor,limit,status));}
    @GetMapping("/{id}") public R<?> detail(@PathVariable String id){return R.ok(conversations.detail(id));}
    @PatchMapping("/{id}") public R<?> patch(@PathVariable String id,@RequestBody Map<String,Object> body){return R.ok(conversations.patch(id,body));}
    @PostMapping("/{id}/messages") @ResponseStatus(HttpStatus.ACCEPTED) public R<?> send(@PathVariable String id,@RequestBody Map<String,Object> body){return R.ok(conversations.send(id,body));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.ACCEPTED) public R<?> delete(@PathVariable String id,@RequestParam long expectedVersion,@RequestHeader("Idempotency-Key") String key){return R.ok(conversations.delete(id,expectedVersion,key));}
    @GetMapping("/{id}/messages/{messageId}/citations") public R<?> citations(@PathVariable String id,@PathVariable String messageId){return R.ok(citations.list(conversations.responseRun(id,messageId)));}
    @GetMapping("/{id}/messages") public R<?> messages(@PathVariable String id,@RequestParam(required=false) Long beforeSeq,@RequestParam(defaultValue="20") int limit){return R.ok(conversations.messages(id,beforeSeq,limit));}
}
