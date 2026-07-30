package com.company.idm.application.sync.importplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.domain.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserImportSnapshotTest {

    @Test
    void preservesLocalAccessAcrossImportUpdateMergeAndRollbackProfiles() {
        User localUser = User.builder()
            .id(7L)
            .userId("zhangsan")
            .realName("张三")
            .deptCode("D001")
            .partTimeDeptCodes(List.of())
            .accessAllowed(false)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .sourceType(SourceType.FEISHU)
            .tokenVersion(4)
            .build();
        User importedProfile = localUser.toBuilder()
            .realName("张三（更新）")
            .deptCode("D002")
            .employmentStatus(EmploymentStatus.RESIGNED)
            .build();

        UserImportSnapshot snapshot = UserImportSnapshot.from(importedProfile);
        User restored = snapshot.applyTo(localUser, localUser.getIntranetEmail());

        assertThat(restored.isAccessAllowed()).isFalse();
        assertThat(restored.getTokenVersion()).isEqualTo(4);
        assertThat(restored.getRealName()).isEqualTo("张三（更新）");
        assertThat(restored.getDeptCode()).isEqualTo("D002");
        assertThat(restored.getEmploymentStatus()).isEqualTo(EmploymentStatus.RESIGNED);
    }
}
