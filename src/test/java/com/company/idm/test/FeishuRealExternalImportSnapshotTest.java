package com.company.idm.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.boot.IdmBootApplication;
import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.ldap.LdapGroupSnapshot;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import com.company.idm.infrastructure.persistence.dataobject.DepartmentDO;
import com.company.idm.infrastructure.persistence.dataobject.UserDO;
import com.company.idm.infrastructure.persistence.mapper.DepartmentMapper;
import com.company.idm.infrastructure.persistence.mapper.UserMapper;
import com.company.idm.infrastructure.persistence.mapper.UserRoleMapper;
import com.company.idm.infrastructure.persistence.record.UserRoleBindingRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在真实 MySQL + OpenLDAP 环境下执行飞书文件导入，并导出真实环境快照。
 */
@SpringBootTest(
    classes = IdmBootApplication.class,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/corp_idm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.username=corp_idm",
        "spring.datasource.password=corp_idm",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "app.ldap.mode=spring",
        "app.ldap.url=ldap://127.0.0.1:3389",
        "app.ldap.bind-dn=cn=admin,dc=corp,dc=local",
        "app.ldap.bind-password=admin",
        "app.startup-check.enabled=true",
        "app.sync.feishu.file-import.enabled=true",
        "app.sync.feishu.file-import.root-dir=docs/feishu-import"
    }
)
class FeishuRealExternalImportSnapshotTest {

    private static final Path SNAPSHOT_ROOT = Path.of("target/feishu-real-external-import-snapshot");
    private static final Path MYSQL_SNAPSHOT_PATH = SNAPSHOT_ROOT.resolve("mysql-snapshot.json");
    private static final Path LDAP_LDIF_PATH = SNAPSHOT_ROOT.resolve("ldap-entries.ldif");
    private static final String DEFAULT_IMPORTED_PASSWORD = "123456";
    private static final String PEOPLE_OU = "ou=people";
    private static final String GROUPS_OU = "ou=groups";
    private static final String BASE_DN = "dc=corp,dc=local";
    private static final String PLACEHOLDER_MEMBER_DN = "uid=placeholder," + PEOPLE_OU + "," + BASE_DN;

    @Autowired
    private SyncApplicationService syncApplicationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppLdapProperties ldapProperties;

    @Autowired
    private LdapDirectoryService ldapDirectoryService;

    @Autowired
    private LdapGroupService ldapGroupService;

