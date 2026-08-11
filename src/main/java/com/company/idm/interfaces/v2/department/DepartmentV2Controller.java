package com.company.idm.interfaces.v2.department;

import com.company.idm.application.department.CreateDepartmentCommand;
import com.company.idm.application.department.DeleteDepartmentCommand;
import com.company.idm.application.department.DepartmentApplicationService;
import com.company.idm.application.department.UpdateDepartmentCommand;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.department.Department;
import com.company.idm.interfaces.department.CreateDepartmentRequest;
import com.company.idm.interfaces.department.DepartmentResponse;
import com.company.idm.interfaces.department.DepartmentTreeNodeResponse;
import com.company.idm.interfaces.department.UpdateDepartmentRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 部门管理接口。
 */
@RestController
@RequestMapping("/api/v2/departments")
@RequiredArgsConstructor
public class DepartmentV2Controller {

    private final DepartmentApplicationService departmentApplicationService;

    @GetMapping("/tree")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'DEPT_TREE')")
    public ApiResponseV2<List<DepartmentTreeNodeResponse>> tree() {
        return ApiResponseV2.ok(buildTree(departmentApplicationService.listDepartments()));
    }

    @GetMapping("/{deptCode}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'DEPT_DETAIL')")
    public ApiResponseV2<DepartmentResponse> detail(@PathVariable String deptCode) {
        return ApiResponseV2.ok(toResponse(departmentApplicationService.getDepartment(deptCode)));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'DEPT_CREATE')")
    public ApiResponseV2<DepartmentResponse> create(
        @Valid @RequestBody CreateDepartmentRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        Department department = departmentApplicationService.createDepartment(new CreateDepartmentCommand(
            request.deptCode(),
            request.deptName(),
            request.parentDeptCode(),
            request.externalId()
        ), username);
        return ApiResponseV2.ok(toResponse(department));
    }

    @PutMapping("/{deptCode}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'DEPT_UPDATE')")
    public ApiResponseV2<DepartmentResponse> update(
        @PathVariable String deptCode,
        @Valid @RequestBody UpdateDepartmentRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        Department department = departmentApplicationService.updateDepartment(new UpdateDepartmentCommand(
            deptCode,
            request.deptName(),
            request.parentDeptCode(),
            request.status()
        ), username);
        return ApiResponseV2.ok(toResponse(department));
    }

    @DeleteMapping("/{deptCode}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'DEPT_DELETE')")
    public ApiResponseV2<Void> delete(
        @PathVariable String deptCode,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        departmentApplicationService.deleteDepartment(new DeleteDepartmentCommand(deptCode, username));
        return ApiResponseV2.ok();
    }

    @PostMapping("/{deptCode}/sync-ldap")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'DEPT_SYNC_LDAP')")
    public ApiResponseV2<DepartmentResponse> syncLdap(
        @PathVariable String deptCode,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(toResponse(departmentApplicationService.syncDepartmentToLdap(deptCode, username)));
    }

    private List<DepartmentTreeNodeResponse> buildTree(List<Department> departments) {
        Map<String, DepartmentTreeNodeResponse> index = new LinkedHashMap<>();
        for (Department department : departments) {
            index.put(department.getDeptCode(), DepartmentTreeNodeResponse.create(
                department.getDeptCode(),
                department.getDeptName(),
                department.getParentDeptCode(),
                department.getAncestorPath(),
                department.getDeptLevel(),
                department.getStatus()
            ));
        }
        List<DepartmentTreeNodeResponse> roots = new ArrayList<>();
        for (DepartmentTreeNodeResponse node : index.values()) {
            if (node.parentDeptCode() == null || node.parentDeptCode().isBlank()) {
                roots.add(node);
                continue;
            }
            DepartmentTreeNodeResponse parent = index.get(node.parentDeptCode());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
            department.getId(),
            department.getDeptCode(),
            department.getDeptName(),
            department.getParentDeptCode(),
            department.getAncestorPath(),
            department.getDeptLevel(),
            department.getSourceType().name(),
            department.getExternalId(),
            department.getLdapDn(),
            department.getStatus()
        );
    }
}
