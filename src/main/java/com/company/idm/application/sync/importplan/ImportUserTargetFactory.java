package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.user.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ImportUserTargetFactory {

    private final IntranetEmailGenerationService intranetEmailGenerationService;

    public ImportUserTargetFactory(IntranetEmailGenerationService intranetEmailGenerationService) {
        this.intranetEmailGenerationService = intranetEmailGenerationService;
    }

    public TargetContext createContext(
        Map<String, Department> departmentsByExternalId,
        List<FeishuUserPayload> importedUsers
    ) {
        Map<LeaderMatchKey, String> leaderRefs = new HashMap<>();
        for (FeishuUserPayload payload : importedUsers) {
            leaderRefs.put(new LeaderMatchKey(payload.realName(), payload.employeeNo()), payload.userId());
        }
        return new TargetContext(Map.copyOf(departmentsByExternalId), Map.copyOf(leaderRefs));
    }

    public User build(FeishuUserPayload payload, User existing, TargetContext context) {
        Department mainDepartment = context.departmentsByExternalId().get(payload.mainDepartmentExternalId());
        if (mainDepartment == null) {
            throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "用户主部门无法匹配");
        }
        List<String> partTimeDeptCodes = new ArrayList<>();
        if (payload.partTimeDepartmentExternalIds() != null) {
            for (String externalId : payload.partTimeDepartmentExternalIds()) {
                Department department = context.departmentsByExternalId().get(externalId);
                if (department == null) {
                    throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "用户兼职部门无法匹配");
                }
                partTimeDeptCodes.add(department.getDeptCode());
            }
        }
        EmploymentStatus employmentStatus = isInactive(payload.status())
            ? EmploymentStatus.RESIGNED
            : EmploymentStatus.ACTIVE;
        String userId = existing == null ? payload.userId() : existing.getUserId();
        return User.builder()
            .id(existing == null ? null : existing.getId())
            .userId(userId)
            .realName(payload.realName())
            .email(preserve(payload.email(), existing == null ? null : existing.getEmail()))
            .intranetEmail(resolveIntranetEmail(payload, existing))
            .mobile(preserve(payload.mobile(), existing == null ? null : existing.getMobile()))
            .employeeNo(payload.employeeNo())
            .deptCode(mainDepartment.getDeptCode())
            .jobTitle(blankToNull(payload.jobTitle()))
            .directLeaderRaw(blankToNull(payload.directLeaderRaw()))
            .leaderRef(resolveLeaderRef(payload, existing, context.leaderRefs()))
            .accountStatus(blankToNull(payload.accountStatus()))
            .partTimeDeptCodes(partTimeDeptCodes)
            .accessAllowed(existing == null || existing.isAccessAllowed())
            .employmentStatus(employmentStatus)
            .sourceType(existing == null ? SourceType.FEISHU : existing.getSourceType())
            .ldapDn(existing == null ? null : existing.getLdapDn())
            .tokenVersion(existing == null ? 0 : existing.getTokenVersion())
            .roleCodes(existing == null ? java.util.Set.of() : existing.getRoleCodes())
            .build();
    }

    private String resolveLeaderRef(
        FeishuUserPayload payload,
        User existing,
        Map<LeaderMatchKey, String> leaderRefs
    ) {
        String raw = payload.directLeaderRaw();
        if (raw == null || raw.isBlank()) {
            return existing == null ? null : existing.getLeaderRef();
        }
        String imported = leaderRefs.entrySet().stream()
            .filter(entry -> raw.trim().contains(entry.getKey().realName()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        return imported == null && existing != null ? existing.getLeaderRef() : imported;
    }

    private String resolveIntranetEmail(FeishuUserPayload payload, User existing) {
        if (existing != null && existing.getIntranetEmail() != null && !existing.getIntranetEmail().isBlank()) {
            return existing.getIntranetEmail();
        }
        return intranetEmailGenerationService.generate(payload.userId(), existing == null ? null : existing.getId());
    }

    private String preserve(String incoming, String existing) {
        return incoming == null || incoming.isBlank() ? existing : incoming;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isInactive(Integer status) {
        return status != null && status != 1;
    }

    public record TargetContext(
        Map<String, Department> departmentsByExternalId,
        Map<LeaderMatchKey, String> leaderRefs
    ) {
    }

    public record LeaderMatchKey(String realName, String employeeNo) {
    }
}
