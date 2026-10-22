package com.deepdimen.matrix.app.service;

import com.github.yulichang.base.MPJBaseService;
import com.deepdimen.matrix.app.api.entity.AppArticleCollectEntity;

public interface AppArticleCollectService extends MPJBaseService<AppArticleCollectEntity> {

	Boolean saveArticleCollect(AppArticleCollectEntity appArticleCollect);

}
