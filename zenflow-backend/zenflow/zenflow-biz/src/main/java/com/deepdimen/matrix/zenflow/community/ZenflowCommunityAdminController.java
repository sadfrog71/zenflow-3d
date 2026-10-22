package com.deepdimen.matrix.zenflow.community;
import com.deepdimen.matrix.common.core.util.R;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/zenflow-admin/v1/community")
public class ZenflowCommunityAdminController {
 private final ZenflowCommunity community;public ZenflowCommunityAdminController(ZenflowCommunity community){this.community=community;}
 @GetMapping("/posts") public Map<String,Object> list(@RequestParam(defaultValue="PENDING") String state,@RequestParam(required=false) String before,@RequestParam(defaultValue="20") int limit){return community.adminList(state,before,limit);}
 @PostMapping("/posts/{id}/review") public Map<String,Object> review(@PathVariable String id,@RequestBody Map<String,Object> body){return community.moderate(id,body);}
 @GetMapping("/attachments/{id}") public Map<String,Object> image(@PathVariable String id){return community.image(id,true);}
}
