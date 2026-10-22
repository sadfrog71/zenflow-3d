package com.deepdimen.matrix.knowledge.support.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段提示注解
 * <p>
 * 用于标注字段的提示信息，通常在 AI 模型工具调用（Function Calling）时使用。 该注解可以为字段提供描述信息，并指定字段是否必填。
 * 同时该注解被 @JacksonAnnotation 标记，可以在 Jackson 序列化/反序列化过程中被识别。
 * </p>
 *
 * @author caoyuan
 * @date 2024/09/27
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.FIELD, })
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotation
public @interface FieldPrompt {

	/**
	 * 参数的描述信息
	 * <p>
	 * 用于描述被注解字段的用途、格式要求等信息， 这些信息可以帮助 AI 模型更好地理解和使用该参数。
	 * </p>
	 * @return 参数的描述信息
	 */
	String value();

	/**
	 * 参数是否必填
	 * <p>
	 * 指定被注解的字段是否为必填项。 默认值为 true，表示该字段是必填的。
	 * </p>
	 * @return 如果参数是必填的返回 true，否则返回 false。默认值为 true。
	 */
	boolean required() default true;

}
