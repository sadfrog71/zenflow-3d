package com.deepdimen.matrix.zenflow.identity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/zenflow/v1/activity")
public class ZenflowActivityController {
 private final ZenflowUserActivity activity;
 public ZenflowActivityController(ZenflowUserActivity activity){this.activity=activity;}
 @PostMapping public java.util.Map<String,Boolean> pulse(@RequestBody ZenflowUserActivity.Pulse pulse){activity.pulse(pulse);return java.util.Map.of("accepted",true);}
}
