package com.deepdimen.matrix.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.deepdimen.matrix.admin.api.constant.TenantStateEnum;
import com.deepdimen.matrix.admin.api.dto.UserDTO;
import com.deepdimen.matrix.admin.api.entity.SysTenant;
import com.deepdimen.matrix.admin.mapper.SysDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysPostMapper;
import com.deepdimen.matrix.admin.mapper.SysRoleMapper;
import com.deepdimen.matrix.admin.mapper.SysRoleMenuMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantUserMapper;
import com.deepdimen.matrix.admin.mapper.SysUserDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.mapper.SysUserPostMapper;
import com.deepdimen.matrix.admin.mapper.SysUserRoleMapper;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.service.SysDeptService;
import com.deepdimen.matrix.admin.service.SysDictItemService;
import com.deepdimen.matrix.admin.service.SysDictService;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.admin.service.SysOauthClientDetailsService;
import com.deepdimen.matrix.admin.service.SysPublicParamService;
import com.deepdimen.matrix.admin.service.SysRoleService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.service.MatrixUser;

@ExtendWith(MockitoExtension.class)
class SysTenantServiceTenantSwitchTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 2052188107445968897L;

    @Mock
    private SysOauthClientDetailsService clientServices;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;
    @Mock
    private SysDictItemService dictItemService;
    @Mock
    private SysPublicParamService paramService;
    @Mock
    private SysRoleService roleService;
    @Mock
    private SysMenuService menuService;
    @Mock
    private SysDeptService deptService;
    @Mock
    private SysDictService dictService;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysTenantUserMapper sysTenantUserMapper;
    @Mock
    private SysUserPostMapper sysUserPostMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysDeptMapper sysDeptMapper;
    @Mock
    private SysPostMapper sysPostMapper;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private SysTenantMapper sysTenantMapper;

    @InjectMocks
    private SysTenantServiceImpl service;

    @BeforeEach
    void setAuthenticatedUser() {
        if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(SysUser.class) == null) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new Configuration(), "tenant-switch-test"), SysUser.class);
        }
        MatrixUser user = new MatrixUser(USER_ID, "admin", List.of(), null, List.of(), List.of(), null, null, null,
                null, null, 1L, "", true, true, "", true, null, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        ReflectionTestUtils.setField(service, "baseMapper", sysTenantMapper);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTenantWithoutUserMembership() {
        when(sysTenantMapper.selectById(TENANT_ID)).thenReturn(validTenant());
        when(sysTenantUserMapper.selectCount(any())).thenReturn(0L);

        R<?> result = service.updateUserTenant(request(TENANT_ID));

        assertFalse(result.isOk());
        verify(sysUserMapper, never()).update(any());
    }

    @Test
    void switchesToValidMemberTenantAndEvictsUserCache() {
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(sysTenantMapper.selectById(TENANT_ID)).thenReturn(validTenant());
        when(sysTenantUserMapper.selectCount(any())).thenReturn(1L);
        when(sysUserMapper.update(any())).thenReturn(1);

        R<?> result = service.updateUserTenant(request(TENANT_ID));

        assertTrue(result.isOk());
        verify(sysUserMapper).update(any());
        verify(cache).evict("admin");
    }

    @Test
    void rejectsSwitchWhenUserTenantUpdateDoesNotMatch() {
        when(sysTenantMapper.selectById(TENANT_ID)).thenReturn(validTenant());
        when(sysTenantUserMapper.selectCount(any())).thenReturn(1L);
        when(sysUserMapper.update(any())).thenReturn(0);

        R<?> result = service.updateUserTenant(request(TENANT_ID));

        assertFalse(result.isOk());
        verify(cacheManager, never()).getCache(anyString());
    }

    private UserDTO request(Long tenantId) {
        UserDTO request = new UserDTO();
        request.setTenantId(tenantId);
        return request;
    }

    private SysTenant validTenant() {
        SysTenant tenant = new SysTenant();
        tenant.setId(TENANT_ID);
        tenant.setStatus(TenantStateEnum.NORMAL.getCode());
        tenant.setStartTime(LocalDateTime.now().minusDays(1));
        tenant.setEndTime(LocalDateTime.now().plusDays(1));
        return tenant;
    }

}
