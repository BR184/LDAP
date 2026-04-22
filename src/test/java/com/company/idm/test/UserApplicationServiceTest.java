package com.company.idm.test;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.UpdateUserStatusCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.eq;

/**
 * 验证用户应用服务核心流程的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Mock
    private LdapGroupService ldapGroupService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PolicyRefreshService policyRefreshService;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private PermissionLevelRuleService permissionLevelRuleService;

    @Mock
    private AppLdapProperties ldapProperties;

    @InjectMocks
    private UserApplicationService userApplicationService;

    @Test
    void shouldListUsers() {
        when(userRepository.findByConditions(null, null, null)).thenReturn(List.of(buildUser(1L, "admin", UserStatus.ENABLED, 0)));

        List<User> users = userApplicationService.listUsers(null, null, null);

        assertThat(users).hasSize(1);
    }

    @Test
    void shouldListUsersWithNormalizedConditions() {
        when(userRepository.findByConditions("admin", "D001", UserStatus.ENABLED.getCode()))
            .thenReturn(List.of(buildUser(1L, "admin", UserStatus.ENABLED, 0)));

        List<User> users = userApplicationService.listUsers(" admin ", " D001 ", UserStatus.ENABLED.getCode());

        assertThat(users).hasSize(1);
        verify(userRepository).findByConditions("admin", "D001", UserStatus.ENABLED.getCode());
    }

    @Test
    void shouldCreateUserSuccessfully() {
        CreateUserCommand command = new CreateUserCommand(
            "zhangsan", "张三", "zhangsan@corp.local", "13900000000", "E10001", "D001", "Password@123", List.of(1L), "admin"
        );
        Department department = Department.builder().id(1L).deptCode("D001").deptName("研发中心").sourceType(SourceType.MANUAL).status(1).build();
        User firstSaved = buildUser(2L, "zhangsan", UserStatus.ENABLED, 0);
        User secondSaved = firstSaved.toBuilder().ldapDn("uid=zhangsan,ou=people,dc=corp,dc=local").build();

        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.empty());
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(department));
        when(ldapDirectoryService.existsByUid("zhangsan")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(firstSaved, secondSaved);
        when(ldapDirectoryService.createUser(firstSaved, "123456")).thenReturn("uid=zhangsan,ou=people,dc=corp,dc=local");
        when(ldapGroupService.createGroup("D001", "研发中心")).thenReturn("cn=D001_研发中心,ou=groups,dc=corp,dc=local");

        User created = userApplicationService.createUser(command);

        assertThat(created.getLdapDn()).isEqualTo("uid=zhangsan,ou=people,dc=corp,dc=local");
        verify(passwordPolicyValidator).validate("123456");
        verify(ldapGroupService).createGroup("D001", "研发中心");
        verify(ldapGroupService).addUserToGroup("zhangsan", "D001");
        verify(userRepository).assignRoles(2L, List.of(1L));
        verify(policyRefreshService).refresh();
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        assertThat(userCaptor.getAllValues().get(0).getSourceType()).isEqualTo(SourceType.MANUAL);
        assertThat(userCaptor.getAllValues().get(1).getLdapDn()).isEqualTo("uid=zhangsan,ou=people,dc=corp,dc=local");
        verify(departmentRepository).save(argThat(savedDepartment ->
            "D001".equals(savedDepartment.getDeptCode())
                && "cn=D001_研发中心,ou=groups,dc=corp,dc=local".equals(savedDepartment.getLdapDn())
        ));
    }

    @Test
    void shouldUpdateUserSuccessfully() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 0);
        Department department = Department.builder().id(1L).deptCode("D002").deptName("运维部").sourceType(SourceType.MANUAL).status(1).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByDeptCode("D002")).thenReturn(Optional.of(department));
        when(ldapGroupService.createGroup("D002", "运维部")).thenReturn("cn=D002_运维部,ou=groups,dc=corp,dc=local");

        User updated = userApplicationService.updateUser(new com.company.idm.application.user.UpdateUserCommand(
            2L, "张三-更新", "new@corp.local", "13911111111", "E10002", "D002", "admin"
        ));

        assertThat(updated.getRealName()).isEqualTo("张三-更新");
        assertThat(updated.getDeptCode()).isEqualTo("D002");
        verify(permissionLevelRuleService).checkCanModifyBasicUser("admin", existing);
        verify(userRepository).updateProfile(any(User.class));
        verify(ldapDirectoryService).updateUser(any(User.class));
        verify(ldapGroupService).removeUserFromGroup("zhangsan", "D001");
        verify(ldapGroupService).createGroup("D002", "运维部");
        verify(ldapGroupService).addUserToGroup("zhangsan", "D002");
        verify(departmentRepository).save(argThat(savedDepartment ->
            "D002".equals(savedDepartment.getDeptCode())
                && "cn=D002_运维部,ou=groups,dc=corp,dc=local".equals(savedDepartment.getLdapDn())
        ));
    }

    @Test
    void shouldRejectCreateUserWhenDepartmentDisabled() {
        Department disabledDepartment = Department.builder()
            .id(1L)
            .deptCode("D009")
            .deptName("停用部门")
            .sourceType(SourceType.MANUAL)
            .status(0)
            .build();
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.empty());
        when(departmentRepository.findByDeptCode("D009")).thenReturn(Optional.of(disabledDepartment));

        assertThatThrownBy(() -> userApplicationService.createUser(new CreateUserCommand(
            "zhangsan", "张三", "zhangsan@corp.local", "13900000000", "E10001", "D009", "Password@123", List.of(1L), "admin"
        )))
            .isInstanceOf(BizException.class)
            .hasMessage("部门已停用");
    }

    @Test
    void shouldRejectDuplicateUserCreation() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(buildUser(1L, "admin", UserStatus.ENABLED, 0)));

        assertThatThrownBy(() -> userApplicationService.createUser(new CreateUserCommand(
            "admin", "管理员", "admin@corp.local", "13800000000", "E001", "D001", "Password@123", List.of(1L), "admin"
        ))).isInstanceOf(BizException.class).hasMessage("用户名已存在");

        verify(ldapDirectoryService, never()).createUser(any(), any());
    }

    @Test
    void shouldDisableUserAndBumpTokenVersion() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 5);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));

        userApplicationService.updateStatus(new UpdateUserStatusCommand(2L, UserStatus.DISABLED.getCode(), "admin"));

        verify(permissionLevelRuleService).checkCanModifySensitiveUser("admin", existing);
        verify(userRepository).updateStatus(2L, UserStatus.DISABLED.getCode(), 6);
        verify(ldapDirectoryService).disableUser("zhangsan");
    }

    @Test
    void shouldEnableUserAndBumpTokenVersionFromNull() {
        User existing = buildUser(2L, "zhangsan", UserStatus.DISABLED, 0).toBuilder().tokenVersion(null).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));

        userApplicationService.updateStatus(new UpdateUserStatusCommand(2L, UserStatus.ENABLED.getCode(), "admin"));

        verify(permissionLevelRuleService).checkCanModifySensitiveUser("admin", existing);
        verify(userRepository).updateStatus(2L, UserStatus.ENABLED.getCode(), 1);
        verify(ldapDirectoryService).enableUser("zhangsan");
    }

    @Test
    void shouldDeleteUserByPhysicalLdapDeleteAndLogicalDbDelete() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));

        userApplicationService.deleteUser(new com.company.idm.application.user.DeleteUserCommand(2L, "admin"));

        verify(permissionLevelRuleService).checkCanModifySensitiveUser("admin", existing);
        verify(ldapGroupService).removeUserFromAllGroups("zhangsan");
        verify(ldapDirectoryService).deleteUser("zhangsan");
        verify(userRepository).removeAllRoles(2L);
        verify(userRepository).logicalDelete(2L, "zhangsan__deleted__2", 3);
    }

    @Test
    void shouldChangePasswordSuccessfully() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2);
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(existing));
        when(ldapDirectoryService.authenticate("zhangsan", "123456")).thenReturn(true);

        userApplicationService.changePassword(new com.company.idm.application.user.ChangePasswordCommand(
            "zhangsan", "123456", "654321", "654321"
        ));

        verify(passwordPolicyValidator).validate("654321");
        verify(ldapDirectoryService).resetPassword("zhangsan", "654321");
        verify(userRepository).bumpTokenVersion(2L, 3);
    }

    @Test
    void shouldRejectChangePasswordWhenOldPasswordInvalid() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2);
        when(userRepository.findByUsername("zhangsan")).thenReturn(Optional.of(existing));
        when(ldapDirectoryService.authenticate("zhangsan", "bad")).thenReturn(false);

        assertThatThrownBy(() -> userApplicationService.changePassword(new com.company.idm.application.user.ChangePasswordCommand(
            "zhangsan", "bad", "654321", "654321"
        ))).isInstanceOf(BizException.class).hasMessage("旧密码错误");
    }

    @Test
    void shouldResetPasswordWithDefaultPasswordForAdmin() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));

        String password = userApplicationService.resetPassword(new com.company.idm.application.user.ResetPasswordCommand(2L, "admin"));

        assertThat(password).isEqualTo("123456");
        verify(permissionLevelRuleService).checkCanModifySensitiveUser("admin", existing);
        verify(passwordPolicyValidator).validate("123456");
        verify(ldapDirectoryService).resetPassword("zhangsan", "123456");
        verify(userRepository).bumpTokenVersion(2L, 3);
    }

    @Test
    void shouldRejectResetPasswordWhenOperatorNotAdmin() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new BizException("AUTH_FORBIDDEN", "仅管理员允许执行敏感操作"))
            .when(permissionLevelRuleService).checkCanModifySensitiveUser("zhangsan", existing);

        assertThatThrownBy(() -> userApplicationService.resetPassword(new com.company.idm.application.user.ResetPasswordCommand(2L, "zhangsan")))
            .isInstanceOf(BizException.class)
            .hasMessage("仅管理员允许执行敏感操作");
    }

    @Test
    void shouldGetUserDetailSuccessfully() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2);
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));

        User detail = userApplicationService.getUser(2L);

        assertThat(detail.getUsername()).isEqualTo("zhangsan");
    }

    @Test
    void shouldSyncUserToLdapSuccessfully() {
        User existing = buildUser(2L, "zhangsan", UserStatus.ENABLED, 2).toBuilder().ldapDn(null).build();
        Department department = Department.builder().id(1L).deptCode("D001").deptName("研发中心").sourceType(SourceType.MANUAL).status(1).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(department));
        when(ldapDirectoryService.existsByUid("zhangsan")).thenReturn(false);
        when(ldapDirectoryService.createUser(existing, "123456")).thenReturn("uid=zhangsan,ou=people,dc=corp,dc=local");
        when(ldapGroupService.createGroup("D001", "研发中心")).thenReturn("cn=D001_研发中心,ou=groups,dc=corp,dc=local");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User synced = userApplicationService.syncUserToLdap(2L, "admin");

        assertThat(synced.getLdapDn()).isEqualTo("uid=zhangsan,ou=people,dc=corp,dc=local");
        verify(permissionLevelRuleService).checkCanModifySensitiveUser("admin", existing);
        verify(ldapDirectoryService).createUser(existing, "123456");
        verify(ldapDirectoryService).enableUser("zhangsan");
        verify(ldapGroupService).syncUserGroups("zhangsan", List.of("D001"));
    }

    private User buildUser(Long id, String username, UserStatus status, Integer tokenVersion) {
        return User.builder()
            .id(id)
            .username(username)
            .realName(username)
            .email(username + "@corp.local")
            .mobile("13800000000")
            .employeeNo("E001")
            .deptCode("D001")
            .status(status)
            .sourceType(SourceType.MANUAL)
            .ldapDn("uid=" + username + ",ou=people,dc=corp,dc=local")
            .tokenVersion(tokenVersion)
            .roleCodes(Set.of("ADMIN"))
            .build();
    }
}
