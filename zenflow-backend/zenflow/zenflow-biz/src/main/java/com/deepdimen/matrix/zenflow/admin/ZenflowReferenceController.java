package com.deepdimen.matrix.zenflow.admin;
import com.deepdimen.matrix.zenflow.companion.ZenflowReferenceAdmin;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/zenflow-admin/v1/references")
public class ZenflowReferenceController {
    private final ZenflowReferenceAdmin service;
    public ZenflowReferenceController(ZenflowReferenceAdmin service){this.service=service;}
    @GetMapping public Map<String,Object> list(@RequestParam(required=false) String before){return service.list(before);}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable String id){return service.detail(id);}
    @PostMapping public Map<String,Object> create(@RequestBody Map<String,Object> body){return service.create(body);}
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable String id,@RequestBody Map<String,Object> body){return service.update(id,body);}
    @PostMapping("/{id}/revoke") public Map<String,Object> revoke(@PathVariable String id,@RequestBody Map<String,Object> body){return service.revoke(id,body);}
    @DeleteMapping("/{id}") public void delete(@PathVariable String id,@RequestParam String expectedSourceVersion){service.delete(id,expectedSourceVersion);}
    @GetMapping("/{id}/chunks") public Map<String,Object> chunks(@PathVariable String id){return service.chunks(id);}
    @PostMapping("/{id}/chunks") public Map<String,Object> addChunk(@PathVariable String id,@RequestBody Map<String,Object> body){return service.addChunk(id,body);}
    @PutMapping("/{id}/chunks/{chunk}") public Map<String,Object> editChunk(@PathVariable String id,@PathVariable String chunk,@RequestBody Map<String,Object> body){return service.editChunk(id,chunk,body);}
    @DeleteMapping("/{id}/chunks/{chunk}") public Map<String,Object> deleteChunk(@PathVariable String id,@PathVariable String chunk,@RequestBody Map<String,Object> body){return service.deleteChunk(id,chunk,body);}
    @PostMapping("/extract") public Map<String,Object> extract(@RequestBody Map<String,Object> body){return service.extract(body);}
}
