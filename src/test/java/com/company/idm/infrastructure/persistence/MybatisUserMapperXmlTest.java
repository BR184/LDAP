package com.company.idm.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.idm.domain.user.UserPageQuery;
import com.company.idm.infrastructure.persistence.dataobject.UserDO;
import com.company.idm.infrastructure.persistence.mapper.UserMapper;
import java.io.InputStream;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证 V2 用户分页查询的 MyBatis XML（UserMapper.xml）可解析、可执行，
 * 覆盖 keyword / roleCodes / 部门规则 / 可见集等动态 SQL 分支。
 */
class MybatisUserMapperXmlTest {

    private UserMapper userMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:xmltest;MODE=MySQL;DB_CLOSE_DELAY=-1");
        MybatisConfiguration configuration = new MybatisConfiguration(
            new Environment("test", new JdbcTransactionFactory(), dataSource)
        );
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(UserMapper.class);
        try (InputStream in = Resources.getResourceAsStream("mapper/UserMapper.xml")) {
            XMLMapperBuilder parser = new XMLMapperBuilder(
                in, configuration, "mapper/UserMapper.xml", configuration.getSqlFragments()
            );
            parser.parse();
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        userMapper = factory.openSession(true).getMapper(UserMapper.class);

        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            for (String table : new String[]{"sys_user_part_time_department", "sys_user_role", "sys_role",
                "sys_department", "sys_user"}) {
                stmt.execute("DROP TABLE IF EXISTS " + table);
            }
            stmt.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id VARCHAR(128) NOT NULL, "
                + "real_name VARCHAR(128), email VARCHAR(255), intranet_email VARCHAR(255), mobile VARCHAR(64), "
                + "employee_no VARCHAR(64), dept_code VARCHAR(64), job_title VARCHAR(128), "
                + "direct_leader_raw VARCHAR(255), leader_ref VARCHAR(64), account_status VARCHAR(64), "
                + "employment_status VARCHAR(32), access_allowed BOOLEAN, source_type VARCHAR(32), "
                + "ldap_dn VARCHAR(255), token_version INT, deleted INT DEFAULT 0, creator VARCHAR(64), "
                + "modifier VARCHAR(64), gmt_create TIMESTAMP, gmt_modified TIMESTAMP)");
            stmt.execute("CREATE TABLE sys_department (id BIGINT PRIMARY KEY, dept_code VARCHAR(64), dept_name VARCHAR(128), "
                + "parent_dept_code VARCHAR(64), ancestor_path VARCHAR(255), dept_level INT, status INT)");
            stmt.execute("CREATE TABLE sys_user_role (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, role_id BIGINT, creator VARCHAR(64))");
            stmt.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, role_code VARCHAR(64), role_name VARCHAR(128), "
                + "permission_level INT, status INT, built_in INT)");
            stmt.execute("CREATE TABLE sys_user_part_time_department (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, dept_code VARCHAR(64), sort_no INT)");

            stmt.execute("INSERT INTO sys_user (id, user_id, real_name, employee_no, dept_code, employment_status, access_allowed, source_type, deleted) VALUES "
                + "(1, 'lixinran', '李欣然', 'E001', 'D001', 'ACTIVE', TRUE, 'MANUAL', 0),"
                + "(2, 'lixinran2', '李欣然2', 'E002', 'D002', 'ACTIVE', TRUE, 'MANUAL', 0),"
                + "(3, 'zhangsan', '张三', 'E003', 'D003', 'ACTIVE', TRUE, 'MANUAL', 0)");
            stmt.execute("INSERT INTO sys_department (id, dept_code, dept_name, parent_dept_code, ancestor_path, dept_level, status) VALUES "
                + "(1, 'D001', '华天创智', NULL, '/D001', 1, 1),"
                + "(2, 'D002', '深圳分公司', 'D001', '/D001/D002', 2, 1),"
                + "(3, 'D003', '北京分公司', 'D001', '/D001/D003', 2, 1)");
            stmt.execute("INSERT INTO sys_role (id, role_code, role_name, permission_level, status, built_in) VALUES "
                + "(1, 'NORMAL_USER', '普通用户', 30, 1, 1), (2, 'ADMIN', '管理员', 10, 1, 1)");
            stmt.execute("INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 1)");
            stmt.execute("INSERT INTO sys_user_part_time_department (user_id, dept_code, sort_no) VALUES (3, 'D001', 1)");
        }
    }

    @Test
    void keywordQueryIsFuzzyAcrossUserFields() {
        List<UserDO> rows = query(null, "lixin", null, null, null, null);
        assertThat(rows).extracting(UserDO::getUserId).containsExactlyInAnyOrder("lixinran", "lixinran2");
    }

    @Test
    void exactUserIdQueryReturnsSingleRow() {
        List<UserDO> rows = query("lixinran", null, null, null, null, null);
        assertThat(rows).extracting(UserDO::getUserId).containsExactly("lixinran");
    }

    @Test
    void roleCodesFilterUsesEnabledRoleSubquery() {
        List<UserDO> rows = query(null, null, List.of("NORMAL_USER"), null, null, null);
        assertThat(rows).extracting(UserDO::getUserId).containsExactlyInAnyOrder("lixinran", "lixinran2");
    }

    @Test
    void includeDepartmentRuleSubtreeMatchesPrimaryAndPartTime() {
        // include subtree D001：primary D001（lixinran）+ part-time D001（zhangsan）+ subtree D002/D003 下用户（lixinran2）
        UserPageQuery.DepartmentRule rule = new UserPageQuery.DepartmentRule(
            false, false, List.of("D001", "D002", "D003"), List.of("D001", "D002", "D003")
        );
        List<UserDO> rows = query(null, null, null, List.of(rule), null, null);
        assertThat(rows).extracting(UserDO::getUserId).containsExactlyInAnyOrder("lixinran", "lixinran2", "zhangsan");
    }

    @Test
    void unassignedExcludeRuleDropsDepartmentlessUsers() {
        UserPageQuery.DepartmentRule unassigned = new UserPageQuery.DepartmentRule(true, true, List.of(), List.of());
        List<UserDO> rows = query(null, null, null, null, List.of(unassigned), null);
        assertThat(rows).extracting(UserDO::getUserId).containsExactlyInAnyOrder("lixinran", "lixinran2", "zhangsan");
    }

    @Test
    void visibleUserIdsRestrictsToProvidedSet() {
        List<UserDO> rows = query(null, null, null, null, null, List.of(1L, 3L));
        assertThat(rows).extracting(UserDO::getUserId).containsExactlyInAnyOrder("lixinran", "zhangsan");
    }

    private List<UserDO> query(
        String userId,
        String keyword,
        List<String> roleCodes,
        List<UserPageQuery.DepartmentRule> includeRules,
        List<UserPageQuery.DepartmentRule> excludeRules,
        List<Long> visibleUserIds
    ) {
        return userMapper.selectV2UserPage(
            new Page<>(1, 10),
            userId, keyword, null, null, null, null, null, null, null,
            null, null, null, roleCodes, null, null,
            includeRules, excludeRules, visibleUserIds, "u.id", "asc"
        ).getRecords();
    }
}
