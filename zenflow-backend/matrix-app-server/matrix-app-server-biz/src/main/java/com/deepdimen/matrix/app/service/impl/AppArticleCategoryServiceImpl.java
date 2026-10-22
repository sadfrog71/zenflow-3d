package com.deepdimen.matrix.app.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.app.api.entity.AppArticleCategoryEntity;
import com.deepdimen.matrix.app.mapper.AppArticleCategoryMapper;
import com.deepdimen.matrix.app.service.AppArticleCategoryService;
import org.springframework.stereotype.Service;

/**
 * 文章分类表
 *
 * @author matrix
 * @date 2023-06-07 16:28:03
 */
@Service
public class AppArticleCategoryServiceImpl extends ServiceImpl<AppArticleCategoryMapper, AppArticleCategoryEntity>
		implements AppArticleCategoryService {

}
