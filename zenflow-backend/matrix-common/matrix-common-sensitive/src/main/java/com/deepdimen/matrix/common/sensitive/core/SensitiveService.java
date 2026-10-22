package com.deepdimen.matrix.common.sensitive.core;

import com.deepdimen.matrix.common.sensitive.annotation.Sensitive;

/**
 * 脱敏逻辑增强处理
 *
 * @author caoyuan
 * @date 2024/6/27
 */
public interface SensitiveService {
    /**
     * 是否脱敏
     */
    boolean isSensitive(Sensitive sensitive);
}