    @Test
    void shouldImportFeishuFilesIntoRealMysqlAndOpenLdap() throws Exception {
        JsonNode departmentJson = objectMapper.readTree(Files.readString(Path.of("docs/feishu-import/departments/demo.json")));
        JsonNode userJson = objectMapper.readTree(Files.readString(Path.of("docs/feishu-import/users/demo.json")));

        List<String> departmentExternalIds = toTextList(departmentJson, "externalId");
        List<String> departmentCodes = toTextList(departmentJson, "departmentCode");
        List<String> userExternalIds = toTextList(userJson, "externalId");
        List<String> usernames = toTextList(userJson, "username");

        cleanupImportedFixtures(departmentExternalIds, departmentCodes, userExternalIds, usernames);

        syncApplicationService.executeFeishuDepartmentFileImport(
            "departments/demo.json",
            false,
            "real-external-import-test",
            "admin",
            SyncTriggerMode.MANUAL
        );
        syncApplicationService.executeFeishuUserFileImport(
            "users/demo.json",
            false,
            "real-external-import-test",
            "admin",
            SyncTriggerMode.MANUAL
        );

        List<DepartmentDO> importedDepartments = departmentMapper.selectList(
            new LambdaQueryWrapper<DepartmentDO>()
                .in(DepartmentDO::getExternalId, departmentExternalIds)
                .orderByAsc(DepartmentDO::getDeptLevel)
                .orderByAsc(DepartmentDO::getId)
        );
        List<UserDO> importedUsers = userMapper.selectList(
            new LambdaQueryWrapper<UserDO>()
                .in(UserDO::getExternalId, userExternalIds)
                .eq(UserDO::getDeleted, 0)
                .orderByAsc(UserDO::getId)
        );

        assertThat(importedDepartments).hasSize(departmentExternalIds.size());
        assertThat(importedUsers).hasSize(userExternalIds.size());

        List<UserRoleBindingRecord> importedUserRoles = userRoleMapper.selectUserRoleBindings().stream()
            .filter(item -> usernames.contains(item.username()))
            .sorted(Comparator.comparing(UserRoleBindingRecord::username).thenComparing(UserRoleBindingRecord::roleCode))
            .toList();

        MysqlSnapshotReport mysqlSnapshot = new MysqlSnapshotReport(
            importedDepartments.stream().map(this::toMysqlDepartmentRecord).toList(),
            importedUsers.stream().map(this::toMysqlUserRecord).toList(),
            importedUserRoles.stream().map(item -> new MysqlUserRoleRecord(item.username(), item.roleCode())).toList()
        );

        String ldapLdif = buildLdif(importedDepartments, importedUsers);

        Files.createDirectories(SNAPSHOT_ROOT);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(MYSQL_SNAPSHOT_PATH.toFile(), mysqlSnapshot);
        Files.writeString(LDAP_LDIF_PATH, ldapLdif);

        assertThat(importedUserRoles).hasSize(usernames.size());
        assertThat(importedUserRoles).allSatisfy(item -> assertThat(item.roleCode()).isEqualTo("NORMAL_USER"));
        assertThat(importedUsers.stream().map(UserDO::getUsername).collect(Collectors.toSet()))
            .containsExactlyInAnyOrderElementsOf(usernames);
        assertThat(importedDepartments.stream().map(DepartmentDO::getDeptCode).collect(Collectors.toSet()))
            .containsExactlyInAnyOrderElementsOf(departmentCodes);
    }

