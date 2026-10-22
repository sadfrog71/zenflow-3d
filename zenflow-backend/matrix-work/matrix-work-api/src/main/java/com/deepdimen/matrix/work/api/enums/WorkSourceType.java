package com.deepdimen.matrix.work.api.enums;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkSourceType {
    AI_DATASOURCE("AI问数数据库", true, List.of("DATABASE_REF")),
    ENTERPRISE_MAIL("企业邮箱", true, List.of("IMAP", "POP3")),
    LOCAL_FOLDER("服务器文件夹", true, List.of("LOCAL_FOLDER")),
    PIG_APPROVAL("Pig AI审批", true, List.of("PIG_APPROVAL")),
    WEB_NEWS("Web资讯", true, List.of("WEB_SEARCH", "RSS")),
    FEISHU("飞书", false, List.of()),
    YONYOU_NC("用友NC待办", false, List.of());

    private final String displayName;
    private final boolean configurable;
    private final List<String> protocols;
}
