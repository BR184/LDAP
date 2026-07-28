package com.company.idm.application.sync.importplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.sync.LeaderRoleDerivationService;
import com.company.idm.application.user.InitialPasswordPolicy;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportBatchRepository;
import com.company.idm.domain.sync.ImportBatchStatus;
import com.company.idm.domain.sync.ImportSourceType;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportExecutionApplicationServiceTest {

    @Mock
    private ImportBatchRepository importBatchRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private LdapDirectoryService ldapDirectoryService;
    @Mock
    private LdapGroupService ldapGroupService;
    @Mock
    private PolicyRefreshService policyRefreshService;
    @Mock
    private LeaderRoleDerivationService leaderRoleDerivationService;
    @Mock
    private ImportJsonService jsonService;

    private ImportExecutionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ImportExecutionApplicationService(
            importBatchRepository,
            departmentRepository,
            userRepository,
            roleRepository,
            ldapDirectoryService,
            ldapGroupService,
            policyRefreshService,
            leaderRoleDerivationService,
            jsonService,
            new InitialPasswordPolicy()
        );
    }

    @Test
    void synchronizesBaselineRoleForAllActiveUsersWhenLdapGroupSyncFails() {
        User importedUser = activeUser(10L, "employee-a");
        User existingManager = activeUser(11L, "manager-a");
        ChangeItem item = ChangeItem.builder()
            .id(100L)
            .targetType(TargetType.USER)
            .targetKey(importedUser.getUserId())
            .changeType(ChangeType.CREATE)
            .afterJson("user-json")
            .enabled(true)
            .riskLevel(RiskLevel.LOW)
            .status(ChangeItemStatus.PENDING)
            .build();
        ImportBatch batch = ImportBatch.builder()
            .id(1L)
            .sourceType(ImportSourceType.MANUAL_FILE)
            .status(ImportBatchStatus.CONFIRMED)
            .changeItems(List.of(item))
            .expiredAt(LocalDateTime.now().plusMinutes(10))
            .build();
        Role normalUserRole = Role.builder().id(2L).roleCode("NORMAL_USER").status(1).build();

        when(importBatchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jsonService.readUserSnapshot("user-json")).thenReturn(UserImportSnapshot.from(importedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ldapDirectoryService.createOrUpdateUser(any(User.class), eq("123456"))).thenReturn(null);
        doThrow(new IllegalStateException("department group is missing"))
            .when(ldapGroupService)
            .syncUserGroupsToExactState(eq(importedUser.getUserId()), any());
        when(userRepository.findActiveUsers()).thenReturn(List.of(importedUser, existingManager));
        when(roleRepository.findByCode("NORMAL_USER")).thenReturn(Optional.of(normalUserRole));

        ImportBatch result = service.executePlan(1L, "admin");

        assertThat(result.getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(item.getStatus()).isEqualTo(ChangeItemStatus.LDAP_FAILED);
        verify(userRepository).syncRoleBindings(2L, Set.of(10L, 11L));
        verify(ldapDirectoryService).createOrUpdateUser(any(User.class), eq("123456"));
        verify(policyRefreshService).refresh();
    }

    @Test
    void usesMobileAsInitialPasswordWhenImportCreatesLdapUser() {
        User importedUser = activeUser(10L, "employee-a").toBuilder()
            .mobile("13800138000")
            .build();
        ChangeItem item = ChangeItem.builder()
            .id(100L)
            .targetType(TargetType.USER)
            .targetKey(importedUser.getUserId())
            .changeType(ChangeType.CREATE)
            .afterJson("user-json")
            .enabled(true)
            .riskLevel(RiskLevel.LOW)
            .status(ChangeItemStatus.PENDING)
            .build();
        ImportBatch batch = ImportBatch.builder()
            .id(1L)
            .sourceType(ImportSourceType.MANUAL_FILE)
            .status(ImportBatchStatus.CONFIRMED)
            .changeItems(List.of(item))
            .expiredAt(LocalDateTime.now().plusMinutes(10))
            .build();
        Role normalUserRole = Role.builder().id(2L).roleCode("NORMAL_USER").status(1).build();

        when(importBatchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jsonService.readUserSnapshot("user-json")).thenReturn(UserImportSnapshot.from(importedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ldapDirectoryService.createOrUpdateUser(any(User.class), any())).thenReturn(null);
        when(userRepository.findActiveUsers()).thenReturn(List.of(importedUser));
        when(roleRepository.findByCode("NORMAL_USER")).thenReturn(Optional.of(normalUserRole));

        service.executePlan(1L, "admin");

        verify(ldapDirectoryService).createOrUpdateUser(any(User.class), eq("13800138000"));
    }

    private User activeUser(Long id, String userId) {
        return User.builder()
            .id(id)
            .userId(userId)
            .realName(userId)
            .deptCode("D001")
            .partTimeDeptCodes(List.of())
            .status(UserStatus.ENABLED)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .sourceType(SourceType.FEISHU)
            .tokenVersion(0)
            .build();
    }
}
