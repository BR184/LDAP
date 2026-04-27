package com.company.idm.test;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.feishu.FeishuImportAlignmentService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuImportAlignmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Mock
    private LdapGroupService ldapGroupService;

    @Test
    void shouldCleanupMissingFeishuUsersOnly() {
        FeishuImportAlignmentService service = buildService();
        User retained = buildUser(1L, "zhangsan", "u001", "D100", SourceType.FEISHU);
        User ghost = buildUser(2L, "lisi", "u002", "D200", SourceType.FEISHU);
        User manual = buildUser(3L, "wangwu", null, "D300", SourceType.MANUAL);
        when(userRepository.findAll()).thenReturn(List.of(retained, ghost, manual));

        List<SyncDiffPayload> diffs = service.cleanupMissingUsers(Set.of("u001"));

        assertThat(diffs).singleElement().satisfies(diff -> {
            assertThat(diff.targetKey()).isEqualTo("lisi");
            assertThat(diff.diffType()).isEqualTo(SyncDiffType.MISSING_IN_SOURCE);
        });
        verify(ldapGroupService).removeUserFromAllGroups("lisi");
        verify(ldapDirectoryService).deleteUser("lisi");
        verify(userRepository).removeAllRoles(2L);
        verify(userRepository).logicalDelete(2L, "lisi__deleted__2", 1);
        verify(userRepository, never()).logicalDelete(1L, "zhangsan__deleted__1", 1);
    }

    @Test
    void shouldCleanupMissingDepartmentsFromLeafToRoot() {
        FeishuImportAlignmentService service = buildService();
        Department retained = buildDepartment(1L, "D100", "ou_root", 1, SourceType.FEISHU);
        Department parentGhost = buildDepartment(2L, "D200", "ou_parent", 2, SourceType.FEISHU);
        Department childGhost = buildDepartment(3L, "D201", "ou_child", 3, SourceType.FEISHU);
        Department manual = buildDepartment(4L, "D300", null, 1, SourceType.MANUAL);
        when(departmentRepository.findAll()).thenReturn(List.of(retained, parentGhost, childGhost, manual));
        when(userRepository.existsDeptBinding("D201")).thenReturn(false);
        when(userRepository.existsDeptBinding("D200")).thenReturn(false);

        List<SyncDiffPayload> diffs = service.cleanupMissingDepartments(Set.of("ou_root"));

        assertThat(diffs).hasSize(2);
        InOrder inOrder = inOrder(ldapGroupService, departmentRepository);
        inOrder.verify(ldapGroupService).deleteGroup("D201");
        inOrder.verify(departmentRepository).deleteByDeptCode("D201");
        inOrder.verify(ldapGroupService).deleteGroup("D200");
        inOrder.verify(departmentRepository).deleteByDeptCode("D200");
    }

    @Test
    void shouldRejectDepartmentCleanupWhenBindingsRemain() {
        FeishuImportAlignmentService service = buildService();
        Department ghost = buildDepartment(2L, "D200", "ou_parent", 2, SourceType.FEISHU);
        when(departmentRepository.findAll()).thenReturn(List.of(ghost));
        when(userRepository.existsDeptBinding("D200")).thenReturn(true);

        assertThatThrownBy(() -> service.cleanupMissingDepartments(Set.of()))
            .hasMessageContaining("D200");
    }

    private FeishuImportAlignmentService buildService() {
        return new FeishuImportAlignmentService(userRepository, departmentRepository, ldapDirectoryService, ldapGroupService);
    }

    private User buildUser(Long id, String username, String externalId, String deptCode, SourceType sourceType) {
        return User.builder()
            .id(id)
            .username(username)
            .realName(username)
            .deptCode(deptCode)
            .status(UserStatus.ENABLED)
            .sourceType(sourceType)
            .externalId(externalId)
            .tokenVersion(0)
            .roleCodes(Set.of())
            .build();
    }

    private Department buildDepartment(Long id, String deptCode, String externalId, int deptLevel, SourceType sourceType) {
        return Department.builder()
            .id(id)
            .deptCode(deptCode)
            .deptName(deptCode)
            .deptLevel(deptLevel)
            .sourceType(sourceType)
            .externalId(externalId)
            .status(1)
            .build();
    }
}
