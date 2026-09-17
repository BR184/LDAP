package com.company.idm.interfaces.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.interfaces.v2.rolegroup.SubscriptionV2Controller;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class RoleGroupControllerAuthorizationTest {

    private static final String READ_OR_MANAGE =
        "@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_READ', 'ROLE_GROUP_MANAGE')";
    private static final String MANAGE =
        "@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')";
    private static final String MANAGE_AND_ASSIGN =
        "@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')"
            + " and @casbinAccessService.hasAny(authentication, 'ROLE_GROUP_USER_ASSIGN')";
    private static final String SESSION_AND_MANAGE =
        "@credentialAccessService.isSession(authentication)"
            + " and @casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')";

    @Test
    void queryOperationsAcceptReadOrManagePermission() {
        assertThat(RoleGroupController.class.getAnnotation(PreAuthorize.class)).isNull();

        List.of("list", "detail", "collaborators", "searchUsers", "roles", "roleMembers")
            .forEach(methodName -> assertExpression(RoleGroupController.class, methodName, READ_OR_MANAGE));
    }

    @Test
    void mutationOperationsRequireManagementPermission() {
        List.of(
            "create", "update", "delete", "saveCollaborator", "removeCollaborator",
            "createRole", "updateRole", "deleteRole"
        ).forEach(methodName -> assertExpression(RoleGroupController.class, methodName, MANAGE));
    }

    @Test
    void roleMemberAssignmentRequiresManagementAndAssignmentPermissions() {
        List.of("addRoleMembers", "removeRoleMember")
            .forEach(methodName -> assertExpression(RoleGroupController.class, methodName, MANAGE_AND_ASSIGN));
    }

    @Test
    void subscriptionManagementRequiresSessionAndManagementPermission() {
        assertThat(SubscriptionV2Controller.class.getAnnotation(PreAuthorize.class))
            .isNotNull()
            .extracting(PreAuthorize::value)
            .isEqualTo(SESSION_AND_MANAGE);
    }

    private void assertExpression(Class<?> controllerType, String methodName, String expected) {
        Method method = List.of(controllerType.getDeclaredMethods()).stream()
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        assertThat(method.getAnnotation(PreAuthorize.class))
            .as(controllerType.getSimpleName() + "." + methodName)
            .isNotNull()
            .extracting(PreAuthorize::value)
            .isEqualTo(expected);
    }
}
