package com.company.idm.test;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.sync.feishu.FeishuUserImportResult;
import com.company.idm.application.sync.feishu.FeishuUserImportService;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.application.user.UsernameGenerationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.feishu.FeishuDepartmentRemoteService;
import com.company.idm.infrastructure.feishu.FeishuUserRemoteService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuUserImportServiceTest {

    private static final String GENERATED_USERNAME = "zhangsane10001";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Mock
    private LdapGroupService ldapGroupService;

    @Mock
    private PolicyRefreshService policyRefreshService;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private FeishuDepartmentRemoteService departmentRemoteService;

    @Mock
    private FeishuUserRemoteService userRemoteService;

    @Mock
    private FeishuImportDocumentResolver importDocumentResolver;

    @Test
    void shouldPreviewNewUsersSuccessfully() {
        FeishuUserImportService service = buildService();
        when(departmentRepository.findAll()).thenReturn(List.of(
            dept("D100", "研发中心", "ou_root"),
            dept("D200", "效能平台", "ou_part")
        ));
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1),
            new FeishuDepartmentPayload("ou_part", "D200", "效能平台", null, 1, 2)
        ));
        when(userRemoteService.fetchUsers()).thenReturn(List.of(
            new FeishuUserPayload("u001", "zhangsan", "张三", "zhangsan@corp.local", "13900000000", "E10001", "ou_root", List.of("ou_part"), 1, 1)
        ));
        when(userRepository.findByExternalId("u001")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(GENERATED_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeNo("E10001")).thenReturn(Optional.empty());

        FeishuUserImportResult result = service.preview(new SyncRequestPayload(false, null, false, "admin", SyncTriggerMode.MANUAL, null));

        assertThat(result.newCount()).isEqualTo(1);
        assertThat(result.diffs()).hasSize(1);
        assertThat(result.diffs().get(0).diffType()).isEqualTo(SyncDiffType.MISSING_IN_MYSQL);
        assertThat(result.diffs().get(0).targetKey()).isEqualTo(GENERATED_USERNAME);
    }

    @Test
    void shouldExecuteUserAndSyncMainAndPartTimeDepartments() {
        FeishuUserImportService service = buildService();
        Role normalUser = Role.builder().id(2L).roleCode("NORMAL_USER").roleName("普通用户").permissionLevel(3).status(1).build();
        User firstSaved = User.builder()
            .id(10L)
            .username(GENERATED_USERNAME)
            .realName("张三")
            .email("zhangsan@corp.local")
            .mobile("13900000000")
            .employeeNo("E10001")
            .deptCode("D100")
            .partTimeDeptCodes(List.of("D200"))
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.FEISHU)
            .externalId("u001")
            .tokenVersion(0)
            .roleCodes(Set.of())
            .build();
        User secondSaved = firstSaved.toBuilder().ldapDn("uid=" + GENERATED_USERNAME + ",ou=people,dc=corp,dc=local").build();
        when(departmentRepository.findAll()).thenReturn(List.of(
            dept("D100", "研发中心", "ou_root"),
            dept("D200", "效能平台", "ou_part")
        ));
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1),
            new FeishuDepartmentPayload("ou_part", "D200", "效能平台", null, 1, 2)
        ));
        when(userRemoteService.fetchUsers()).thenReturn(List.of(
            new FeishuUserPayload("u001", "zhangsan", "张三", "zhangsan@corp.local", "13900000000", "E10001", "ou_root", List.of("ou_part"), 1, 1)
        ));
        when(userRepository.findByExternalId("u001")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(GENERATED_USERNAME)).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeNo("E10001")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("NORMAL_USER")).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any(User.class))).thenReturn(firstSaved, secondSaved);
        when(ldapDirectoryService.existsByUid(GENERATED_USERNAME)).thenReturn(false);
        when(ldapDirectoryService.createUser(any(User.class), any())).thenReturn("uid=" + GENERATED_USERNAME + ",ou=people,dc=corp,dc=local");
        when(ldapGroupService.createGroup(any(), any())).thenAnswer(invocation -> "cn=" + invocation.getArgument(0) + ",ou=groups,dc=corp,dc=local");

        FeishuUserImportResult result = service.execute(new SyncRequestPayload(false, null, false, "admin", SyncTriggerMode.MANUAL, null));

        assertThat(result.newCount()).isEqualTo(1);
        verify(passwordPolicyValidator).validate("123456");
        verify(ldapGroupService).syncUserGroups(GENERATED_USERNAME, List.of("D100", "D200"));
        verify(userRepository).assignRoles(10L, List.of(2L));
        verify(policyRefreshService).refresh();
    }

    @Test
    void shouldRejectWhenManualUserConflicts() {
        FeishuUserImportService service = buildService();
        User manualUser = User.builder()
            .id(1L)
            .username(GENERATED_USERNAME)
            .realName("手工用户")
            .employeeNo("E10001")
            .sourceType(SourceType.MANUAL)
            .status(UserStatus.ENABLED)
            .build();
        when(departmentRepository.findAll()).thenReturn(List.of(dept("D100", "研发中心", "ou_root")));
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1)
        ));
        when(userRemoteService.fetchUsers()).thenReturn(List.of(
            new FeishuUserPayload("u001", "zhangsan", "张三", null, null, "E10001", "ou_root", List.of(), 1, 1)
        ));
        when(userRepository.findByExternalId("u001")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(GENERATED_USERNAME)).thenReturn(Optional.of(manualUser));
        when(userRepository.findByEmployeeNo("E10001")).thenReturn(Optional.of(manualUser));

        assertThatThrownBy(() -> service.preview(new SyncRequestPayload(false, null, false, "admin", SyncTriggerMode.MANUAL, null)))
            .isInstanceOf(BizException.class)
            .hasMessage("飞书用户 employee_no 与手工用户冲突");
    }

    private Department dept(String deptCode, String deptName, String externalId) {
        return Department.builder()
            .id(1L)
            .deptCode(deptCode)
            .deptName(deptName)
            .externalId(externalId)
            .sourceType(SourceType.FEISHU)
            .status(1)
            .build();
    }

    private FeishuUserImportService buildService() {
        AppLdapProperties ldapProperties = new AppLdapProperties();
        ldapProperties.setBaseDn("dc=corp,dc=local");
        ldapProperties.setPeopleOu("ou=people");
        return new FeishuUserImportService(
            userRemoteService,
            departmentRemoteService,
            importDocumentResolver,
            userRepository,
            departmentRepository,
            roleRepository,
            ldapDirectoryService,
            ldapGroupService,
            policyRefreshService,
            passwordPolicyValidator,
            new UsernameGenerationService(),
            ldapProperties
        );
    }
}
