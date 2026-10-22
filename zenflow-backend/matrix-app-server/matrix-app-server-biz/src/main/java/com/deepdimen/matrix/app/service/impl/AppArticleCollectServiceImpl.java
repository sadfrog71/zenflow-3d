package com.deepdimen.matrix.app.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.app.api.entity.AppArticleCollectEntity;
import com.deepdimen.matrix.app.mapper.AppArticleCollectMapper;
import com.deepdimen.matrix.app.service.AppArticleCollectService;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import org.springframework.stereotype.Service;

/**
 * 文章收藏表
 *
 * @author matrix
 * @date 2023-06-16 14:33:41
 */
@Service
public class AppArticleCollectServiceImpl extends ServiceImpl<AppArticleCollectMapper, AppArticleCollectEntity>
        implements AppArticleCollectService {

    @Override
    public Boolean saveArticleCollect(AppArticleCollectEntity appArticleCollect) {
        Long id = SecurityUtils.getUser().getId();
        appArticleCollect.setUserId(id);

        if (baseMapper.exists(Wrappers.<AppArticleCollectEntity>lambdaQuery()
                .eq(AppArticleCollectEntity::getUserId, id)
                .eq(AppArticleCollectEntity::getArticleId, appArticleCollect.getArticleId()))) {
            return Boolean.FALSE;
        }

        return this.save(appArticleCollect);

    }

}
