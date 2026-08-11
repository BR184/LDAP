package com.company.idm.interfaces.user;

import com.company.idm.application.department.DepartmentDisplay;
import com.company.idm.application.department.DepartmentPathService;
import com.company.idm.application.user.PasswordResetAuthorizationService;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户响应组装器：V1/V2 用户接口共用同一套 UserResponse 映射，避免两处映射漂移。
 */
@Component
@RequiredArgsConstructor
public class UserResponseAssembler {

    private final DepartmentPathService departmentPathService;
    private final PasswordResetAuthorizationService passwordResetAuthorizationService;
    private final UserRepository userRepository;

    public UserResponse toResponse(User user, boolean canResetPassword, List<Department> departments) {
        List<DepartmentReferenceResponse> partTimeDepartments = toDepartmentReferences(
            user.getPartTimeDeptCodes(),
            departments
        );
        List<String> partTimeDeptCodes = partTimeDepartments.stream()
            .map(DepartmentReferenceResponse::deptCode)
            .toList();
        return new UserResponse(
            user.getId(),
            user.getUserId(),
            user.getRealName(),
            user.getEmail(),
            user.getIntranetEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptName(),
            user.getDeptCode(),
            user.getDepartmentPath(),
            user.getJobTitle(),
            user.getDirectLeaderRaw(),
            user.getLeaderRef(),
            user.getAccountStatus(),
            partTimeDeptCodes,
            partTimeDepartments.stream()
                .map(department -> department.deptName() == null || department.deptName().isBlank()
                    ? department.deptCode()
                    : department.deptName())
                .toList(),
            partTimeDepartments,
            user.getPermissionLevel(),
            user.isAccessAllowed(),
            user.getEmploymentStatus() == null ? null : user.getEmploymentStatus().name(),
            user.getLdapDn(),
            user.getRoleCodes(),
            canResetPassword
        );
    }

    public List<DepartmentReferenceResponse> toDepartmentReferences(List<String> departmentCodes, List<Department> departments) {
        if (departmentCodes == null || departmentCodes.isEmpty()) {
            return List.of();
        }
        return departmentCodes.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .distinct()
            .map(code -> {
                DepartmentDisplay display = departmentPathService.resolve(code, departments);
                return new DepartmentReferenceResponse(code, display.departmentName(), display.departmentPath());
            })
            .toList();
    }

    public User resolveOperator(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return userRepository.findByUserId(username).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public boolean canResetPassword(User operator, User target, List<User> allUsers, Set<String> operatorPermissionCodes) {
        return passwordResetAuthorizationService.evaluate(operator, target, allUsers, operatorPermissionCodes).allowed();
    }

    public List<User> repositorySnapshot() {
        return userRepository.findAll();
    }
}
