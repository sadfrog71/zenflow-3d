package com.deepdimen.matrix.capsule.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CapsuleTitleNormalizationTest {

    @Test
    void automaticTitleRemovesMarkdownHeadingPrefix() {
        assertEquals("现场问题", CapsuleServiceImpl.normalizeTitle(null, "## 现场问题\n后续正文"));
        assertEquals("普通首行", CapsuleServiceImpl.normalizeTitle("", "普通首行\n后续正文"));
    }

    @Test
    void explicitTitleKeepsItsLiteralText() {
        assertEquals("# 明确标题", CapsuleServiceImpl.normalizeTitle(" # 明确标题 ", "正文"));
    }
}
