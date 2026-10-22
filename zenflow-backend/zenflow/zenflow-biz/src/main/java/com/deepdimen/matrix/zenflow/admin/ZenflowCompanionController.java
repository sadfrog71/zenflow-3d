package com.deepdimen.matrix.zenflow.admin;
import com.deepdimen.matrix.zenflow.companion.ZenflowCompanionService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/zenflow-admin/v1/companion")
public class ZenflowCompanionController {
    private final ZenflowCompanionService service;
    public ZenflowCompanionController(ZenflowCompanionService service){this.service=service;}
    @GetMapping public Map<String,Object> detail(){return service.detail();}
    @PostMapping("/drafts") public Map<String,Object> draft(@RequestBody Map<String,Object> body){return service.createDraft(body);}
    @PutMapping("/revisions/{id}") public Map<String,Object> save(@PathVariable String id,@RequestBody Map<String,Object> body){return service.save(id,body);}
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    @PostMapping("/revisions/{id}/prepare") public Map<String,Object> prepare(@PathVariable String id,@RequestBody Map<String,Object> body){return service.prepare(id,body);}
    @PostMapping("/revisions/{id}/publish") public Map<String,Object> publish(@PathVariable String id,@RequestBody Map<String,Object> body){return service.publish(id,body);}
    @PostMapping("/revisions/{id}/rollback") public Map<String,Object> rollback(@PathVariable String id,@RequestBody Map<String,Object> body){return service.publish(id,body);}
    @GetMapping("/revisions/{id}/preview") public Map<String,Object> preview(@PathVariable String id){return service.preview(id);}
    @PostMapping("/disable") public Map<String,Object> disable(@RequestBody Map<String,Object> body){return service.disable(body);}
}
