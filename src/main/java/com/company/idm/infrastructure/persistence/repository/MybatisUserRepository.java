package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.idm.common.enums.EmploymentStatus;
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
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现用户仓储与角色绑定维护。
 */
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
        return Optional.ofNullable(dataObject).map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())));
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        UserDO dataObject = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getUserId, userId)
            .eq(UserDO::getDeleted, 0));
        return Optional.ofNullable(dataObject).map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())));
    }

    @Override
    public Optional<User> findByEmployeeNo(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return Optional.empty();
        }
        UserDO dataObject = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getEmployeeNo, employeeNo)
            .eq(UserDO::getDeleted, 0));
        return Optional.ofNullable(dataObject).map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())));
    }

    @Override
    public Optional<User> findByIntranetEmail(String intranetEmail) {
        if (intranetEmail == null || intranetEmail.isBlank()) {
            return Optional.empty();
        }
        UserDO dataObject = userMapper.selectOne(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getIntranetEmail, intranetEmail)
            .eq(UserDO::getDeleted, 0));
        return Optional.ofNullable(dataObject).map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())));
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getDeleted, 0)
                .orderByAsc(UserDO::getId))
            .stream()
            .map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())))
            .toList();
    }

    @Override
    public List<User> findActiveUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getDeleted, 0)
                .eq(UserDO::getStatus, UserStatus.ENABLED.getCode())
                .ne(UserDO::getEmploymentStatus, EmploymentStatus.RESIGNED.name())
                .orderByAsc(UserDO::getId))
            .stream()
            .map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())))
            .toList();
    }

    @Override
    public List<User> findByConditions(String keyword, String deptCode, Integer statusCode) {
        LambdaQueryWrapper<UserDO> queryWrapper = new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getDeleted, 0)
            .orderByAsc(UserDO::getId);

        if (keyword != null && !keyword.isBlank()) {
            queryWrapper.and(wrapper -> wrapper
                .like(UserDO::getUserId, keyword)
                .or()
                .like(UserDO::getRealName, keyword)
                .or()
                .like(UserDO::getEmployeeNo, keyword));
        }
        if (deptCode != null && !deptCode.isBlank()) {
            queryWrapper.eq(UserDO::getDeptCode, deptCode);
        }
        if (statusCode != null) {
            queryWrapper.eq(UserDO::getStatus, statusCode);
        }

        return userMapper.selectList(queryWrapper).stream()
            .map(data -> toDomain(data, findRoleCodesByUserId(data.getUserId())))
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
        return toDomain(dataObject, findRoleCodesByUserId(dataObject.getUserId()));
    }

    @Override
    public void updateProfile(User user) {
        userMapper.update(
            null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, user.getId())
                .set(UserDO::getRealName, user.getRealName())
                .set(UserDO::getEmail, user.getEmail())
                .set(UserDO::getIntranetEmail, user.getIntranetEmail())
                .set(UserDO::getMobile, user.getMobile())
                .set(UserDO::getEmployeeNo, user.getEmployeeNo())
                .set(UserDO::getDeptCode, user.getDeptCode())
                .set(UserDO::getJobTitle, user.getJobTitle())
                .set(UserDO::getDirectLeaderRaw, user.getDirectLeaderRaw())
                .set(UserDO::getLeaderRef, user.getLeaderRef())
                .set(UserDO::getAccountStatus, user.getAccountStatus())
                .set(UserDO::getPartTimeDeptCodes, joinDeptCodes(user.getPartTimeDeptCodes()))
                .set(UserDO::getEmploymentStatus, user.getEmploymentStatus() == null ? null : user.getEmploymentStatus().name())
                .set(UserDO::getModifier, "system")
                .set(UserDO::getGmtModified, LocalDateTime.now())
        );
    }

    @Override
    public void updateStatus(Long id, Integer statusCode, Integer tokenVersion) {
        UserDO dataObject = new UserDO();
        dataObject.setId(id);
        dataObject.setStatus(statusCode);
        dataObject.setAccountStatus(statusCode != null && statusCode == UserStatus.ENABLED.getCode() ? "正常" : "冻结");
        dataObject.setTokenVersion(tokenVersion);
        dataObject.setModifier("system");
        dataObject.setGmtModified(LocalDateTime.now());
        userMapper.updateById(dataObject);
    }

    @Override
    public void logicalDelete(Long id, String recycledUsername, Integer tokenVersion) {
        userMapper.update(
            null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, id)
                .set(UserDO::getUserId, recycledUsername)
                .set(UserDO::getEmployeeNo, null)
                .set(UserDO::getIntranetEmail, null)
                .set(UserDO::getPartTimeDeptCodes, null)
                .set(UserDO::getDeleted, 1)
                .set(UserDO::getStatus, UserStatus.DISABLED.getCode())
                .set(UserDO::getTokenVersion, tokenVersion)
                .set(UserDO::getModifier, "system")
                .set(UserDO::getGmtModified, LocalDateTime.now())
        );
    }

    @Override
    public void bumpTokenVersion(Long id, Integer tokenVersion) {
        UserDO dataObject = new UserDO();
        dataObject.setId(id);
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
    public void syncRoleBindings(Long roleId, Set<Long> expectedUserIds) {
        Set<Long> expected = expectedUserIds == null ? Set.of() : new HashSet<>(expectedUserIds);
        List<UserRoleDO> currentBindings = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleDO>()
            .eq(UserRoleDO::getRoleId, roleId));
        for (UserRoleDO binding : currentBindings) {
            if (!expected.contains(binding.getUserId())) {
                userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>()
                    .eq(UserRoleDO::getUserId, binding.getUserId())
                    .eq(UserRoleDO::getRoleId, roleId));
            }
        }
        Set<Long> currentUserIds = currentBindings.stream()
            .map(UserRoleDO::getUserId)
            .collect(java.util.stream.Collectors.toSet());
        for (Long userId : expected) {
            if (currentUserIds.contains(userId)) {
                continue;
            }
            UserRoleDO relation = new UserRoleDO();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            relation.setCreator("system");
            relation.setGmtCreate(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
    }

    @Override
    public void removeAllRoles(Long userId) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
    }

    @Override
    public Set<String> findRoleCodesByUserId(String userId) {
        List<String> roleCodes = userRoleMapper.selectRoleCodesByUserId(userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(roleCodes);
    }

    @Override
    public List<UserRoleBinding> listUserRoleBindings() {
        return userRoleMapper.selectUserRoleBindings().stream()
            .map(item -> new UserRoleBinding(item.userId(), item.roleCode()))
            .toList();
    }

    @Override
    public boolean existsActiveDeptBinding(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return false;
        }
        return userMapper.selectCount(new LambdaQueryWrapper<UserDO>()
            .eq(UserDO::getDeleted, 0)
            .ne(UserDO::getEmploymentStatus, EmploymentStatus.RESIGNED.name())
            .and(wrapper -> wrapper
                .eq(UserDO::getDeptCode, deptCode)
                .or()
                .like(UserDO::getPartTimeDeptCodes, deptCode))) > 0;
    }

    private User toDomain(UserDO dataObject, Set<String> roleCodes) {
        return User.builder()
            .id(dataObject.getId())
            .userId(dataObject.getUserId())
            .realName(dataObject.getRealName())
            .email(dataObject.getEmail())
            .intranetEmail(dataObject.getIntranetEmail())
            .mobile(dataObject.getMobile())
            .employeeNo(dataObject.getEmployeeNo())
            .deptCode(dataObject.getDeptCode())
            .jobTitle(dataObject.getJobTitle())
            .directLeaderRaw(dataObject.getDirectLeaderRaw())
            .leaderRef(dataObject.getLeaderRef())
            .accountStatus(dataObject.getAccountStatus())
            .partTimeDeptCodes(parseDeptCodes(dataObject.getPartTimeDeptCodes()))
            .status(UserStatus.fromCode(dataObject.getStatus()))
            .employmentStatus(EmploymentStatus.fromCode(dataObject.getEmploymentStatus()))
            .sourceType(SourceType.valueOf(dataObject.getSourceType()))
            .ldapDn(dataObject.getLdapDn())
            .tokenVersion(dataObject.getTokenVersion())
            .roleCodes(roleCodes)
            .build();
    }

    private UserDO toDataObject(User user) {
        UserDO dataObject = new UserDO();
        dataObject.setId(user.getId());
        dataObject.setUserId(user.getUserId());
        dataObject.setRealName(user.getRealName());
        dataObject.setEmail(user.getEmail());
        dataObject.setIntranetEmail(user.getIntranetEmail());
        dataObject.setMobile(user.getMobile());
        dataObject.setEmployeeNo(user.getEmployeeNo());
        dataObject.setDeptCode(user.getDeptCode());
        dataObject.setJobTitle(user.getJobTitle());
        dataObject.setDirectLeaderRaw(user.getDirectLeaderRaw());
        dataObject.setLeaderRef(user.getLeaderRef());
        dataObject.setAccountStatus(user.getAccountStatus());
        dataObject.setPartTimeDeptCodes(joinDeptCodes(user.getPartTimeDeptCodes()));
        dataObject.setEmploymentStatus(user.getEmploymentStatus() == null ? null : user.getEmploymentStatus().name());
        dataObject.setStatus(user.getStatus().getCode());
        dataObject.setSourceType(user.getSourceType().name());
        dataObject.setLdapDn(user.getLdapDn());
        dataObject.setTokenVersion(user.getTokenVersion());
        dataObject.setDeleted(0);
        dataObject.setCreator("system");
        dataObject.setModifier("system");
        return dataObject;
    }

    private List<String> parseDeptCodes(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(rawValue.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private String joinDeptCodes(List<String> deptCodes) {
        if (deptCodes == null || deptCodes.isEmpty()) {
            return null;
        }
        return deptCodes.stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .collect(java.util.stream.Collectors.joining(","));
    }
}
