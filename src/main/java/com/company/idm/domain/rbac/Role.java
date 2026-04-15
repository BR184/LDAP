package com.company.idm.domain.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 角色领域实体，描述平台角色的基本属性。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    private Long id;
    private String roleCode;
    private String roleName;
    private Integer status;
    private String remark;
}

