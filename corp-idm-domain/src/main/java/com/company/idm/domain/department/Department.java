package com.company.idm.domain.department;

import com.company.idm.common.enums.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    private Long id;
    private String deptCode;
    private String deptName;
    private String parentDeptCode;
    private SourceType sourceType;
    private String externalId;
    private Integer status;
}

