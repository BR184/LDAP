package com.company.idm.test;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.user.BatchDeleteUsersResult;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.DeleteUserCommand;
import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UpdateUserStatusCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.application.user.UsernameGenerationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final String GENERATED_USERNAME = "zhangsane10001";

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
    private RoleRepository roleRepository;

    @Mock
    private UsernameGenerationService usernameGenerationService;

    @Mock
    private IntranetEmailGenerationService intranetEmailGenerationService;

    @Mock
    private AppLdapProperties ldapProperties;

    @InjectMocks
    private UserApplicationService userApplicationService;

    @BeforeEach
    void setUp() {
        lenient().when(roleRepository.findByCodes(anySet())).thenAnswer(invocation -> invocation.<Set<String>>getArgument(0).stream()
            .map(this::buildRoleByCode)
            .toList());
        lenient().when(roleRepository.findByIds(anyList())).thenAnswer(invocation -> invocation.<List<Long>>getArgument(0).stream()
            .map(this::buildRoleById)
            .toList());
        lenient().when(usernameGenerationService.generate(any(), any())).thenAnswer(invocation -> {
            String employeeNo = invocation.getArgument(1, String.class);
            if ("E10001".equals(employeeNo)) {
                return GENERATED_USERNAME;
            }
            if ("E10002".equals(employeeNo)) {
                return "zhangsane10002";
            }
            return "user" + employeeNo.toLowerCase();
        });
    }

    @Test
    void shouldCreateUserWithMainAndPartTimeDepartments() {
        CreateUserCommand command = new CreateUserCommand(
            "张三",
            "zhangsan@corp.local",
            "zhangsan@crowncad.com",
            "13900000000",
            "E10001",
            "D001",
            List.of("D002", "D003"),
            "123456",
            List.of(1L),
            "admin"
        );
        when(userRepository.findByEmployeeNo("E10001")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(GENERATED_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByIntranetEmail("zhangsan@crowncad.com")).thenReturn(Optional.empty());
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(dept("D001", "主部门")));
        when(departmentRepository.findByDeptCode("D002")).thenReturn(Optional.of(dept("D002", "兼职部门一")));
        when(departmentRepository.findByDeptCode("D003")).thenReturn(Optional.of(dept("D003", "兼职部门二")));
        when(ldapDirectoryService.existsByUid(GENERATED_USERNAME)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class).toBuilder().id(2L).build());
        when(ldapDirectoryService.createUser(any(User.class), any())).thenReturn("uid=" + GENERATED_USERNAME + ",ou=people,dc=corp,dc=local");
        when(ldapGroupService.createGroup(any(), any())).thenAnswer(invocation -> "cn=" + invocation.getArgument(0) + ",ou=groups,dc=corp,dc=local");

        User created = userApplicationService.createUser(command);

        assertThat(created.getDeptCode()).isEqualTo("D001");
        assertThat(created.getPartTimeDeptCodes()).containsExactly("D002", "D003");
        assertThat(created.getIntranetEmail()).isEqualTo("zhangsan@crowncad.com");
        verify(ldapGroupService).syncUserGroups(GENERATED_USERNAME, List.of("D001", "D002", "D003"));
    }

    @Test
    void shouldRejectWhenPartTimeDepartmentContainsMainDepartment() {
        CreateUserCommand command = new CreateUserCommand(
            "张三",
            "zhangsan@corp.local",
            "zhangsan@crowncad.com",
            "13900000000",
            "E10001",
            "D001",
            List.of("D001", "D002", "D002"),
            "123456",
            List.of(1L),
            "admin"
        );
        when(userRepository.findByEmployeeNo("E10001")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(GENERATED_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByIntranetEmail("zhangsan@crowncad.com")).thenReturn(Optional.empty());
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(dept("D001", "主部门")));
        when(departmentRepository.findByDeptCode("D002")).thenReturn(Optional.of(dept("D002", "兼职部门一")));
        when(ldapDirectoryService.existsByUid(GENERATED_USERNAME)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class).toBuilder().id(2L).build());
        when(ldapDirectoryService.createUser(any(User.class), any())).thenReturn("uid=" + GENERATED_USERNAME + ",ou=people,dc=corp,dc=local");
        when(ldapGroupService.createGroup(any(), any())).thenAnswer(invocation -> "cn=" + invocation.getArgument(0) + ",ou=groups,dc=corp,dc=local");

        User created = userApplicationService.createUser(command);

        assertThat(created.getPartTimeDeptCodes()).containsExactly("D002");
    }

    @Test
    void shouldUpdateUserWithNewPartTimeDepartments() {
        User existing = buildUser(2L, GENERATED_USERNAME, UserStatus.ENABLED, 0).toBuilder()
            .deptCode("D001")
            .partTimeDeptCodes(List.of("D002"))
            .employeeNo("E10001")
            .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmployeeNo("E10002")).thenReturn(Optional.empty());
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(dept("D001", "主部门")));
        when(departmentRepository.findByDeptCode("D003")).thenReturn(Optional.of(dept("D003", "兼职部门二")));
        when(ldapGroupService.createGroup(any(), any())).thenAnswer(invocation -> "cn=" + invocation.getArgument(0) + ",ou=groups,dc=corp,dc=local");

        User updated = userApplicationService.updateUser(new UpdateUserCommand(
            2L,
            "张三-更新",
            "new@corp.local",
            "zhangsan@crowncad.com",
            "13911111111",
            "E10002",
            "D001",
            List.of("D003"),
            "admin"
        ));

        assertThat(updated.getEmployeeNo()).isEqualTo("E10002");
        assertThat(updated.getPartTimeDeptCodes()).containsExactly("D003");
        verify(userRepository).updateProfile(argThat(user ->
            "E10002".equals(user.getEmployeeNo())
                && "zhangsan@crowncad.com".equals(user.getIntranetEmail())
                && user.getPartTimeDeptCodes().equals(List.of("D003"))
        ));
        verify(ldapGroupService).syncUserGroups(GENERATED_USERNAME, List.of("D001", "D003"));
    }

    @Test
    void shouldRejectWhenEmployeeNoDuplicatedOnUpdate() {
        User existing = buildUser(2L, GENERATED_USERNAME, UserStatus.ENABLED, 0).toBuilder().employeeNo("E10001").build();
        User otherUser = buildUser(3L, "lisi", UserStatus.ENABLED, 0).toBuilder().employeeNo("E20001").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmployeeNo("E20001")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> userApplicationService.updateUser(new UpdateUserCommand(
            2L,
            "张三",
            "new@corp.local",
            "zhangsan@crowncad.com",
            "13911111111",
            "E20001",
            "D001",
            List.of(),
            "admin"
        )))
            .isInstanceOf(BizException.class)
            .hasMessage("工号已存在");
    }

    @Test
    void shouldRejectWhenRoleDisabled() {
        when(roleRepository.findByIds(List.of(9L))).thenReturn(List.of(
            Role.builder().id(9L).roleCode("DISABLED_ROLE").roleName("禁用角色").permissionLevel(4).status(0).build()
        ));
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(dept("D001", "主部门")));

        assertThatThrownBy(() -> userApplicationService.createUser(new CreateUserCommand(
            "测试用户",
            "zhangsan@corp.local",
            "zhangsan@crowncad.com",
            "13900000000",
            "E10001",
            "D001",
            List.of(),
            "123456",
            List.of(9L),
            "admin"
        )))
            .isInstanceOf(BizException.class)
            .hasMessage("已禁用角色不允许分配");

        verify(ldapDirectoryService, never()).createUser(any(), any());
    }

    @Test
    void shouldBackfillManualUserIntranetEmailWhenUpdatingLegacyUser() {
        User existing = buildUser(2L, GENERATED_USERNAME, UserStatus.ENABLED, 0).toBuilder()
            .intranetEmail(null)
            .employeeNo("E10001")
            .roleCodes(Set.of("NORMAL_USER"))
            .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(dept("D001", "主部门")));
        when(userRepository.findByIntranetEmail("zhangsan@crowncad.com")).thenReturn(Optional.empty());
        when(ldapGroupService.createGroup(any(), any())).thenAnswer(invocation -> "cn=" + invocation.getArgument(0) + ",ou=groups,dc=corp,dc=local");

        User updated = userApplicationService.updateUser(new UpdateUserCommand(
            2L,
            "张三",
            "new@corp.local",
            "zhangsan@crowncad.com",
            "13911111111",
            "E10001",
            "D001",
            List.of(),
            "admin"
        ));

        assertThat(updated.getIntranetEmail()).isEqualTo("zhangsan@crowncad.com");
        verify(userRepository).updateProfile(argThat(user ->
            "zhangsan@crowncad.com".equals(user.getIntranetEmail())
        ));
    }

    @Test
    void shouldRejectWhenManualUserIntranetEmailDuplicated() {
        User existingUser = buildUser(9L, "lisi1002", UserStatus.ENABLED, 0).toBuilder()
            .intranetEmail("shared@crowncad.com")
            .build();
        when(userRepository.findByIntranetEmail("shared@crowncad.com")).thenReturn(Optional.of(existingUser));
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(dept("D001", "主部门")));

        assertThatThrownBy(() -> userApplicationService.createUser(new CreateUserCommand(
            "张三",
            "zhangsan@corp.local",
            "shared@crowncad.com",
            "13900000000",
            "E10001",
            "D001",
            List.of(),
            "123456",
            List.of(1L),
            "admin"
        )))
            .isInstanceOf(BizException.class)
            .hasMessage("内网邮箱已存在");
    }

    @Test
    void shouldDisplayLastTwoDepartmentLevelsForUserList() {
        Department level1 = dept("D001", "研发中心", "/D001");
        Department level2 = dept("D002", "产品研发一部", "/D001/D002");
        Department level3 = dept("D003", "四组", "/D001/D002/D003");
        when(departmentRepository.findAll()).thenReturn(List.of(level1, level2, level3));
        when(userRepository.findByConditions(null, null, null)).thenReturn(List.of(
            buildUser(2L, "zhangsan1001", UserStatus.ENABLED, 0).toBuilder()
                .deptCode("D003")
                .roleCodes(Set.of("NORMAL_USER"))
                .build()
        ));

        List<User> users = userApplicationService.listUsers(null, null, null);

        assertThat(users).singleElement().satisfies(user ->
            assertThat(user.getDeptName()).isEqualTo("产品研发一部/四组")
        );
    }

    @Test
    void shouldDisplaySingleDepartmentNameWhenNoParentDepartmentExists() {
        when(departmentRepository.findAll()).thenReturn(List.of(
            dept("D100", "基础研发中心", "/D100")
        ));
        when(userRepository.findByConditions(null, null, null)).thenReturn(List.of(
            buildUser(3L, "lisi1002", UserStatus.ENABLED, 0).toBuilder()
                .deptCode("D100")
                .roleCodes(Set.of("NORMAL_USER"))
                .build()
        ));

        List<User> users = userApplicationService.listUsers(null, null, null);

        assertThat(users).singleElement().satisfies(user ->
            assertThat(user.getDeptName()).isEqualTo("基础研发中心")
        );
    }

    @Test
    void shouldDeleteUserWhenLdapUserAlreadyMissing() {
        User existing = buildUser(2L, GENERATED_USERNAME, UserStatus.DISABLED, 1).toBuilder()
            .roleCodes(Set.of("NORMAL_USER"))
            .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(ldapDirectoryService.existsByUid(GENERATED_USERNAME)).thenReturn(false);

        userApplicationService.deleteUser(new DeleteUserCommand(2L, "admin"));

        verify(ldapGroupService).removeUserFromAllGroups(GENERATED_USERNAME);
        verify(ldapDirectoryService, never()).deleteUser(GENERATED_USERNAME);
        verify(userRepository).removeAllRoles(2L);
        verify(userRepository).logicalDelete(2L, GENERATED_USERNAME + "__deleted__2", 2);
    }

    @Test
    void shouldWriteCompactAuditSummaryWhenBatchDeletingManyUsers() {
        List<User> users = java.util.stream.LongStream.rangeClosed(1, 200)
            .mapToObj(index -> User.builder()
                .id(index)
                .username("user" + index)
                .realName("user" + index)
                .status(UserStatus.DISABLED)
                .sourceType(SourceType.MANUAL)
                .tokenVersion(0)
                .roleCodes(Set.of("NORMAL_USER"))
                .build())
            .toList();
        for (User user : users) {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(ldapDirectoryService.existsByUid(user.getUsername())).thenReturn(false);
        }

        BatchDeleteUsersResult result = userApplicationService.batchDeleteUsers(
            new com.company.idm.application.user.BatchDeleteUsersCommand(
                users.stream().map(User::getId).toList(),
                "admin"
            )
        );

        assertThat(result.deletedCount()).isEqualTo(200);
        verify(auditLogRepository).save(argThat(log ->
            "USER_BATCH_DELETE".equals(log.getBizId())
                && log.getAfterJson() != null
                && log.getAfterJson().contains("\"totalCount\":200")
                && log.getAfterJson().contains("\"truncated\":true")
        ));
    }

    private Department dept(String deptCode, String deptName) {
        return Department.builder()
            .id(1L)
            .deptCode(deptCode)
            .deptName(deptName)
            .ancestorPath("/" + deptCode)
            .deptLevel(1)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
    }

    private Department dept(String deptCode, String deptName, String ancestorPath) {
        return Department.builder()
            .id(1L)
            .deptCode(deptCode)
            .deptName(deptName)
            .ancestorPath(ancestorPath)
            .deptLevel(1)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
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
            .deptName("主部门")
            .status(status)
            .sourceType(SourceType.MANUAL)
            .ldapDn("uid=" + username + ",ou=people,dc=corp,dc=local")
            .tokenVersion(tokenVersion)
            .roleCodes(Set.of("ADMIN"))
            .build();
    }

    private Role buildRoleByCode(String roleCode) {
        return switch (roleCode) {
            case "SUPER_ADMIN" -> Role.builder().roleCode(roleCode).roleName("超级管理员").permissionLevel(1).status(1).builtIn(1).build();
            case "ADMIN" -> Role.builder().roleCode(roleCode).roleName("管理员").permissionLevel(2).status(1).builtIn(1).build();
            case "NORMAL_USER" -> Role.builder().roleCode(roleCode).roleName("普通用户").permissionLevel(3).status(1).builtIn(1).build();
            default -> Role.builder().roleCode(roleCode).roleName(roleCode).permissionLevel(4).status(1).builtIn(0).build();
        };
    }

    private Role buildRoleById(Long roleId) {
        if (roleId == null) {
            return Role.builder().roleCode("UNKNOWN").roleName("UNKNOWN").permissionLevel(4).status(1).builtIn(0).build();
        }
        return switch (roleId.intValue()) {
            case 1 -> Role.builder().id(roleId).roleCode("NORMAL_USER").roleName("普通用户").permissionLevel(3).status(1).builtIn(1).build();
            case 2 -> Role.builder().id(roleId).roleCode("ADMIN").roleName("管理员").permissionLevel(2).status(1).builtIn(1).build();
            default -> Role.builder().id(roleId).roleCode("ROLE_" + roleId).roleName("ROLE_" + roleId).permissionLevel(4).status(1).builtIn(0).build();
        };
    }
}