    private List<String> toTextList(JsonNode arrayNode, String fieldName) {
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false)
            .map(node -> node.path(fieldName).asText())
            .toList();
    }

    private void cleanupImportedFixtures(
        List<String> departmentExternalIds,
        List<String> departmentCodes,
        List<String> userExternalIds,
        List<String> usernames
    ) {
        departmentCodes.forEach(ldapGroupService::deleteGroup);
        usernames.stream()
            .filter(ldapDirectoryService::existsByUid)
            .forEach(ldapDirectoryService::deleteUser);

        deleteByValues("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE external_id IN (%s))", userExternalIds);
        deleteByValues("DELETE FROM sys_user WHERE external_id IN (%s)", userExternalIds);
        deleteByValues("DELETE FROM sys_department WHERE external_id IN (%s)", departmentExternalIds);
    }

    private void deleteByValues(String sqlTemplate, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        jdbcTemplate.update(sqlTemplate.formatted(placeholders), values.toArray());
    }

    private MysqlDepartmentRecord toMysqlDepartmentRecord(DepartmentDO item) {
        return new MysqlDepartmentRecord(
            item.getId(),
            item.getDeptCode(),
            item.getDeptName(),
            item.getParentDeptCode(),
            item.getAncestorPath(),
            item.getDeptLevel(),
            item.getSourceType(),
            item.getExternalId(),
            item.getLdapDn(),
            item.getStatus(),
            item.getGmtCreate(),
            item.getGmtModified()
        );
    }

    private MysqlUserRecord toMysqlUserRecord(UserDO item) {
        return new MysqlUserRecord(
            item.getId(),
            item.getUsername(),
            item.getRealName(),
            item.getEmail(),
            item.getMobile(),
            item.getEmployeeNo(),
            item.getDeptCode(),
            item.getStatus(),
            item.getSourceType(),
            item.getExternalId(),
            item.getLdapDn(),
            item.getTokenVersion(),
            item.getDeleted(),
            item.getCreator(),
            item.getModifier(),
            item.getGmtCreate(),
            item.getGmtModified()
        );
    }

    private String buildLdif(List<DepartmentDO> departments, List<UserDO> users) {
        StringBuilder builder = new StringBuilder();
        for (UserDO user : users) {
            builder.append(renderUserEntry(user)).append(System.lineSeparator());
        }
        for (DepartmentDO department : departments) {
            builder.append(renderGroupEntry(department)).append(System.lineSeparator());
        }
        return builder.toString().trim() + System.lineSeparator();
    }

    private String renderUserEntry(UserDO user) {
        LdapUserSnapshot snapshot = ldapDirectoryService.findUserSnapshot(user.getUsername());
        StringBuilder builder = new StringBuilder();
        builder.append("dn: ").append(snapshot.getDn()).append(System.lineSeparator());
        builder.append("objectClass: inetOrgPerson").append(System.lineSeparator());
        builder.append("uid: ").append(user.getUsername()).append(System.lineSeparator());
        builder.append("cn: ").append(user.getRealName()).append(System.lineSeparator());
        builder.append("sn: ").append(user.getRealName()).append(System.lineSeparator());
        appendIfPresent(builder, "mail", snapshot.getEmail());
        appendIfPresent(builder, "mobile", snapshot.getMobile());
        appendIfPresent(builder, "employeeNumber", snapshot.getEmployeeNo());
        appendIfPresent(builder, "departmentNumber", snapshot.getDeptCode());
        appendIfPresent(builder, "employeeType", snapshot.getStatus());
        builder.append("# expected userPassword after import: ").append(DEFAULT_IMPORTED_PASSWORD).append(System.lineSeparator());
        return builder.toString();
    }

    private String renderGroupEntry(DepartmentDO department) {
        LdapGroupSnapshot snapshot = ldapGroupService.findGroupSnapshot(department.getDeptCode());
        List<String> memberDns = snapshot.getMembers().isEmpty()
            ? List.of(PLACEHOLDER_MEMBER_DN)
            : snapshot.getMembers().stream().map(this::buildUserDn).toList();

        StringBuilder builder = new StringBuilder();
        builder.append("dn: ").append(snapshot.getDn()).append(System.lineSeparator());
        builder.append("objectClass: groupOfNames").append(System.lineSeparator());
        builder.append("cn: ").append(department.getDeptCode()).append("_").append(department.getDeptName()).append(System.lineSeparator());
        builder.append("description: ").append(department.getDeptName()).append(System.lineSeparator());
        builder.append("businessCategory: ").append(department.getDeptCode()).append(System.lineSeparator());
        for (String memberDn : memberDns) {
            builder.append("member: ").append(memberDn).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append(key).append(": ").append(value).append(System.lineSeparator());
    }

    private String buildUserDn(String username) {
        return LdapDnHelper.buildUserDn(ldapProperties, username);
    }

    private record MysqlSnapshotReport(
        List<MysqlDepartmentRecord> sysDepartment,
        List<MysqlUserRecord> sysUser,
        List<MysqlUserRoleRecord> sysUserRole
    ) {
    }

    private record MysqlDepartmentRecord(
        Long id,
        String deptCode,
        String deptName,
        String parentDeptCode,
        String ancestorPath,
        Integer deptLevel,
        String sourceType,
        String externalId,
        String ldapDn,
        Integer status,
        Object gmtCreate,
        Object gmtModified
    ) {
    }

    private record MysqlUserRecord(
        Long id,
        String username,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String deptCode,
        Integer status,
        String sourceType,
        String externalId,
        String ldapDn,
        Integer tokenVersion,
        Integer deleted,
        String creator,
        String modifier,
        Object gmtCreate,
        Object gmtModified
    ) {
    }

    private record MysqlUserRoleRecord(
        String username,
        String roleCode
    ) {
    }
}
