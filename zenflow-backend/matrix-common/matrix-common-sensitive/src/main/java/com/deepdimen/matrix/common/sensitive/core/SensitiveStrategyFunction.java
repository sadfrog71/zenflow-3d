package com.deepdimen.matrix.common.sensitive.core;

/**
 * @author caoyuan
 * @date 2024/6/27
 */
@FunctionalInterface
public interface SensitiveStrategyFunction<T, U, R> {

    R apply(T t, U u);
}
