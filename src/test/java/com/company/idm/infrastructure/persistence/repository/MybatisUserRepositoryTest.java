package com.company.idm.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.persistence.dataobject.UserDO;
import com.company.idm.infrastructure.persistence.dataobject.UserPartTimeDepartmentDO;
import com.company.idm.infrastructure.persistence.mapper.UserMapper;
import com.company.idm.infrastructure.persistence.mapper.UserPartTimeDepartmentMapper;
import com.company.idm.infrastructure.persistence.mapper.UserRoleMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisUserRepositoryTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final UserPartTimeDepartmentMapper partTimeDepartmentMapper = mock(UserPartTimeDepartmentMapper.class);
    private final MybatisUserRepository repository = new MybatisUserRepository(
        userMapper,
        userRoleMapper,
        partTimeDepartmentMapper
    );

    @Test
    void loadsEveryPartTimeDepartmentInPersistedOrder() {
        UserDO user = userData(7L, "mjc");
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(userRoleMapper.selectRoleCodesByUserId("mjc")).thenReturn(List.of());
        when(partTimeDepartmentMapper.selectByUserIds(List.of(7L))).thenReturn(List.of(
            relation(7L, "D_RESEARCH", 1),
            relation(7L, "D_JINAN", 2),
            relation(7L, "D_SHENZHEN", 3)
        ));

        List<User> users = repository.findAll();

        assertThat(users).singleElement().satisfies(item ->
            assertThat(item.getPartTimeDeptCodes())
                .containsExactly("D_RESEARCH", "D_JINAN", "D_SHENZHEN")
        );
    }

    @Test
    void replacesRelationsWithDistinctDepartmentsAndExcludesMainDepartment() {
        User user = User.builder()
            .id(9L)
            .userId("zhangsan")
            .realName("张三")
            .deptCode("D_MAIN")
            .partTimeDeptCodes(List.of("D_MAIN", "D_ONE", "D_ONE", " D_TWO "))
            .accessAllowed(true)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .sourceType(SourceType.FEISHU)
            .tokenVersion(0)
            .build();
        when(userRoleMapper.selectRoleCodesByUserId("zhangsan")).thenReturn(List.of());

        User saved = repository.save(user);

        ArgumentCaptor<UserPartTimeDepartmentDO> relationCaptor = ArgumentCaptor.forClass(UserPartTimeDepartmentDO.class);
        verify(partTimeDepartmentMapper, times(2)).insert(relationCaptor.capture());
        assertThat(relationCaptor.getAllValues())
            .extracting(UserPartTimeDepartmentDO::getDeptCode)
            .containsExactly("D_ONE", "D_TWO");
        assertThat(saved.getPartTimeDeptCodes()).containsExactly("D_ONE", "D_TWO");
    }

    private UserDO userData(Long id, String userId) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setUserId(userId);
        user.setRealName("测试用户");
        user.setAccessAllowed(true);
        user.setEmploymentStatus(EmploymentStatus.ACTIVE.name());
        user.setSourceType(SourceType.FEISHU.name());
        user.setTokenVersion(0);
        user.setDeleted(0);
        return user;
    }

    private UserPartTimeDepartmentDO relation(Long userId, String deptCode, int sortNo) {
        UserPartTimeDepartmentDO relation = new UserPartTimeDepartmentDO();
        relation.setUserId(userId);
        relation.setDeptCode(deptCode);
        relation.setSortNo(sortNo);
        return relation;
    }
}
