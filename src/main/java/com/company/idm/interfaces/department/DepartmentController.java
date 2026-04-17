package com.company.idm.interfaces.department;

import com.company.idm.application.department.CreateDepartmentCommand;
import com.company.idm.application.department.DeleteDepartmentCommand;
import com.company.idm.application.department.DepartmentApplicationService;
import com.company.idm.application.department.UpdateDepartmentCommand;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.department.Department;
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
 * 提供部门树、详情和 CRUD 接口。
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentApplicationService departmentApplicationService;

    @GetMapping("/tree")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/departments/tree', 'GET')")
    public ApiResponse<List<DepartmentTreeNodeResponse>> tree() {
        return ApiResponse.success(buildTree(departmentApplicationService.listDepartments()));
    }

    @GetMapping("/{deptCode}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/departments/' + #deptCode, 'GET')")
    public ApiResponse<DepartmentResponse> detail(@PathVariable String deptCode) {
        return ApiResponse.success(toResponse(departmentApplicationService.getDepartment(deptCode)));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/departments', 'POST')")
    public ApiResponse<DepartmentResponse> create(
        @Valid @RequestBody CreateDepartmentRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        Department department = departmentApplicationService.createDepartment(new CreateDepartmentCommand(
            request.deptCode(),
            request.deptName(),
            request.parentDeptCode(),
            request.externalId()
        ), username);
        return ApiResponse.success(toResponse(department));
    }

    @PutMapping("/{deptCode}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/departments/' + #deptCode, 'PUT')")
    public ApiResponse<DepartmentResponse> update(
        @PathVariable String deptCode,
        @Valid @RequestBody UpdateDepartmentRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        Department department = departmentApplicationService.updateDepartment(new UpdateDepartmentCommand(
            deptCode,
            request.deptName(),
            request.parentDeptCode(),
            request.status()
        ), username);
        return ApiResponse.success(toResponse(department));
    }

    @DeleteMapping("/{deptCode}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/departments/' + #deptCode, 'DELETE')")
    public ApiResponse<Void> delete(
        @PathVariable String deptCode,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        departmentApplicationService.deleteDepartment(new DeleteDepartmentCommand(deptCode, username));
        return ApiResponse.success();
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
