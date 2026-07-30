package com.company.idm.application.sync.importplan;

import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.domain.user.User;
import java.util.List;

public record UserImportSnapshot(
    Long id,
    String userId,
    String realName,
    String email,
    String mobile,
    String employeeNo,
    String deptCode,
    String jobTitle,
    String directLeaderRaw,
    String leaderRef,
    String accountStatus,
    List<String> partTimeDeptCodes,
    EmploymentStatus employmentStatus
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
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptCode(),
            user.getJobTitle(),
            user.getDirectLeaderRaw(),
            user.getLeaderRef(),
            user.getAccountStatus(),
            user.getPartTimeDeptCodes(),
            user.getEmploymentStatus()
        );
    }

    /**
     * Applies the fields owned by the personnel source to the current authoritative user.
     * Local access, LDAP and token fields intentionally remain on the current user.
     */
    public User applyTo(User currentUser, String intranetEmail) {
        User.UserBuilder builder = currentUser == null
            ? User.builder()
                .id(id)
                .userId(userId)
                .intranetEmail(intranetEmail)
                .accessAllowed(true)
                .sourceType(com.company.idm.common.enums.SourceType.FEISHU)
                .tokenVersion(0)
            : currentUser.toBuilder();
        return builder
            .realName(realName)
            .email(email)
            .mobile(mobile)
            .employeeNo(employeeNo)
            .deptCode(deptCode)
            .jobTitle(jobTitle)
            .directLeaderRaw(directLeaderRaw)
            .leaderRef(leaderRef)
            .accountStatus(accountStatus)
            .partTimeDeptCodes(partTimeDeptCodes == null ? List.of() : partTimeDeptCodes)
            .employmentStatus(employmentStatus)
            .build();
    }
}
