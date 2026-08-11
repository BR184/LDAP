package com.company.idm.interfaces.v2.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.application.user.UserReadScopeService;
import com.company.idm.common.api.PageResult;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserPageQuery;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import com.company.idm.interfaces.user.BatchDeleteUsersRequest;
import com.company.idm.interfaces.user.UserResponse;
import com.company.idm.interfaces.user.UserResponseAssembler;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserV2ControllerTest {

    private final UserApplicationService userApplicationService = mock(UserApplicationService.class);
    private final PasswordResetApplicationService passwordResetApplicationService = mock(PasswordResetApplicationService.class);
    private final UserReadScopeService userReadScopeService = mock(UserReadScopeService.class);
    private final EffectivePermissionService effectivePermissionService = mock(EffectivePermissionService.class);
    private final RbacApplicationService rbacApplicationService = mock(RbacApplicationService.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final UserResponseAssembler userResponseAssembler = mock(UserResponseAssembler.class);
    private final UserV2DepartmentRuleResolver departmentRuleResolver = mock(UserV2DepartmentRuleResolver.class);

    private final UserV2Controller controller = new UserV2Controller(
        userApplicationService,
        passwordResetApplicationService,
        userReadScopeService,
        effectivePermissionService,
        rbacApplicationService,
        departmentRepository,
        userResponseAssembler,
        departmentRuleResolver
    );

    @Test
    void pageReturnsServerSidePagedResponses() {
        User operator = user("admin", 1L);
        when(userResponseAssembler.resolveOperator("admin")).thenReturn(operator);
        when(userResponseAssembler.repositorySnapshot()).thenReturn(List.of());
        when(effectivePermissionService.resolve(principal())).thenReturn(Set.of("USER_READ"));
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(departmentRuleResolver.resolve(any(), anyList())).thenReturn(List.of());
        when(userApplicationService.pageUsersV2(any(), any(), anyList(), anySet()))
            .thenReturn(PageResult.of(List.of(user("employee", 2L)), 1L, 1L, 10L));
        when(userResponseAssembler.canResetPassword(any(), any(), anyList(), anySet())).thenReturn(false);
        when(userResponseAssembler.toResponse(any(), eq(false), anyList())).thenReturn(userResponse());

        UserV2PageQuery query = new UserV2PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setOrderBy("userId");
        query.setOrderDir("asc");

        PageResult<UserResponse> result = controller.page(query, principal()).data();

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);

        ArgumentCaptor<UserPageQuery> specCaptor = ArgumentCaptor.forClass(UserPageQuery.class);
        verify(userApplicationService).pageUsersV2(specCaptor.capture(), eq(operator), anyList(), anySet());
        assertThat(specCaptor.getValue().pageSize()).isEqualTo(10);
        assertThat(specCaptor.getValue().orderBy()).isEqualTo("u.user_id");
    }

    @Test
    void pageClampsPageSizeToMaxAndNormalizesOrder() {
        when(userResponseAssembler.resolveOperator("admin")).thenReturn(user("admin", 1L));
        when(userResponseAssembler.repositorySnapshot()).thenReturn(List.of());
        when(effectivePermissionService.resolve(principal())).thenReturn(Set.of("USER_READ"));
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(userApplicationService.pageUsersV2(any(), any(), anyList(), anySet()))
            .thenReturn(PageResult.empty(1L, 100L));
        when(userResponseAssembler.toResponse(any(), anyBoolean(), anyList())).thenReturn(userResponse());

        UserV2PageQuery query = new UserV2PageQuery();
        query.setPageNum(1);
        query.setPageSize(5000);
        query.setOrderBy("unknownColumn");

        controller.page(query, principal());

        ArgumentCaptor<UserPageQuery> specCaptor = ArgumentCaptor.forClass(UserPageQuery.class);
        verify(userApplicationService).pageUsersV2(specCaptor.capture(), any(), anyList(), anySet());
        assertThat(specCaptor.getValue().pageSize()).isEqualTo(100);
        assertThat(specCaptor.getValue().orderBy()).isEqualTo("u.id");
    }

    @Test
    void batchDeleteRejectsEmptyUserIds() {
        assertThatThrownBy(() -> controller.batchDelete(new BatchDeleteUsersRequest(List.of(), null, null, null), "admin"))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("PARAM_INVALID");
    }

    @Test
    void pageWithoutOrderByFallsBackToIdSortWithoutNpe() {
        when(userResponseAssembler.resolveOperator("admin")).thenReturn(user("admin", 1L));
        when(userResponseAssembler.repositorySnapshot()).thenReturn(List.of());
        when(effectivePermissionService.resolve(principal())).thenReturn(Set.of("USER_READ"));
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(departmentRuleResolver.resolve(any(), anyList())).thenReturn(List.of());
        when(userApplicationService.pageUsersV2(any(), any(), anyList(), anySet()))
            .thenReturn(PageResult.empty(1L, 10L));
        when(userResponseAssembler.toResponse(any(), anyBoolean(), anyList())).thenReturn(userResponse());

        // 前端默认不传 orderBy（null），必须回退默认排序而非抛 NPE
        controller.page(new UserV2PageQuery(), principal());

        ArgumentCaptor<UserPageQuery> specCaptor = ArgumentCaptor.forClass(UserPageQuery.class);
        verify(userApplicationService).pageUsersV2(specCaptor.capture(), any(), anyList(), anySet());
        assertThat(specCaptor.getValue().orderBy()).isEqualTo("u.id");
    }

    private User user(String userId, long id) {
        return User.builder()
            .id(id)
            .userId(userId)
            .realName(userId)
            .roleCodes(Set.of())
            .build();
    }

    private UserResponse userResponse() {
        return new UserResponse(
            2L, "employee", "员工", null, null, null, null, null, null, null,
            null, null, null, null, List.of(), List.of(), List.of(),
            30, true, "ACTIVE", null, Set.of(), false
        );
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
            1L, "admin", 0, Set.of(), CredentialType.SESSION, null, Set.of(), null,
            null, 1L
        );
    }
}
