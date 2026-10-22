package com.deepdimen.matrix.knowledge.support.constant;

import java.util.HashMap;
import java.util.Map;

public class ActionPrompts {

	public static final Map<String, String> ACTION_PROMPTS = new HashMap<>();

	static {
		ACTION_PROMPTS.put("polish", "请在不改变原意的前提下进行润色，提高清晰度、流畅度与正式度，尽量保持原长度相近。");
		ACTION_PROMPTS.put("rewrite", "请对文本进行多样化改写，重述结构与措辞，但不改变原意。");
		ACTION_PROMPTS.put("extend", "请在保持主题一致的前提下进行扩写，补充合理细节与论据，使内容更充实。");
		ACTION_PROMPTS.put("imitate", "请模仿原文的语气与风格进行重写，保持语境一致。");
		ACTION_PROMPTS.put("continue", "请基于给定文本自然续写，保持连贯性与语气一致，需要要重复已给内容。");
		ACTION_PROMPTS.put("shorten", "请在不改变关键信息的前提下进行压缩，减少冗余，使表达更简练。");
	}

}
