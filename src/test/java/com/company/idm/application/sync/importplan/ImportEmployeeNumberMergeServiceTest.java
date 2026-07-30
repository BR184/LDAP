package com.company.idm.application.sync.importplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.application.sync.feishu.FeishuFullImportDocument;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportConflictCode;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImportEmployeeNumberMergeServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final FeishuImportDocumentResolver documentResolver = mock(FeishuImportDocumentResolver.class);
    private final ImportJsonService jsonService = new ImportJsonService(new ObjectMapper());
    private final ImportConflictImpactResolver impactResolver = new ImportConflictImpactResolver(jsonService);
    private final ImportUserTargetFactory userTargetFactory = new ImportUserTargetFactory(
        new IntranetEmailGenerationService(userRepository)
    );
    private final ImportConflictCandidateRecoveryService candidateRecoveryService = new ImportConflictCandidateRecoveryService(
        documentResolver,
        departmentRepository,
        userRepository,
        userTargetFactory,
        jsonService
    );
    private final ImportEmployeeNumberMergeService service = new ImportEmployeeNumberMergeService(
        userRepository,
        new ImportDiffPolicyService(jsonService),
        impactResolver,
        candidateRecoveryService,
        jsonService
    );

    @Test
    void preservesExistingIdentityWhilePreparingImportedProfileUpdates() {
        User existing = user(270L, "yangchenyu", "杨辰宇", "1941")
            .toBuilder()
            .accessAllowed(false)
            .intranetEmail("yangchenyu@crowncad.com")
            .ldapDn("uid=yangchenyu,ou=people,dc=corp,dc=local")
            .tokenVersion(7)
            .build();
        UserImportSnapshot incoming = UserImportSnapshot.from(user(null, "zhangsan", "张三", "1941"));
        ChangeItem conflict = ChangeItem.builder()
            .id(364L)
            .targetType(TargetType.USER)
            .targetKey("zhangsan")
            .changeType(ChangeType.CONFLICT)
            .conflictCode(ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER)
            .afterJson(jsonService.toJson(incoming))
            .enabled(false)
            .riskLevel(RiskLevel.BLOCKER)
            .status(ChangeItemStatus.PENDING)
            .build();
        ChangeItem staleCreate = ChangeItem.builder()
            .id(365L)
            .targetType(TargetType.USER)
            .targetKey("zhangsan")
            .changeType(ChangeType.CREATE)
            .afterJson(jsonService.toJson(incoming))
            .enabled(true)
            .riskLevel(RiskLevel.MEDIUM)
            .status(ChangeItemStatus.PENDING)
            .build();
        when(userRepository.findByEmployeeNo("1941")).thenReturn(Optional.of(existing));

        ImportEmployeeNumberMergeService.MergePreparation result = service.prepare(
            ImportBatch.builder().fileHash("unused").build(),
            conflict,
            List.of(conflict, staleCreate)
        );

        assertThat(result.relatedItemIds()).containsExactly(365L);
        assertThat(result.updateItems()).isNotEmpty();
        UserImportSnapshot target = jsonService.readUserSnapshot(result.updateItems().get(0).getAfterJson());
        assertThat(target.id()).isEqualTo(270L);
        assertThat(target.userId()).isEqualTo("yangchenyu");
        assertThat(target.realName()).isEqualTo("张三");
        assertThat(target.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(existing.isAccessAllowed()).isFalse();
    }

    @Test
    void restoresMissingCandidateSnapshotFromBatchSourceHash() {
        User existing = user(223L, "gaojianfeng", "高剑锋", "2680")
            .toBuilder()
            .intranetEmail("gaojianfeng@crowncad.com")
            .ldapDn("uid=gaojianfeng,ou=people,dc=corp,dc=local")
            .build();
        Department department = Department.builder()
            .id(10L)
            .deptCode("FD_FINANCE")
            .deptName("财务部")
            .externalId("finance-external-id")
            .ancestorPath("/ROOT/FD_FINANCE")
            .deptLevel(2)
            .sourceType(SourceType.FEISHU)
            .status(1)
            .build();
        FeishuUserPayload incoming = new FeishuUserPayload(
            "zhangsan",
            "张三",
            "zhangsan@crowncad.com",
            "19999999999",
            "2680",
            "资金管理",
            "刘敏2(+8615662661200)",
            "正常",
            "finance-external-id",
            List.of(),
            1,
            1
        );
        ChangeItem conflict = mergeConflict(441L, "zhangsan", null);
        ImportBatch batch = ImportBatch.builder().fileHash("source-sha256").changeItems(List.of(conflict)).build();
        when(documentResolver.resolveFullImportDocumentByHash("source-sha256"))
            .thenReturn(new FeishuFullImportDocument(List.of(), List.of(incoming)));
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(userRepository.findByEmployeeNo("2680")).thenReturn(Optional.of(existing));
        when(userRepository.findByUserId("zhangsan")).thenReturn(Optional.empty());

        ImportEmployeeNumberMergeService.MergePreparation result = service.prepare(batch, conflict, List.of(conflict));

        assertThat(conflict.getBeforeJson()).isNotBlank();
        assertThat(conflict.getAfterJson()).isNotBlank();
        assertThat(result.updateItems()).isNotEmpty();
        UserImportSnapshot target = jsonService.readUserSnapshot(result.updateItems().get(0).getAfterJson());
        assertThat(target.id()).isEqualTo(223L);
        assertThat(target.userId()).isEqualTo("gaojianfeng");
        assertThat(target.realName()).isEqualTo("张三");
        assertThat(target.mobile()).isEqualTo("19999999999");
        assertThat(target.deptCode()).isEqualTo("FD_FINANCE");
    }

    @Test
    void rejectsRecoveryWhenEmployeeNumberAppearsMoreThanOnceInSource() {
        User existing = user(223L, "gaojianfeng", "高剑锋", "2680");
        FeishuUserPayload incoming = new FeishuUserPayload(
            "zhangsan", "张三", null, null, "2680", "finance", 1, 1
        );
        FeishuUserPayload duplicate = new FeishuUserPayload(
            "lisi", "李四", null, null, "2680", "finance", 1, 2
        );
        ChangeItem conflict = mergeConflict(441L, "zhangsan", null);
        ImportBatch batch = ImportBatch.builder().fileHash("source-sha256").changeItems(List.of(conflict)).build();
        when(documentResolver.resolveFullImportDocumentByHash("source-sha256"))
            .thenReturn(new FeishuFullImportDocument(List.of(), List.of(incoming, duplicate)));
        when(userRepository.findByEmployeeNo("2680")).thenReturn(Optional.of(existing));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.prepare(batch, conflict, List.of(conflict)))
            .isInstanceOfSatisfying(BizException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("IMPORT_CONFLICT_CANDIDATE_AMBIGUOUS"));
    }

    private ChangeItem mergeConflict(Long id, String targetKey, String afterJson) {
        return ChangeItem.builder()
            .id(id)
            .targetType(TargetType.USER)
            .targetKey(targetKey)
            .changeType(ChangeType.CONFLICT)
            .conflictCode(ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER)
            .afterJson(afterJson)
            .enabled(false)
            .riskLevel(RiskLevel.BLOCKER)
            .status(ChangeItemStatus.PENDING)
            .build();
    }

    private User user(Long id, String userId, String realName, String employeeNo) {
        return User.builder()
            .id(id)
            .userId(userId)
            .realName(realName)
            .email(userId + "@example.com")
            .mobile("13800138000")
            .employeeNo(employeeNo)
            .deptCode("D001")
            .partTimeDeptCodes(List.of())
            .accessAllowed(true)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .sourceType(SourceType.FEISHU)
            .tokenVersion(0)
            .build();
    }
}
