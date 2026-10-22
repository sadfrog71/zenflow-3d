package com.deepdimen.matrix.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.deepdimen.matrix.app.api.entity.AppArticleCategoryEntity;
import com.deepdimen.matrix.app.api.entity.AppArticleCollectEntity;
import com.deepdimen.matrix.app.api.entity.AppArticleEntity;
import com.deepdimen.matrix.app.mapper.AppArticleCollectMapper;
import com.deepdimen.matrix.app.mapper.AppArticleMapper;
import com.deepdimen.matrix.app.service.AppArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 文章资讯
 *
 * @author matrix
 * @date 2023-06-07 16:32:35
 */
@Service
@RequiredArgsConstructor
public class AppArticleServiceImpl extends ServiceImpl<AppArticleMapper, AppArticleEntity>
		implements AppArticleService {

	private final AppArticleCollectMapper collectMapper;

	/**
	 * 获取文章并使阅读数+1
	 * @param id id
	 * @return
	 */
	@Override
	public AppArticleEntity getArticleAndIncrById(Long id, Long userId) {
		AppArticleEntity appArticleEntity = baseMapper.selectById(id);
		// 查询是否收藏了
		if (Objects.nonNull(userId)) {
			boolean exists = collectMapper.exists(Wrappers.<AppArticleCollectEntity>lambdaQuery()//
				.eq(AppArticleCollectEntity::getArticleId, appArticleEntity.getId())//
				.eq(AppArticleCollectEntity::getUserId, userId));
			appArticleEntity.setCollect(exists);
		}

		// TODO 更新条件需要根据其他指数限制
		Integer nowVisit = appArticleEntity.getVisit();
		appArticleEntity.setVisit(nowVisit + 1);
		// 乐观锁
		baseMapper.update(appArticleEntity,
				Wrappers.<AppArticleEntity>lambdaQuery()
					.eq(AppArticleEntity::getId, id)
					.eq(AppArticleEntity::getVisit, nowVisit));
		return appArticleEntity;
	}

	/**
	 * 分页查询文章列表 包含分类名称
	 * @param page 分页参数
	 * @param appArticle 文章查询条件
	 * @return
	 */
	@Override
	public Page<AppArticleEntity> pageAndCname(Page<AppArticleEntity> page, AppArticleEntity appArticle) {
		MPJLambdaWrapper<AppArticleEntity> wrapper = new MPJLambdaWrapper<>();
		wrapper.selectAll(AppArticleEntity.class)
			.selectAs(AppArticleCategoryEntity::getName, AppArticleEntity.Fields.cname)
			.leftJoin(AppArticleCategoryEntity.class, AppArticleCategoryEntity::getId, AppArticleEntity::getCid)
			.like(StrUtil.isNotBlank(appArticle.getAuthor()), AppArticleEntity::getAuthor, appArticle.getAuthor())
			.like(StrUtil.isNotBlank(appArticle.getTitle()), AppArticleEntity::getTitle, appArticle.getTitle())
			.eq(Objects.nonNull(appArticle.getCid()), AppArticleEntity::getCid, appArticle.getCid())
			// 推荐筛选: 0=否 1=是，传 null 则不过滤
			.eq(Objects.nonNull(appArticle.getIsRecommended()), AppArticleEntity::getIsRecommended,
					appArticle.getIsRecommended())
			.orderByAsc(AppArticleEntity::getSort)
			.orderByDesc(AppArticleEntity::getCreateTime);
		return this.page(page, wrapper);
	}

}
