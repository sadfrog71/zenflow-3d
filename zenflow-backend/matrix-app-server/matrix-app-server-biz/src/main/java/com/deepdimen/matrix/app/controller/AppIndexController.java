package com.deepdimen.matrix.app.controller;

import com.deepdimen.matrix.app.service.AppIndexService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.Inner;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * app 首页控制
 *
 * @author caoyuan
 * @date 2023/6/8
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/index")
@Tag(description = "App 页面控制", name = "app index")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AppIndexController {

    private final AppIndexService indexService;

    /**
     * 获取主页配置
     *
     * @return {@link R }
     */
    @Inner(value = false)
    @GetMapping("/index")
    public R index() {
        return R.ok(indexService.index());
    }

    /**
     * 获取移动端appTabbar 配置
     *
     * @return {@link R }
     */
    @Inner(value = false)
    @GetMapping("/config")
    public R config() {
        return R.ok(indexService.config());
    }

    /**
     * decorate方法，用于根据页面类型装饰内容
     *
     * @param pageType 页面类型
     * @return 装饰后的内容
     * @throws IllegalArgumentException 参数id为空时抛出异常
     */
    @Inner(value = false)
    @GetMapping("/decorate")
    public R decorate(@Validated @RequestParam("id") Integer pageType) {
        return R.ok(indexService.decorate(pageType));
    }

    @GetMapping("/workbench")
    public R workbench() {
        return R.ok(indexService.workbench());
    }

}
