package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.domain.user.UserRoleBinding;
import com.company.idm.infrastructure.persistence.dataobject.UserDO;
import com.company.idm.infrastructure.persistence.dataobject.UserRoleDO;
import com.company.idm.infrastructure.persistence.mapper.UserMapper;
import com.company.idm.infrastructure.persistence.mapper.UserRoleMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MybatisUserRepository implements UserRepository {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public Optional<User> findById(Long id) {
        UserDO dataObject = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getId, id)
            .eq(UserDO::getDeleted, 0));
        return Optional.ofNullable(dataObject).map(data -> toDomain(data, findRoleCodesByUsername(data.getUsername())));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        UserDO dataObject = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUsername, username)
            .eq(UserDO::getDeleted, 0));
        return Optional.ofNullable(dataObject).map(data -> toDomain(data, findRoleCodesByUsername(data.getUsername())));
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getDeleted, 0)
                .orderByAsc(UserDO::getId))
            .stream()
            .map(data -> toDomain(data, findRoleCodesByUsername(data.getUsername())))
            .toList();
    }

    @Override
    public User save(User user) {
        UserDO dataObject = toDataObject(user);
        if (dataObject.getId() == null) {
            dataObject.setDeleted(0);
            dataObject.setGmtCreate(LocalDateTime.now());
            dataObject.setGmtModified(LocalDateTime.now());
            userMapper.insert(dataObject);
        } else {
            dataObject.setGmtModified(LocalDateTime.now());
            userMapper.updateById(dataObject);
        }
        return toDomain(dataObject, findRoleCodesByUsername(dataObject.getUsername()));
    }

    @Override
    public void updateStatus(Long id, Integer statusCode, Integer tokenVersion) {
        UserDO dataObject = new UserDO();
        dataObject.setId(id);
        dataObject.setStatus(statusCode);
        dataObject.setTokenVersion(tokenVersion);
        dataObject.setModifier("system");
        dataObject.setGmtModified(LocalDateTime.now());
        userMapper.updateById(dataObject);
    }

    @Override
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRoleDO relation = new UserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setCreator("system");
            relation.setGmtCreate(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
    }

    @Override
    public Set<String> findRoleCodesByUsername(String username) {
        List<String> roleCodes = userRoleMapper.selectRoleCodesByUsername(username);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(roleCodes);
    }

    @Override
    public List<UserRoleBinding> listUserRoleBindings() {
        return userRoleMapper.selectUserRoleBindings().stream()
            .map(item -> new UserRoleBinding(item.username(), item.roleCode()))
            .toList();
    }

    private User toDomain(UserDO dataObject, Set<String> roleCodes) {
        return User.builder()
            .id(dataObject.getId())
            .username(dataObject.getUsername())
            .realName(dataObject.getRealName())
            .email(dataObject.getEmail())
            .mobile(dataObject.getMobile())
            .employeeNo(dataObject.getEmployeeNo())
            .deptCode(dataObject.getDeptCode())
            .status(UserStatus.fromCode(dataObject.getStatus()))
            .sourceType(SourceType.valueOf(dataObject.getSourceType()))
            .externalId(dataObject.getExternalId())
            .ldapDn(dataObject.getLdapDn())
            .tokenVersion(dataObject.getTokenVersion())
            .roleCodes(roleCodes)
            .build();
    }

    private UserDO toDataObject(User user) {
        UserDO dataObject = new UserDO();
        dataObject.setId(user.getId());
        dataObject.setUsername(user.getUsername());
        dataObject.setRealName(user.getRealName());
        dataObject.setEmail(user.getEmail());
        dataObject.setMobile(user.getMobile());
        dataObject.setEmployeeNo(user.getEmployeeNo());
        dataObject.setDeptCode(user.getDeptCode());
        dataObject.setStatus(user.getStatus().getCode());
        dataObject.setSourceType(user.getSourceType().name());
        dataObject.setExternalId(user.getExternalId());
        dataObject.setLdapDn(user.getLdapDn());
        dataObject.setTokenVersion(user.getTokenVersion());
        dataObject.setDeleted(0);
        dataObject.setCreator("system");
        dataObject.setModifier("system");
        return dataObject;
    }
}
