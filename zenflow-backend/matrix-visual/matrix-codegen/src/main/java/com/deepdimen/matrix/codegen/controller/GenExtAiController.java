package com.deepdimen.matrix.codegen.controller;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import org.anyline.entity.DataSet;
import org.anyline.proxy.ServiceProxy;
import org.anyline.service.AnylineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扩展aI 接口
 *
 * @author caoyuan
 * @date 2024/7/30
 */
@RestController
@RequestMapping("/ext")
public class GenExtAiController {


    /**
     * 执行 SQL
     *
     * @param dsName DS 名称
     * @param sql    SQL格式
     * @return {@link R }
     */
    @GetMapping("/execSQL")
    @HasPermission("codegen_table_add")
    public R execSQL(String dsName, String sql) {
        DynamicDataSourceContextHolder.push(dsName);
        AnylineService service = ServiceProxy.service();

        try {
            DataSet querys = service.querys(sql);
            return R.ok(querys);
        } catch (Exception e) {
            return R.failed(e.getMessage());
        }
    }
}
