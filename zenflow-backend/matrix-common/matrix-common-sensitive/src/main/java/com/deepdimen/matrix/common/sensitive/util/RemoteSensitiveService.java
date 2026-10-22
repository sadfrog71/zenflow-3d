package com.deepdimen.matrix.common.sensitive.util;

import com.deepdimen.matrix.common.core.constant.ServiceNameConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * @author caoyuan
 * @date 2024/7/7
 */
@FeignClient(contextId = "remoteSensitiveService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteSensitiveService {

    @NoToken
    @GetMapping("/sysSensitiveWord/remote/list/{type}")
    R<List<String>> list(@PathVariable String type);
}
