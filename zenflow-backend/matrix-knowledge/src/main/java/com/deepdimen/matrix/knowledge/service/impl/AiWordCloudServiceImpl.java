package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.font.scale.LinearFontScalar;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.nlp.tokenizers.ChineseWordTokenizer;
import com.kennycason.kumo.palette.ColorPalette;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.service.AiWordCloudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI词云生成服务实现类
 * <p>
 * 基于kumo库实现中文词云图生成功能 使用中文分词器处理文档切片内容，生成美观的矩形词云图
 *
 * @author matrix
 * @date 2025-01-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWordCloudServiceImpl implements AiWordCloudService {

	private final AiSliceMapper aiSliceMapper;

	/**
	 * 默认词云图尺寸
	 */
	private static final Dimension DEFAULT_DIMENSION = new Dimension(800, 600);

	/**
	 * 默认颜色配色方案 - 深色系配色，适合浅色背景
	 */
	private static final Color[] DEFAULT_COLORS = { new Color(0xFF6B6B), // 珊瑚红
			new Color(0x4ECDC4), // 青绿色
			new Color(0x45B7D1), // 天蓝色
			new Color(0x96CEB4), // 薄荷绿
			new Color(0xFECA57), // 金黄色
			new Color(0xFF9FF3), // 粉紫色
			new Color(0x54A0FF), // 亮蓝色
			new Color(0x5F27CD) // 紫色
	};

	@Override
	public byte[] generateWordCloud(Long documentId) throws IOException {
		return generateWordCloud(documentId, DEFAULT_DIMENSION);
	}

	@Override
	public byte[] generateWordCloud(Long documentId, Dimension dimension) throws IOException {
		log.info("开始为文档ID {} 生成词云图，尺寸: {}x{}", documentId, dimension.width, dimension.height);

		// 1. 查询文档的所有切片内容
		List<AiSliceEntity> sliceList = aiSliceMapper.selectList(Wrappers.<AiSliceEntity>lambdaQuery()
			.eq(AiSliceEntity::getDocumentId, documentId)
			.isNotNull(AiSliceEntity::getContent));

		if (CollUtil.isEmpty(sliceList)) {
			log.warn("文档ID {} 没有找到有效的切片内容", documentId);
			throw new IllegalArgumentException("文档不存在或没有切片内容");
		}

		// 2. 组合所有切片内容为完整文本
		String documentContent = sliceList.stream()
			.map(AiSliceEntity::getContent)
			.filter(StrUtil::isNotBlank)
			.collect(Collectors.joining(StrUtil.SPACE));

		if (StrUtil.isBlank(documentContent)) {
			log.warn("文档ID {} 的切片内容为空", documentId);
			throw new IllegalArgumentException("文档内容为空");
		}

		log.info("文档ID {} 内容长度: {} 字符", documentId, documentContent.length());

		// 3. 使用中文分词器进行词频分析
		FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
		frequencyAnalyzer.setWordFrequenciesToReturn(300); // 返回前300个高频词
		frequencyAnalyzer.setMinWordLength(2); // 最小词长度为2
		frequencyAnalyzer.setWordTokenizer(new ChineseWordTokenizer()); // 使用中文分词器

		// 添加常见的中文停用词
		frequencyAnalyzer.setStopWords(getStopWords());

		List<WordFrequency> wordFrequencies = frequencyAnalyzer
			.load(IoUtil.toStream(documentContent, StandardCharsets.UTF_8));

		if (CollUtil.isEmpty(wordFrequencies)) {
			log.warn("文档ID {} 分词后没有有效词汇", documentId);
			throw new IllegalArgumentException("文档分词后没有有效内容");
		}

		log.info("文档ID {} 分词得到 {} 个有效词汇", documentId, wordFrequencies.size());

		// 4. 创建词云图配置
		WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
		wordCloud.setPadding(2); // 设置词汇间距

		// 适合light模式查看
		wordCloud.setBackgroundColor(new Color(0xFFFFFF)); // 纯白色背景

		wordCloud.setColorPalette(new ColorPalette(DEFAULT_COLORS)); // 设置深色字体颜色方案
		wordCloud.setFontScalar(new LinearFontScalar(12, 40)); // 字体大小范围
		// 5. 生成词云图
		wordCloud.build(wordFrequencies);

		// 6. 将词云图转换为字节数组
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		wordCloud.writeToStreamAsPNG(outputStream);

		byte[] result = outputStream.toByteArray();
		log.info("文档ID {} 词云图生成成功，图片大小: {} 字节", documentId, result.length);

		return result;
	}

	/**
	 * 获取常用停用词集合
	 * @return 包含常用停用词的不可变集合
	 */
	private Set<String> getStopWords() {
		return Set.of("tr", "td", "table", "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也",
				"很", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "可以", "对", "能", "他", "她", "它", "我们",
				"你们", "他们", "这个", "那个", "这些", "那些", "什么", "怎么", "为什么", "哪里", "谁", "怎样", "多少", "几", "第一", "第二", "第三",
				"等", "等等", "比如", "或者", "还是", "如果", "因为", "所以", "但是", "然后", "并且", "而且", "或", "及", "以及", "以", "为", "由",
				"从", "向", "到", "把", "被", "让", "使", "与", "同", "跟", "关于", "对于", "按照", "根据", "通过", "经过", "除了", "包括", "其中",
				"之间", "当中", "里面", "外面", "上面", "下面", "前面", "后面", "左边", "右边", "中间", "附近", "周围", "四周", "各种", "所有", "每个",
				"任何", "某个", "另外", "其他", "别的", "更", "最", "非常", "特别", "尤其", "特殊", "一般", "通常", "经常", "总是", "从来", "从不",
				"偶尔", "有时", "一直", "已经", "刚刚", "马上", "立即", "现在", "目前", "以前", "以后", "将来", "过去", "当时", "同时", "之前", "之后",
				"期间", "时候", "时间", "地方", "位置", "情况", "状态", "方式", "方法", "手段", "途径", "原因", "结果", "目的", "作用", "影响", "效果",
				"意义", "价值", "重要", "必要", "可能", "应该", "需要", "希望", "想要", "打算", "计划", "准备", "开始", "继续", "停止", "结束", "完成",
				"成功", "失败", "正确", "错误", "清楚", "明白", "知道", "了解", "认识", "记得", "忘记", "想起", "发现", "找到", "得到", "失去", "拥有",
				"缺少", "增加", "减少", "提高", "降低", "改变", "变化", "发展", "进步", "退步", "问题", "解决", "处理", "应对", "面对", "遇到", "碰到",
				"出现", "发生", "产生", "造成", "导致", "引起");
	}

}