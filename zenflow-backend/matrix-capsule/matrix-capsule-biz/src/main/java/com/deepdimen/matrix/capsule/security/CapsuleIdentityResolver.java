package com.deepdimen.matrix.capsule.security;

import com.deepdimen.matrix.common.security.util.SecurityUtils;
import org.springframework.stereotype.Component;

@Component
public class CapsuleIdentityResolver {

    public CapsuleIdentity current() {
        if (SecurityUtils.getUser() == null
                || SecurityUtils.getUser().getTenantId() == null
                || SecurityUtils.getUser().getId() == null) {
            throw new IllegalStateException("未获取到当前用户身份");
        }
        var user=SecurityUtils.getUser();
        String subject;
        if ("0".equals(user.getUserType())) subject="ADMIN_USER";
        else if ("1".equals(user.getUserType()) && "zenflow-ios".equals(user.getAttributes().get(com.deepdimen.matrix.common.core.constant.SecurityConstants.CLIENT_ID))) subject="APP_USER";
        else throw new IllegalStateException("不支持的记录身份");
        return new CapsuleIdentity(user.getTenantId(), user.getId(), subject);
    }
}
