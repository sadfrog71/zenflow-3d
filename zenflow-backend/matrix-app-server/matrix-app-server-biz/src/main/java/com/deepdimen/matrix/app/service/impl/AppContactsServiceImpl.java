package com.deepdimen.matrix.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.app.api.entity.AppContactsEntity;
import com.deepdimen.matrix.app.mapper.AppContactsMapper;
import com.deepdimen.matrix.app.service.AppContactsService;
import com.deepdimen.matrix.common.data.datascope.DataScope;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import org.springframework.stereotype.Service;

/**
 * 通讯录服务实现类
 *
 * @author caoyuan
 * @date 2025/05/29
 */
@Service
public class AppContactsServiceImpl extends ServiceImpl<AppContactsMapper, AppContactsEntity> implements AppContactsService {
    /**
     * 根据应用联系人实体获取分页范围
     *
     * @param page        分页对象
     * @param appContacts 应用联系人实体
     * @return 分页范围对象
     */
    @Override
    public Page pageScope(Page page, AppContactsEntity appContacts) {
        LambdaQueryWrapper<AppContactsEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StrUtil.isNotBlank(appContacts.getContactName()), AppContactsEntity::getContactName, appContacts.getContactName());
        wrapper.like(StrUtil.isNotBlank(appContacts.getContactPhone()), AppContactsEntity::getContactPhone, appContacts.getContactPhone());

        DataScope dataScope = new DataScope();
        dataScope.setUsername(SecurityUtils.getUser().getUsername());
        return baseMapper.selectPageByScope(page, wrapper, dataScope);
    }
}