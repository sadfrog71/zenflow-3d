package com.deepdimen.matrix.app.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.app.api.entity.AppArticleEntity;
import com.deepdimen.matrix.app.api.entity.AppPageEntity;
import com.deepdimen.matrix.app.api.entity.AppRole;
import com.deepdimen.matrix.app.api.entity.AppTabbarEntity;
import com.deepdimen.matrix.app.api.entity.AppUserRole;
import com.deepdimen.matrix.app.api.entity.*;
import com.deepdimen.matrix.app.api.enums.PageTypeEnums;
import com.deepdimen.matrix.app.mapper.AppArticleMapper;
import com.deepdimen.matrix.app.mapper.AppPageMapper;
import com.deepdimen.matrix.app.mapper.AppRoleMapper;
import com.deepdimen.matrix.app.mapper.AppTabbarMapper;
import com.deepdimen.matrix.app.service.AppIndexService;
import com.deepdimen.matrix.app.service.AppUserRoleService;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * App首页服务实现类
 * 实现AppIndexService接口，提供首页数据、导航栏配置以及页面配置等功能。
 *
 * @author caoyuan
 * @date 2023/6/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppIndexServiceImpl implements AppIndexService {

    private final AppTabbarMapper appTabbarMapper;

    private final AppRoleMapper appRoleMapper;

    private final AppPageMapper appPageMapper;

    private final AppArticleMapper appArticleMapper;

    private final AppUserRoleService appUserRoleService;

    /**
     * 商城首页 首页数据
     */
    @Override
    public Map<String, Object> index() {
        Map<String, Object> response = new LinkedHashMap<>();
        AppPageEntity appPageEntity = appPageMapper.selectOne(Wrappers.<AppPageEntity>lambdaQuery()
                        .eq(AppPageEntity::getPageType, PageTypeEnums.HOME.getPageType())
                        .orderByDesc(AppPageEntity::getCreateTime),
                false
        );

        List<AppArticleEntity> articleList = appArticleMapper
                .selectList(Wrappers.<AppArticleEntity>lambdaQuery().orderByDesc(AppArticleEntity::getSort));
        response.put("pages", appPageEntity);
        response.put("article", articleList);
        return response;
    }

    /**
     * 导航栏配置
     *
     * @return Map<String, Object>
     */
    @Override
    public Map<String, Object> config() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<AppTabbarEntity> tabbarEntityList = appTabbarMapper
                .selectList(Wrappers.<AppTabbarEntity>lambdaQuery().orderByAsc(AppTabbarEntity::getSortOrder));
        response.put("tabbar", tabbarEntityList);
        return response;
    }

    /**
     * 根据页面类型获取最新的应用页面实体
     *
     * @param pageType 页面类型
     * @return 最新的应用页面实体，可能为null
     */
    @Override
    public AppPageEntity decorate(Integer pageType) {
        return appPageMapper.selectOne(
                Wrappers.<AppPageEntity>lambdaQuery()
                        .eq(AppPageEntity::getPageType, pageType)
                        .orderByDesc(AppPageEntity::getCreateTime),
                false
        );
    }


    /**
     * 获取工作台页面数据
     *
     * @return 工作台页面实体，包含过滤后的页面数据。若用户角色信息未找到或菜单ID列表为空，则返回null或原始页面数据
     */
    @Override
    public AppPageEntity workbench() {

        AppPageEntity appPage = appPageMapper.selectOne(
                Wrappers.<AppPageEntity>lambdaQuery()
                        .eq(AppPageEntity::getPageType, PageTypeEnums.WORKBENCH.getPageType())
                        .orderByDesc(AppPageEntity::getCreateTime),
                false
        );

        List<AppUserRole> appUserRoleList = appUserRoleService.list(Wrappers.<AppUserRole>lambdaQuery()
                .eq(AppUserRole::getUserId, SecurityUtils.getUser().getId()));

        if (CollUtil.isEmpty(appUserRoleList)) {
            log.warn("用户角色信息未找到，无法过滤工作台页面数据");
            return null;
        }

        List<String> menuIdList = appRoleMapper.selectByIds(appUserRoleList.stream().map(AppUserRole::getRoleId).toList())
                .stream().map(AppRole::getMenuId).flatMap(s -> Arrays.stream(s.split(StrUtil.COMMA))).toList();
        if (CollUtil.isEmpty(menuIdList)) {
            return appPage;
        }

        JSONArray pageDataArray = JSONUtil.parseArray(appPage.getPageData());
        for (Object groupObj : pageDataArray) {
            JSONObject group = (JSONObject) groupObj;
            JSONArray itemArray = group.getJSONObject("content").getJSONArray("data");

            // 使用 removeIf 进行过滤
            itemArray.removeIf(item -> {
                String menuId = ((JSONObject) item).getStr(AppPageEntity.Fields.id);
                return StrUtil.isNotBlank(menuId) && !menuIdList.contains(menuId);
            });
        }

        appPage.setPageData(pageDataArray.toStringPretty());
        return appPage;
    }
}
