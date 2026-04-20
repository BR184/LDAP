package com.company.idm.test;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.boot.IdmBootApplication;
import com.company.idm.domain.ldap.LdapGroupSnapshot;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.infrastructure.ldap.StubLdapDirectoryService;
import com.company.idm.infrastructure.ldap.StubLdapGroupService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 使用 docs/feishu-import 中的真实样例验证飞书文件导入，并导出 MySQL / LDAP 保存快照。
 */
@SpringBootTest(
    classes = IdmBootApplication.class,
    properties = {
        "app.sync.feishu.file-import.enabled=true",
        "app.sync.feishu.file-import.root-dir=docs/feishu-import"
    }
)
@AutoConfigureMockMvc
class FeishuRealFileImportSnapshotTest {

    private static final String DEFAULT_IMPORTED_PASSWORD = "123456";
    private static final String PEOPLE_OU = "ou=people";
    private static final String GROUPS_OU = "ou=groups";
    private static final String BASE_DN = "dc=corp,dc=local";
    private static final String PLACEHOLDER_MEMBER_DN = "uid=placeholder," + PEOPLE_OU + "," + BASE_DN;
    private static final Path SNAPSHOT_ROOT = Path.of("target/feishu-real-import-snapshot");
    private static final Path MYSQL_SNAPSHOT_PATH = SNAPSHOT_ROOT.resolve("mysql-snapshot.json");
    private static final Path LDAP_LDIF_PATH = SNAPSHOT_ROOT.resolve("ldap-entries.ldif");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private StubLdapDirectoryService stubLdapDirectoryService;

    @Autowired
    private StubLdapGroupService stubLdapGroupService;

    @Test
    void shouldImportRealFeishuFilesAndExportMysqlAndLdapSnapshots() throws Exception {
        String adminToken = login("admin", "admin123456");
        int expectedDepartmentCount = readArraySize(Path.of("docs/feishu-import/departments/demo.json"));
        int expectedUserCount = readArraySize(Path.of("docs/feishu-import/users/demo.json"));

        mockMvc.perform(post("/api/v1/departments/import/feishu-file")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "departments/demo.json",
                      "remark": "real-file-import-test"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.batch.fileName").value("departments/demo.json"));

        mockMvc.perform(post("/api/v1/users/import/feishu-file")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "users/demo.json",
                      "remark": "real-file-import-test"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.batch.fileName").value("users/demo.json"));

        List<DepartmentDO> importedDepartments = departmentMapper.selectList(
            new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getSourceType, "FEISHU")
                .orderByAsc(DepartmentDO::getDeptLevel)
                .orderByAsc(DepartmentDO::getId)
        );
        List<UserDO> importedUsers = userMapper.selectList(
            new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getSourceType, "FEISHU")
                .eq(UserDO::getDeleted, 0)
                .orderByAsc(UserDO::getId)
        );

        assertThat(importedDepartments).hasSize(expectedDepartmentCount);
        assertThat(importedUsers).hasSize(expectedUserCount);

        Set<String> importedUsernames = importedUsers.stream()
            .map(UserDO::getUsername)
            .collect(Collectors.toSet());

        List<UserRoleBindingRecord> importedUserRoles = userRoleMapper.selectUserRoleBindings().stream()
            .filter(item -> importedUsernames.contains(item.username()))
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

        assertThat(importedUserRoles).allSatisfy(item -> assertThat(item.roleCode()).isEqualTo("NORMAL_USER"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("accessToken").asText();
    }

    private int readArraySize(Path path) throws Exception {
        JsonNode node = objectMapper.readTree(Files.readString(path));
        return node.size();
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
        LdapUserSnapshot snapshot = stubLdapDirectoryService.findUserSnapshot(user.getUsername());
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
        builder.append("employeeType: ").append(snapshot.getStatus()).append(System.lineSeparator());
        builder.append("userPassword: ").append(DEFAULT_IMPORTED_PASSWORD).append(System.lineSeparator());
        return builder.toString();
    }

    private String renderGroupEntry(DepartmentDO department) {
        LdapGroupSnapshot snapshot = stubLdapGroupService.findGroupSnapshot(department.getDeptCode());
        List<String> members = snapshot.getMembers().isEmpty()
            ? List.of(PLACEHOLDER_MEMBER_DN)
            : snapshot.getMembers();

        StringBuilder builder = new StringBuilder();
        builder.append("dn: ").append(snapshot.getDn()).append(System.lineSeparator());
        builder.append("objectClass: groupOfNames").append(System.lineSeparator());
        builder.append("cn: ").append(department.getDeptCode()).append("_").append(department.getDeptName()).append(System.lineSeparator());
        builder.append("description: ").append(department.getDeptName()).append(System.lineSeparator());
        builder.append("businessCategory: ").append(department.getDeptCode()).append(System.lineSeparator());
        for (String member : members) {
            builder.append("member: ").append(member).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append(key).append(": ").append(value).append(System.lineSeparator());
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
