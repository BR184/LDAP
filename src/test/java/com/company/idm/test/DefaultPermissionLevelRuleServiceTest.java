package com.company.idm.test;

import com.company.idm.application.rbac.DefaultPermissionLevelRuleService;
import com.company.idm.common.enums.MenuType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 验证 permission_level 规则服务核心授权边界的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DefaultPermissionLevelRuleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private DefaultPermissionLevelRuleService ruleService;

    @Test
    void shouldReturnMinimalEffectivePermissionLevel() {
        when(userRepository.findRoleCodesByUsername("zhangsan")).thenReturn(Set.of("DEV", "NORMAL_USER"));
        when(roleRepository.findByCodes(Set.of("DEV", "NORMAL_USER"))).thenReturn(List.of(
            Role.builder().roleCode("DEV").permissionLevel(4).status(1).build(),
            Role.builder().roleCode("NORMAL_USER").permissionLevel(3).status(1).build()
        ));

        assertThat(ruleService.getEffectivePermissionLevel("zhangsan")).isEqualTo(3);
    }

    @Test
    void shouldAllowModifyBasicUserWhenOperatorIsAdmin() {
        User targetUser = User.builder().username("zhangsan").build();
        when(userRepository.findRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));

        ruleService.checkCanModifyBasicUser("admin", targetUser);
    }

    @Test
    void shouldRejectModifyBasicUserWhenPermissionLevelNotHigher() {
        User targetUser = User.builder().username("lisi").build();
        when(userRepository.findRoleCodesByUsername("zhangsan")).thenReturn(Set.of("NORMAL_USER"));
        when(userRepository.findRoleCodesByUsername("lisi")).thenReturn(Set.of("DEV"));
        when(roleRepository.findByCodes(Set.of("NORMAL_USER"))).thenReturn(List.of(
            Role.builder().roleCode("NORMAL_USER").permissionLevel(3).status(1).build()
        ));
        when(roleRepository.findByCodes(Set.of("DEV"))).thenReturn(List.of(
            Role.builder().roleCode("DEV").permissionLevel(3).status(1).build()
        ));

        assertThatThrownBy(() -> ruleService.checkCanModifyBasicUser("zhangsan", targetUser))
            .isInstanceOf(BizException.class)
            .hasMessage("无权修改当前用户的基础信息");
    }

    @Test
    void shouldRejectSensitiveUserOperationWhenOperatorIsNotAdmin() {
        User targetUser = User.builder().username("lisi").build();
        when(userRepository.findRoleCodesByUsername("zhangsan")).thenReturn(Set.of("NORMAL_USER"));

        assertThatThrownBy(() -> ruleService.checkCanModifySensitiveUser("zhangsan", targetUser))
            .isInstanceOf(BizException.class)
            .hasMessage("仅管理员允许执行敏感操作");
    }

    @Test
    void shouldRejectBindMenusWhenMenuPermissionLevelTooHigh() {
        Role targetRole = Role.builder().roleCode("NORMAL_USER").permissionLevel(3).status(1).build();
        Menu restrictedMenu = Menu.builder()
            .menuCode("SYSTEM_MANAGEMENT")
            .menuType(MenuType.CATALOG)
            .minPermissionLevel(1)
            .build();
        when(userRepository.findRoleCodesByUsername("admin")).thenReturn(Set.of("ADMIN"));

        assertThatThrownBy(() -> ruleService.checkCanBindMenus("admin", targetRole, List.of(restrictedMenu)))
            .isInstanceOf(BizException.class)
            .hasMessage("角色权限等级不足以绑定目标菜单");
    }

    @Test
    void shouldRejectManageMenuWhenOperatorIsNotAdmin() {
        when(userRepository.findRoleCodesByUsername("zhangsan")).thenReturn(Set.of("NORMAL_USER"));

        assertThatThrownBy(() -> ruleService.checkCanManageMenu("zhangsan"))
            .isInstanceOf(BizException.class)
            .hasMessage("仅管理员允许维护菜单");
    }

    @Test
    void shouldRejectManageDepartmentWhenOperatorIsNotAdmin() {
        when(userRepository.findRoleCodesByUsername("zhangsan")).thenReturn(Set.of("NORMAL_USER"));

        assertThatThrownBy(() -> ruleService.checkCanManageDepartment("zhangsan"))
            .isInstanceOf(BizException.class)
            .hasMessage("仅管理员允许维护部门");
    }
}
