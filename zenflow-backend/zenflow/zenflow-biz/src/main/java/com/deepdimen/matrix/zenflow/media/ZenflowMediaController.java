package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.common.core.util.R;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/zenflow/v1/media")
@ConditionalOnProperty(prefix="zenflow.media",name="enabled",havingValue="true")
public class ZenflowMediaController {
    private final ZenflowMediaUploads uploads;private final ZenflowMediaTransfers transfers;private final ZenflowMediaCompletion completion;
    private final ZenflowMediaQueries queries;private final ZenflowNoteMedia notes;private final ZenflowMediaObjects objects;
    public ZenflowMediaController(ZenflowMediaUploads uploads,ZenflowMediaTransfers transfers,ZenflowMediaCompletion completion,ZenflowMediaQueries queries,ZenflowNoteMedia notes,ZenflowMediaObjects objects){this.uploads=uploads;this.transfers=transfers;this.completion=completion;this.queries=queries;this.notes=notes;this.objects=objects;}
    @PostMapping("/uploads") public ResponseEntity<R<?>> create(@RequestBody JsonNode body,@RequestHeader(value="Idempotency-Key",required=false) String key){
        matchingKey(body,key);return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(R.ok(uploads.create(body)));
    }
    @PutMapping(value="/uploads/{id}/content",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<R<?>> receive(@PathVariable String id,@RequestHeader("Upload-Version") int version,HttpServletRequest request)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(transfers.receive(id,version,request.getInputStream())));
    }
    @PostMapping("/uploads/{id}/complete") public ResponseEntity<R<?>> complete(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="Idempotency-Key",required=false) String key)throws Exception{
        matchingKey(body,key);
        body.fieldNames().forEachRemaining(name->{if(!Set.of("clientRequestId","expectedVersion").contains(name))throw invalid("INVALID_MEDIA_COMPLETION");});
        if(!body.path("expectedVersion").isIntegralNumber()||!body.path("expectedVersion").canConvertToInt()||!body.path("clientRequestId").isTextual())throw invalid("INVALID_MEDIA_COMPLETION");
        var receipt=completion.complete(id,body.get("expectedVersion").intValue(),body.get("clientRequestId").textValue());
        return ResponseEntity.status("PROCESSING".equals(receipt.state())?202:"SUCCEEDED".equals(receipt.state())?200:422).cacheControl(CacheControl.noStore()).body(R.ok(receipt));
    }
    @GetMapping("/{id}/status") public ResponseEntity<R<?>> status(@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.ok(queries.status(id)));}
    @GetMapping("/{id}/content") public void content(@PathVariable String id,@RequestHeader(value="Range",required=false) String header,HttpServletResponse response)throws IOException{
        var pointer=notes.pointer(id);ZenflowMediaRange range;
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        try{range=ZenflowMediaRange.parse(header,pointer.bytes());}catch(IllegalArgumentException invalid){response.setStatus(416);response.setHeader("Content-Range","bytes */"+pointer.bytes());response.setContentLengthLong(0);return;}
        response.setStatus(range.partial()?206:200);response.setContentType(pointer.mime());response.setContentLengthLong(range.length());response.setHeader("Accept-Ranges","bytes");if(range.partial())response.setHeader("Content-Range",range.contentRange());
        // Synchronous bounded stream keeps the verified request identity available for the second guard.
        objects.stream(pointer,header,mediaId->notes.pointer(mediaId.toString()),response.getOutputStream());
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<R<?>> failed(ResponseStatusException error){
        String code=error.getReason();if(code==null||!code.matches("[A-Z][A-Z0-9_]{1,63}"))code="MEDIA_REQUEST_FAILED";
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore()).body(R.failed(Map.of("errorCode",code)));
    }
    @ExceptionHandler(IOException.class) public ResponseEntity<R<?>> ioFailure(IOException error){
        String code=error.getMessage();int status=503;
        if("MEDIA_SIZE_EXCEEDED".equals(code))status=413;
        else if(Set.of("MEDIA_HASH_MISMATCH","MEDIA_SIZE_MISMATCH","INVALID_AUDIO_CONTENT").contains(code==null?"":code))status=422;
        else code="MEDIA_TEMPORARILY_UNAVAILABLE";
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(R.failed(Map.of("errorCode",code)));
    }
    private static void matchingKey(JsonNode body,String header){if(body==null||!body.isObject()||header!=null&&!header.equals(body.path("clientRequestId").asText()))throw invalid("IDEMPOTENCY_KEY_MISMATCH");}
    private static ResponseStatusException invalid(String code){return new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,code);}
}
