package com.company.idm.application.sync.importplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportConflictCode;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportConflictDetectorTest {

    private final ImportConflictDetector detector = new ImportConflictDetector(
        mock(UserRepository.class),
        mock(LdapDirectoryService.class),
        mock(LdapGroupService.class)
    );

    @Test
    void duplicateEmployeeNumberBlocksEveryAffectedUserPayload() {
        FeishuUserPayload existingIdentity = user("yangchenyu", "1941");
        FeishuUserPayload conflictingIdentity = user("zhangsan", "1941");
        List<ChangeItem> conflicts = new ArrayList<>();

        ImportFileConflictIndex index = detector.detectFileLevelConflicts(
            List.of(),
            List.of(existingIdentity, conflictingIdentity),
            conflicts
        );

        assertThat(index.blocksUser(existingIdentity)).isTrue();
        assertThat(index.blocksUser(conflictingIdentity)).isTrue();
        assertThat(conflicts).singleElement().satisfies(item -> {
            assertThat(item.getChangeType()).isEqualTo(ChangeType.CONFLICT);
            assertThat(item.getRiskLevel()).isEqualTo(RiskLevel.BLOCKER);
            assertThat(item.getTargetKey()).isEqualTo("1941");
            assertThat(item.getConflictCode()).isEqualTo(ImportConflictCode.FILE_DUPLICATE_EMPLOYEE_NO);
        });
    }

    @Test
    void marksEmployeeNumberOwnedByAnotherUserAsMergeableConflictType() {
        FeishuUserPayload payload = user("zhangsan", "1941");
        User employeeOwner = User.builder().id(270L).userId("yangchenyu").employeeNo("1941").build();
        List<ChangeItem> conflicts = new ArrayList<>();

        detector.detectUserConflict(payload, null, employeeOwner, conflicts);

        assertThat(conflicts).singleElement().satisfies(item ->
            assertThat(item.getConflictCode()).isEqualTo(ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER));
    }

    @Test
    void uniqueIdentifiersDoNotBlockPayload() {
        FeishuUserPayload user = user("zhangsan", "1941");

        ImportFileConflictIndex index = detector.detectFileLevelConflicts(List.of(), List.of(user), new ArrayList<>());

        assertThat(index.blocksUser(user)).isFalse();
    }

    private FeishuUserPayload user(String userId, String employeeNo) {
        return new FeishuUserPayload(
            userId,
            userId,
            userId + "@crowncad.com",
            "13800138000",
            employeeNo,
            "department-1",
            1,
            1
        );
    }
}
