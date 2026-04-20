package com.company.idm.test;

import com.company.idm.application.department.CreateDepartmentCommand;
import com.company.idm.application.department.DeleteDepartmentCommand;
import com.company.idm.application.department.DepartmentApplicationService;
import com.company.idm.application.department.UpdateDepartmentCommand;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证部门应用服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DepartmentApplicationServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LdapGroupService ldapGroupService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PermissionLevelRuleService permissionLevelRuleService;

    @InjectMocks
    private DepartmentApplicationService departmentApplicationService;

    @Test
    void shouldCreateDepartmentSuccessfully() {
        Department parent = Department.builder()
            .id(1L)
            .deptCode("D001")
            .deptName("研发中心")
            .ancestorPath("/D001")
            .deptLevel(1)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findByDeptCode("D100")).thenReturn(Optional.empty());
        when(departmentRepository.findByDeptCode("D001")).thenReturn(Optional.of(parent));
        when(departmentRepository.save(any(Department.class)))
            .thenReturn(
                Department.builder()
                    .id(2L)
                    .deptCode("D100")
                    .deptName("平台研发部")
                    .parentDeptCode("D001")
                    .ancestorPath("/D001/D100")
                    .deptLevel(2)
                    .sourceType(SourceType.MANUAL)
                    .status(1)
                    .build(),
                Department.builder()
                    .id(2L)
                    .deptCode("D100")
                    .deptName("平台研发部")
                    .parentDeptCode("D001")
                    .ancestorPath("/D001/D100")
                    .deptLevel(2)
                    .sourceType(SourceType.MANUAL)
                    .ldapDn("cn=D100_平台研发部,ou=groups,dc=corp,dc=local")
                    .status(1)
                    .build()
            );
        when(ldapGroupService.createGroup("D100", "平台研发部"))
            .thenReturn("cn=D100_平台研发部,ou=groups,dc=corp,dc=local");

        Department department = departmentApplicationService.createDepartment(
            new CreateDepartmentCommand("D100", "平台研发部", "D001", "ou_xxx"),
            "admin"
        );

        assertThat(department.getAncestorPath()).isEqualTo("/D001/D100");
        assertThat(department.getDeptLevel()).isEqualTo(2);
        assertThat(department.getLdapDn()).isEqualTo("cn=D100_平台研发部,ou=groups,dc=corp,dc=local");
        verify(permissionLevelRuleService).checkCanManageDepartment("admin");
    }

    @Test
    void shouldUpdateDepartmentAndRefreshDescendantPath() {
        Department current = Department.builder()
            .id(2L)
            .deptCode("D100")
            .deptName("平台研发部")
            .parentDeptCode("D001")
            .ancestorPath("/D001/D100")
            .deptLevel(2)
            .sourceType(SourceType.MANUAL)
            .ldapDn("cn=D100_平台研发部,ou=groups,dc=corp,dc=local")
            .status(1)
            .build();
        Department newParent = Department.builder()
            .id(3L)
            .deptCode("D002")
            .deptName("技术中心")
            .ancestorPath("/D002")
            .deptLevel(1)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        Department child = Department.builder()
            .id(4L)
            .deptCode("D101")
            .deptName("后端组")
            .parentDeptCode("D100")
            .ancestorPath("/D001/D100/D101")
            .deptLevel(3)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findByDeptCode("D100")).thenReturn(Optional.of(current));
        when(departmentRepository.findByDeptCode("D002")).thenReturn(Optional.of(newParent));
        when(departmentRepository.findAll()).thenReturn(List.of(current, newParent, child));
        when(ldapGroupService.updateGroup("D100", "平台工程部"))
            .thenReturn("cn=D100_平台工程部,ou=groups,dc=corp,dc=local");
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Department updated = departmentApplicationService.updateDepartment(
            new UpdateDepartmentCommand("D100", "平台工程部", "D002", 1),
            "admin"
        );

        assertThat(updated.getAncestorPath()).isEqualTo("/D002/D100");
        assertThat(updated.getDeptLevel()).isEqualTo(2);
        assertThat(updated.getLdapDn()).isEqualTo("cn=D100_平台工程部,ou=groups,dc=corp,dc=local");
        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository, times(2)).save(captor.capture());
        List<Department> savedDepartments = captor.getAllValues();
        assertThat(savedDepartments.get(0).getDeptCode()).isEqualTo("D100");
        assertThat(savedDepartments.get(0).getAncestorPath()).isEqualTo("/D002/D100");
        assertThat(savedDepartments.get(1).getDeptCode()).isEqualTo("D101");
        assertThat(savedDepartments.get(1).getAncestorPath()).isEqualTo("/D002/D100/D101");
        assertThat(savedDepartments.get(1).getDeptLevel()).isEqualTo(3);
    }

    @Test
    void shouldRejectWhenParentIsDescendant() {
        Department current = Department.builder()
            .id(2L)
            .deptCode("D100")
            .deptName("平台研发部")
            .parentDeptCode("D001")
            .ancestorPath("/D001/D100")
            .deptLevel(2)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        Department descendant = Department.builder()
            .id(4L)
            .deptCode("D101")
            .deptName("后端组")
            .parentDeptCode("D100")
            .ancestorPath("/D001/D100/D101")
            .deptLevel(3)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findByDeptCode("D100")).thenReturn(Optional.of(current));
        when(departmentRepository.findByDeptCode("D101")).thenReturn(Optional.of(descendant));

        assertThatThrownBy(() -> departmentApplicationService.updateDepartment(
            new UpdateDepartmentCommand("D100", "平台研发部", "D101", 1),
            "admin"
        ))
            .isInstanceOf(BizException.class)
            .hasMessage("父部门不能选择当前部门的下级节点");
    }

    @Test
    void shouldRejectDeleteWhenDepartmentInUse() {
        Department current = Department.builder()
            .id(2L)
            .deptCode("D100")
            .deptName("平台研发部")
            .ancestorPath("/D001/D100")
            .deptLevel(2)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findByDeptCode("D100")).thenReturn(Optional.of(current));
        when(departmentRepository.existsChildren("D100")).thenReturn(false);
        when(userRepository.existsDeptBinding("D100")).thenReturn(true);

        assertThatThrownBy(() -> departmentApplicationService.deleteDepartment(
            new DeleteDepartmentCommand("D100", "admin")
        ))
            .isInstanceOf(BizException.class)
            .hasMessage("当前部门已绑定用户，不能直接删除");
    }

    @Test
    void shouldDeleteDepartmentSuccessfully() {
        Department current = Department.builder()
            .id(2L)
            .deptCode("D100")
            .deptName("平台研发部")
            .ancestorPath("/D001/D100")
            .deptLevel(2)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findByDeptCode("D100")).thenReturn(Optional.of(current));
        when(departmentRepository.existsChildren("D100")).thenReturn(false);
        when(userRepository.existsDeptBinding("D100")).thenReturn(false);

        departmentApplicationService.deleteDepartment(new DeleteDepartmentCommand("D100", "admin"));

        verify(permissionLevelRuleService).checkCanManageDepartment("admin");
        verify(ldapGroupService).deleteGroup("D100");
        verify(departmentRepository).deleteByDeptCode("D100");
    }

    @Test
    void shouldSyncDepartmentToLdapSuccessfully() {
        Department current = Department.builder()
            .id(2L)
            .deptCode("D100")
            .deptName("平台研发部")
            .ancestorPath("/D001/D100")
            .deptLevel(2)
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findByDeptCode("D100")).thenReturn(Optional.of(current));
        when(ldapGroupService.existsGroup("D100")).thenReturn(false);
        when(ldapGroupService.createGroup("D100", "平台研发部")).thenReturn("cn=D100_平台研发部,ou=groups,dc=corp,dc=local");
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAll()).thenReturn(List.of(
            com.company.idm.domain.user.User.builder().username("zhangsan").deptCode("D100").build(),
            com.company.idm.domain.user.User.builder().username("lisi").deptCode("D001").build()
        ));

        Department synced = departmentApplicationService.syncDepartmentToLdap("D100", "admin");

        assertThat(synced.getLdapDn()).isEqualTo("cn=D100_平台研发部,ou=groups,dc=corp,dc=local");
        verify(permissionLevelRuleService).checkCanManageDepartment("admin");
        verify(ldapGroupService).addUserToGroup("zhangsan", "D100");
        verify(ldapGroupService).removeUserFromGroup("lisi", "D100");
    }
}
