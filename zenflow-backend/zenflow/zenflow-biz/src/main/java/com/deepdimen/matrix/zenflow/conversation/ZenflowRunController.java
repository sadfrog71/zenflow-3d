package com.deepdimen.matrix.zenflow.conversation;

import com.deepdimen.matrix.assistant.run.*;
import com.deepdimen.matrix.common.core.util.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/zenflow/v1/runs")
public class ZenflowRunController {
    private final ZenflowConversationFacade conversations;
    private final AssistantRunEventStreamService streams;
    private final ZenflowCitations citations;
    public ZenflowRunController(ZenflowConversationFacade conversations,AssistantRunEventService events,AssistantRunEventBus bus,AssistantRunRepository repository,ZenflowCitations citations) {
        this.conversations=conversations;this.citations=citations;this.streams=new AssistantRunEventStreamService(events,bus,repository);
    }
    @GetMapping("/{id}") public R<?> snapshot(@PathVariable String id){return R.ok(conversations.snapshot(id));}
    @GetMapping("/{id}/citations") public R<?> citations(@PathVariable String id){return R.ok(citations.list(id));}
    @GetMapping("/{id}/citations/{key}") public R<?> citation(@PathVariable String id,@PathVariable String key){return R.ok(citations.detail(id,key));}
    @PostMapping("/{id}/cancel") public R<?> cancel(@PathVariable String id,@RequestHeader("Idempotency-Key") String key){return R.ok(conversations.cancel(id,key));}
    @GetMapping(value="/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id,@RequestParam(defaultValue="0") long afterSeq,@RequestHeader(value="Last-Event-ID",required=false) String last) {
        long resume=afterSeq;
        try{if(last!=null)resume=Math.max(resume,Long.parseLong(last));}catch(NumberFormatException invalid){throw ZenflowConversationFacade.error(HttpStatus.BAD_REQUEST,"INVALID_EVENT_SEQUENCE");}
        if(afterSeq<0 || resume<0)throw ZenflowConversationFacade.error(HttpStatus.BAD_REQUEST,"INVALID_EVENT_SEQUENCE");
        return streams.stream(id,resume,conversations.streamGuard(id));
    }
}
