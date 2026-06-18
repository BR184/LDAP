package com.company.idm.application.sync.importplan;

import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.user.User;
import java.util.List;

public record UserImportSnapshot(
    Long id,
    String userId,
    String realName,
    String email,
    String intranetEmail,
    String mobile,
    String employeeNo,
    String deptCode,
    String jobTitle,
    String directLeaderRaw,
    String leaderRef,
    String accountStatus,
    List<String> partTimeDeptCodes,
    UserStatus status,
    EmploymentStatus employmentStatus,
    SourceType sourceType,
    String ldapDn,
    Integer tokenVersion
) {

    public static UserImportSnapshot from(User user) {
        if (user == null) {
            return null;
        }
        return new UserImportSnapshot(
            user.getId(),
            user.getUserId(),
            user.getRealName(),
            user.getEmail(),
            user.getIntranetEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptCode(),
            user.getJobTitle(),
            user.getDirectLeaderRaw(),
            user.getLeaderRef(),
            user.getAccountStatus(),
            user.getPartTimeDeptCodes(),
            user.getStatus(),
            user.getEmploymentStatus(),
            user.getSourceType(),
            user.getLdapDn(),
            user.getTokenVersion()
        );
    }

    public User toUser() {
        return User.builder()
            .id(id)
            .userId(userId)
            .realName(realName)
            .email(email)
            .intranetEmail(intranetEmail)
            .mobile(mobile)
            .employeeNo(employeeNo)
            .deptCode(deptCode)
            .jobTitle(jobTitle)
            .directLeaderRaw(directLeaderRaw)
            .leaderRef(leaderRef)
            .accountStatus(accountStatus)
            .partTimeDeptCodes(partTimeDeptCodes == null ? List.of() : partTimeDeptCodes)
            .status(status)
            .employmentStatus(employmentStatus)
            .sourceType(sourceType)
            .ldapDn(ldapDn)
            .tokenVersion(tokenVersion)
            .build();
    }
}
