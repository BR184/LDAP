package com.company.idm.application.rolegroup;

import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 角色供给范围判定：MQ 路由、订阅上下文、完整快照与补偿增量共用同一判定，
 * 避免“一边按选集、一边按全组及全局角色”的范围不一致。
 *
 * <p>角色组订阅的范围严格等于该组当前全部角色（动态范围）：组内新增角色自动纳入，
 * 删除角色自动移出；页面可见的全局角色与其他组角色一律不纳入，防止越权导出。
 * 全局订阅保留既有语义（可见全部角色），其范围不因整组动态订阅的引入而改变。
 */
@Component
public class RoleSupplyScopePolicy {

    /**
     * 过滤出订阅主体可见的角色。
     *
     * @param subjectType 令牌主体类型
     * @param subjectId 令牌主体标识（角色组 ID）
     * @param roles 候选角色集合
     * @return 范围内的角色，保持稳定顺序
     */
    public List<Role> filterVisibleRoles(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        List<Role> roles
    ) {
        if (roles == null) {
            return List.of();
        }
        if (subjectType == PersonalAccessTokenSubjectType.GLOBAL) {
            return List.copyOf(roles);
        }
        if (subjectType != PersonalAccessTokenSubjectType.ROLE_GROUP || subjectId == null) {
            return List.of();
        }
        return roles.stream()
            .filter(role -> role.getRoleScope() == RoleScope.GROUP && subjectId.equals(role.getRoleGroupId()))
            .toList();
    }
}
