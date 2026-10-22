package com.deepdimen.matrix.zenflow.community;
import com.deepdimen.matrix.common.core.util.R;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/zenflow/v1/community")
public class ZenflowCommunityController {
 private final ZenflowCommunity community;public ZenflowCommunityController(ZenflowCommunity community){this.community=community;}
 @GetMapping("/posts") public R<?> list(@RequestParam(defaultValue="false") boolean mine,@RequestParam(required=false) String category,@RequestParam(required=false) String before,@RequestParam(defaultValue="20") int limit){return R.ok(community.list(mine,category,before,limit));}
 @PostMapping("/posts") public R<?> submit(@RequestBody Map<String,Object> body){return R.ok(community.submit(body));}
 @GetMapping("/posts/{id}") public R<?> detail(@PathVariable String id){return R.ok(community.detail(id));}
 @PostMapping("/posts/{id}/withdraw") public R<?> withdraw(@PathVariable String id,@RequestBody Map<String,Integer> body){Integer version=body.get("expectedVersion");if(version==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"COMMUNITY_VERSION_REQUIRED");return R.ok(community.withdraw(id,version));}
 @PostMapping("/attachments") public R<?> upload(@RequestBody Map<String,Object> body){return R.ok(community.upload(body));}
 @GetMapping("/attachments/{id}") public R<?> image(@PathVariable String id){return R.ok(community.image(id,false));}
 @DeleteMapping("/attachments/{id}") public R<?> discard(@PathVariable String id){community.discard(id);return R.ok();}
}
