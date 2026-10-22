package com.deepdimen.matrix.zenflow.admin;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/zenflow-admin/v1/home-slogans")
public class ZenflowHomeSlogansController {
 private final ZenflowHomeSlogans service;
 public ZenflowHomeSlogansController(ZenflowHomeSlogans service){this.service=service;}
 @GetMapping public Map<String,Object> list(){return service.list();}
 @PutMapping public Map<String,Object> save(@RequestBody Map<String,Object> body){return service.save(body);}
}
