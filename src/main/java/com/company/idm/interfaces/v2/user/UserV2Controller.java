package com.company.idm.interfaces.v2.user;

import com.company.idm.application.rbac.AssignUserRolesCommand;
import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.user.AdminResetPasswordCommand;
import com.company.idm.application.user.BatchDeleteUsersCommand;
import com.company.idm.application.user.BatchDeleteUsersResult;
import com.company.idm.application.user.ChangePasswordCommand;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.DeleteUserCommand;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.application.user.UpdateUserAccessCommand;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.application.user.UserReadScopeService;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.common.api.PageResult;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserPageQuery;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import com.company.idm.interfaces.user.AssignUserRolesRequest;
import com.company.idm.interfaces.user.BatchDeleteUsersRequest;
import com.company.idm.interfaces.user.BatchDeleteUsersResponse;
import com.company.idm.interfaces.user.ChangePasswordRequest;
import com.company.idm.interfaces.user.CreateUserRequest;
import com.company.idm.interfaces.user.UpdateUserAccessRequest;
import com.company.idm.interfaces.user.UpdateUserRequest;
import com.company.idm.interfaces.user.UserResponse;
import com.company.idm.interfaces.user.UserResponseAssembler;
import com.company.idm.interfaces.user.VerifyPasswordRequest;
import com.company.idm.interfaces.user.VerifyPasswordResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 用户管理接口，遵循阿里巴巴 Java 开发手册规范：
 * 精确/模糊分离、服务端分页、统一响应体、参数校验。
 */
