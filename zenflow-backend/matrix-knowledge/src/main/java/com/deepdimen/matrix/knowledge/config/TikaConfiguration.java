package com.deepdimen.matrix.knowledge.config;

import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Apache Tika文档解析配置类 配置Tika用于解析各种格式的文档内容，如Word、Excel、PPT、PDF等
 *
 * @author caoyuan
 * @date 2024/3/14
 */
@Configuration(proxyBeanMethods = false)
public class TikaConfiguration {

	/**
	 * 创建并配置Tika实例 从类路径加载自定义的tika-config.xml配置文件
	 * @return 配置好的Tika实例，用于文档内容提取
	 * @throws Exception 配置文件加载失败时抛出异常
	 */
	@Bean
	public Tika tika() throws Exception {
		// 从类路径加载Tika配置文件
		ClassPathResource resource = new ClassPathResource("tika-config.xml");
		// 根据配置文件创建TikaConfig实例
		TikaConfig config = new TikaConfig(resource.getInputStream());
		// 获取配置的文档类型检测器
		Detector detector = config.getDetector();
		// 创建自动检测解析器，支持多种文档格式
		Parser autoDetectParser = new AutoDetectParser(config);
		// 返回配置完成的Tika实例
		return new Tika(detector, autoDetectParser);
	}

}
