package com.company.idm.application.sync.importplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportConflictCode;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportConflictImpactResolverTest {

    private final ImportJsonService jsonService = new ImportJsonService(new ObjectMapper());
    private final ImportConflictImpactResolver resolver = new ImportConflictImpactResolver(jsonService);

    @Test
    void linksEmployeeNumberBlockerToUserCreateWithDifferentUserId() {
        ChangeItem blocker = blocker(10L, TargetType.USER, "1941");
        ChangeItem create = userCreate(11L, "zhangsan", "1941");

        List<Long> result = resolver.resolveRelatedItemIds(blocker, List.of(blocker, create));

        assertThat(result).containsExactly(11L);
    }

    @Test
    void linksLdapUserBlockerByUserId() {
        ChangeItem blocker = blocker(10L, TargetType.LDAP_USER, "zhangsan");
        ChangeItem create = userCreate(11L, "zhangsan", "1941");

        List<Long> result = resolver.resolveRelatedItemIds(blocker, List.of(blocker, create));

        assertThat(result).containsExactly(11L);
    }

    @Test
    void linksRenamedEmployeeCreateAndExistingAccountResignBySnapshotIdentifiers() {
        ChangeItem blocker = blocker(10L, TargetType.USER, "zhangsan").toBuilder()
            .conflictCode(ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER)
            .beforeJson(jsonService.toJson(userSnapshot(270L, "yangchenyu", "1941", "杨辰宇")))
            .afterJson(jsonService.toJson(userSnapshot(270L, "yangchenyu", "1941", "张三")))
            .build();
        ChangeItem staleCreate = userCreate(11L, "zhangsan", "1941");
        ChangeItem staleResign = ChangeItem.builder()
            .id(12L)
            .targetType(TargetType.USER)
            .targetKey("yangchenyu")
            .changeType(ChangeType.RESIGN)
            .beforeJson(jsonService.toJson(userSnapshot(270L, "yangchenyu", "1941", "杨辰宇")))
            .afterJson(jsonService.toJson(userSnapshot(270L, "yangchenyu", "1941", "杨辰宇")))
            .enabled(true)
            .riskLevel(RiskLevel.HIGH)
            .status(ChangeItemStatus.PENDING)
            .build();

        List<Long> result = resolver.resolveRelatedItemIds(blocker, List.of(blocker, staleCreate, staleResign));

        assertThat(result).containsExactly(11L, 12L);
    }

    private ChangeItem blocker(Long id, TargetType targetType, String targetKey) {
        return ChangeItem.builder()
            .id(id)
            .targetType(targetType)
            .targetKey(targetKey)
            .changeType(ChangeType.CONFLICT)
            .enabled(false)
            .riskLevel(RiskLevel.BLOCKER)
            .status(ChangeItemStatus.PENDING)
            .build();
    }

    private ChangeItem userCreate(Long id, String userId, String employeeNo) {
        UserImportSnapshot snapshot = userSnapshot(null, userId, employeeNo, "张三");
        return ChangeItem.builder()
            .id(id)
            .targetType(TargetType.USER)
            .targetKey(userId)
            .changeType(ChangeType.CREATE)
            .afterJson(jsonService.toJson(snapshot))
            .enabled(true)
            .riskLevel(RiskLevel.MEDIUM)
            .status(ChangeItemStatus.PENDING)
            .build();
    }

    private UserImportSnapshot userSnapshot(Long id, String userId, String employeeNo, String realName) {
        return new UserImportSnapshot(
            id,
            userId,
            realName,
            null,
            null,
            employeeNo,
            "D001",
            null,
            null,
            null,
            null,
            List.of(),
            EmploymentStatus.ACTIVE
        );
    }
}