@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserV2Controller {

    private static final int MAX_PAGE_SIZE = 100;

    /** 排序白名单：前端 orderBy 键 → 安全 SQL 列（未知回退主键排序） */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
        "id", "u.id",
        "userId", "u.user_id",
        "realName", "u.real_name",
        "employeeNo", "u.employee_no",
        "deptCode", "u.dept_code",
        "gmtCreate", "u.gmt_create",
        "gmtModified", "u.gmt_modified"
    );

    private final UserApplicationService userApplicationService;
    private final PasswordResetApplicationService passwordResetApplicationService;
    private final UserReadScopeService userReadScopeService;
    private final EffectivePermissionService effectivePermissionService;
    private final RbacApplicationService rbacApplicationService;
    private final DepartmentRepository departmentRepository;
    private final UserResponseAssembler userResponseAssembler;
    private final UserV2DepartmentRuleResolver departmentRuleResolver;

    /**
     * 用户分页查询（组合筛选 + 服务端分页 + 读取范围下推）。
     */
    @GetMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_READ', 'USER_READ_SELF_AND_SUBORDINATE_TREE')")
    public ApiResponseV2<PageResult<UserResponse>> page(
        @Valid @ModelAttribute UserV2PageQuery query,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        String username = principal.userId();
        User operator = userResponseAssembler.resolveOperator(username);
        List<User> organizationSnapshot = userResponseAssembler.repositorySnapshot();
        Set<String> operatorPermissionCodes = effectivePermissionService.resolve(principal);
        List<Department> departments = departmentRepository.findAll();

        UserPageQuery spec = toSpec(query, departments);
        PageResult<User> page = userApplicationService.pageUsersV2(spec, operator, organizationSnapshot, operatorPermissionCodes);
        List<UserResponse> items = page.items().stream()
            .map(user -> userResponseAssembler.toResponse(
                user,
                userResponseAssembler.canResetPassword(operator, user, organizationSnapshot, operatorPermissionCodes),
                departments
            ))
            .toList();
        return ApiResponseV2.ok(PageResult.of(items, page.total(), page.pageNum(), page.pageSize()));
    }

    /**
     * 按业务用户ID精确查询（第三方身份目录/单用户解析用，走唯一索引，返回单条）。
     */
    @GetMapping("/by-user-id/{userId}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_READ', 'USER_READ_SELF_AND_SUBORDINATE_TREE')")
    public ApiResponseV2<UserResponse> byUserId(
        @PathVariable String userId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        User operator = userResponseAssembler.resolveOperator(principal.userId());
        List<User> organizationSnapshot = userResponseAssembler.repositorySnapshot();
        Set<String> operatorPermissionCodes = effectivePermissionService.resolve(principal);
        User user = userApplicationService.getUserByUserId(userId);
        userReadScopeService.checkCanReadUser(operator, user, organizationSnapshot, operatorPermissionCodes);
        return ApiResponseV2.ok(userResponseAssembler.toResponse(
            user,
            userResponseAssembler.canResetPassword(operator, user, organizationSnapshot, operatorPermissionCodes),
            departmentRepository.findAll()
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_DETAIL')")
    public ApiResponseV2<UserResponse> detail(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        User operator = userResponseAssembler.resolveOperator(principal.userId());
        List<User> organizationSnapshot = userResponseAssembler.repositorySnapshot();
        Set<String> operatorPermissionCodes = effectivePermissionService.resolve(principal);
        User user = userApplicationService.getUser(id);
        userReadScopeService.checkCanReadUser(operator, user, organizationSnapshot, operatorPermissionCodes);
        return ApiResponseV2.ok(userResponseAssembler.toResponse(
            user,
            userResponseAssembler.canResetPassword(operator, user, organizationSnapshot, operatorPermissionCodes),
            departmentRepository.findAll()
        ));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_CREATE')")
    public ApiResponseV2<UserResponse> create(
        @Valid @RequestBody CreateUserRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        User user = userApplicationService.createUser(new CreateUserCommand(
            request.userId(),
            request.realName(),
            request.email(),
            request.intranetEmail(),
            request.mobile(),
            request.employeeNo(),
            request.deptCode(),
            request.partTimeDeptCodes(),
            request.accessAllowed(),
            request.roleIds(),
            username
        ));
        return ApiResponseV2.ok(userResponseAssembler.toResponse(user, false, departmentRepository.findAll()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_UPDATE')")
    public ApiResponseV2<UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        User user = userApplicationService.updateUser(new UpdateUserCommand(
            id,
            request.userId(),
            request.realName(),
            request.email(),
            request.intranetEmail(),
            request.mobile(),
            request.employeeNo(),
            request.deptCode(),
            request.partTimeDeptCodes(),
            username
        ));
        return ApiResponseV2.ok(userResponseAssembler.toResponse(user, false, departmentRepository.findAll()));
    }

    @PutMapping("/{id}/access")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_ACCESS_UPDATE')")
    public ApiResponseV2<Void> updateAccess(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserAccessRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        userApplicationService.updateAccess(new UpdateUserAccessCommand(id, request.accessAllowed(), username));
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_DELETE')")
    public ApiResponseV2<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        userApplicationService.deleteUser(new DeleteUserCommand(id, username));
        return ApiResponseV2.ok();
    }

    /**
     * 批量删除（V2 收紧为仅显式用户ID，禁止按查询条件批量删，消除误删面）。
     */
    @PostMapping("/batch-delete")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_BATCH_DELETE')")
    public ApiResponseV2<BatchDeleteUsersResponse> batchDelete(
        @Valid @RequestBody BatchDeleteUsersRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        if (request.userIds() == null || request.userIds().isEmpty()) {
            throw new BizException("PARAM_INVALID", "批量删除必须显式指定 userIds");
        }
        BatchDeleteUsersResult result = userApplicationService.batchDeleteUsers(new BatchDeleteUsersCommand(
            request.userIds(),
            null,
            null,
            null,
            username
        ));
        return ApiResponseV2.ok(new BatchDeleteUsersResponse(result.totalCount(), result.deletedCount()));
    }

    @PutMapping("/me/password")
    @PreAuthorize("@credentialAccessService.isSession(authentication)")
    public ApiResponseV2<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        userApplicationService.changePassword(new ChangePasswordCommand(
            username,
            request.verificationToken(),
            request.newPassword(),
            request.confirmPassword()
        ));
        return ApiResponseV2.ok();
    }

    @PostMapping("/me/password/verify")
    @PreAuthorize("@credentialAccessService.isSession(authentication)")
    public ApiResponseV2<VerifyPasswordResponse> verifyPassword(
        @Valid @RequestBody VerifyPasswordRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(new VerifyPasswordResponse(userApplicationService.verifyPassword(username, request.oldPassword())));
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_PASSWORD_RESET_ALL', 'USER_PASSWORD_RESET_DIRECT', 'USER_PASSWORD_RESET_TREE')")
    public ApiResponseV2<Void> resetPassword(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        passwordResetApplicationService.adminResetPassword(new AdminResetPasswordCommand(
            id,
            principal.userId(),
            ClientIpUtil.getClientIp(httpServletRequest),
            effectivePermissionService.resolve(principal)
        ));
        return ApiResponseV2.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_ROLE_ASSIGN')")
    public ApiResponseV2<Void> assignRoles(
        @PathVariable Long id,
        @Valid @RequestBody AssignUserRolesRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.assignUserRoles(new AssignUserRolesCommand(
            id,
            request.roleIds(),
            request.expectedRoleIds(),
            username
        ));
        return ApiResponseV2.ok();
    }

    @PostMapping("/{id}/sync-ldap")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'USER_SYNC_LDAP')")
    public ApiResponseV2<UserResponse> syncLdap(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(userResponseAssembler.toResponse(
            userApplicationService.syncUserToLdap(id, username),
            false,
            departmentRepository.findAll()
        ));
    }

    private UserPageQuery toSpec(UserV2PageQuery query, List<Department> departments) {
        long pageNum = Math.max(1, query.getPageNum());
        long pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, query.getPageSize()));
        String orderBy = resolveSortColumn(query.getOrderBy());
        String orderDir = "desc".equalsIgnoreCase(query.getOrderDir()) ? "desc" : "asc";
        return new UserPageQuery(
            trimToNull(query.getKeyword()),
            trimToNull(query.getUserId()),
            trimToNull(query.getRealName()),
            trimToNull(query.getEmployeeNo()),
            trimToNull(query.getMobile()),
            trimToNull(query.getEmail()),
            trimToNull(query.getIntranetEmail()),
            trimToNull(query.getJobTitle()),
            query.getAccessAllowed(),
            trimToNull(query.getEmploymentStatus()),
            trimToNull(query.getAccountStatus()),
            trimToNull(query.getSourceType()),
            query.getRoleCodes() == null || query.getRoleCodes().isEmpty() ? null : query.getRoleCodes(),
            parseDateTime(query.getCreatedStart()),
            parseEndDateTime(query.getCreatedEnd()),
            departmentRuleResolver.resolve(query.getDepartmentRules(), departments),
            pageNum,
            pageSize,
            orderBy,
            orderDir
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 排序白名单解析：null/未知字段回退主键排序（Map.of 的不可变 Map 对 null key 的 get 会抛 NPE，需先判空）。 */
    private String resolveSortColumn(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return "u.id";
        }
        return SORT_COLUMNS.getOrDefault(orderBy, "u.id");
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            if (v.length() == 10) {
                return LocalDate.parse(v).atStartOfDay();
            }
            return LocalDateTime.parse(normalizeDateTimeText(v));
        } catch (DateTimeParseException exception) {
            throw new BizException("PARAM_INVALID", "时间参数格式错误: " + v);
        }
    }

    /**
     * createdEnd 按闭区间处理：仅日期时返回次日零点，使 SQL 的 gmt_create &lt; end 覆盖当天整天。
     */
    private LocalDateTime parseEndDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            if (v.length() == 10) {
                return LocalDate.parse(v).plusDays(1).atStartOfDay();
            }
            return LocalDateTime.parse(normalizeDateTimeText(v));
        } catch (DateTimeParseException exception) {
            throw new BizException("PARAM_INVALID", "时间参数格式错误: " + v);
        }
    }

    private String normalizeDateTimeText(String value) {
        return value.replace('T', ' ').substring(0, 19);
    }
}
