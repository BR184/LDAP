package com.company.idm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenPermission;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.infrastructure.persistence.dataobject.PersonalAccessTokenDO;
import com.company.idm.infrastructure.persistence.dataobject.PersonalAccessTokenPermissionDO;
import com.company.idm.infrastructure.persistence.mapper.PersonalAccessTokenMapper;
import com.company.idm.infrastructure.persistence.mapper.PersonalAccessTokenPermissionMapper;
import com.company.idm.infrastructure.persistence.record.PersonalAccessTokenPermissionRecord;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MybatisPersonalAccessTokenRepository implements PersonalAccessTokenRepository {

    private final PersonalAccessTokenMapper tokenMapper;
    private final PersonalAccessTokenPermissionMapper permissionMapper;

    @Override
    @Transactional
    public PersonalAccessToken create(PersonalAccessToken token) {
        if (token.getId() != null) {
            throw new IllegalArgumentException("New personal access token must not already have an id");
        }
        LocalDateTime now = LocalDateTime.now();
        PersonalAccessTokenDO dataObject = toDataObject(token);
        dataObject.setGmtCreate(token.getGmtCreate() == null ? now : token.getGmtCreate());
        dataObject.setGmtModified(token.getGmtModified() == null ? now : token.getGmtModified());
        tokenMapper.insert(dataObject);

        for (PersonalAccessTokenPermission permission : token.getPermissions()) {
            PersonalAccessTokenPermissionDO relation = new PersonalAccessTokenPermissionDO();
            relation.setTokenId(dataObject.getId());
            relation.setPermissionId(permission.id());
            relation.setGmtCreate(now);
            permissionMapper.insert(relation);
        }
        return findByTokenUid(dataObject.getTokenUid()).orElseThrow();
    }

    @Override
    public Optional<PersonalAccessToken> findByTokenUid(String tokenUid) {
        if (tokenUid == null || tokenUid.isBlank()) {
            return Optional.empty();
        }
        PersonalAccessTokenDO token = tokenMapper.selectOne(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getTokenUid, tokenUid));
        return Optional.ofNullable(token).map(item -> toDomain(item, permissionsByTokenIds(List.of(item.getId()))));
    }

    @Override
    public Optional<PersonalAccessToken> findOwnedById(Long tokenId, Long userId) {
        if (tokenId == null || userId == null) {
            return Optional.empty();
        }
        PersonalAccessTokenDO token = tokenMapper.selectOne(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getId, tokenId)
            .eq(PersonalAccessTokenDO::getUserId, userId));
        return Optional.ofNullable(token).map(item -> toDomain(item, permissionsByTokenIds(List.of(item.getId()))));
    }

    @Override
    public List<PersonalAccessToken> findByUserId(Long userId, long offset, int limit) {
        if (userId == null) {
            return List.of();
        }
        long safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<PersonalAccessTokenDO> tokens = tokenMapper.selectList(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getUserId, userId)
            .orderByDesc(PersonalAccessTokenDO::getGmtCreate)
            .orderByDesc(PersonalAccessTokenDO::getId)
            .last("LIMIT " + safeOffset + ", " + safeLimit));
        if (tokens.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PersonalAccessTokenPermission>> permissions = permissionsByTokenIds(
            tokens.stream().map(PersonalAccessTokenDO::getId).toList()
        );
        return tokens.stream().map(token -> toDomain(token, permissions)).toList();
    }

    @Override
    public long countByUserId(Long userId) {
        if (userId == null) {
            return 0;
        }
        return tokenMapper.selectCount(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getUserId, userId));
    }

    @Override
    public long countActiveByUserId(Long userId, LocalDateTime now) {
        if (userId == null || now == null) {
            return 0;
        }
        return tokenMapper.selectCount(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getUserId, userId)
            .isNull(PersonalAccessTokenDO::getRevokedAt)
            .and(expiry -> expiry.isNull(PersonalAccessTokenDO::getExpiresAt)
                .or()
                .gt(PersonalAccessTokenDO::getExpiresAt, now)));
    }

    @Override
    public boolean revokeOwned(Long tokenId, Long userId, LocalDateTime revokedAt, String modifier) {
        if (tokenId == null || userId == null || revokedAt == null) {
            return false;
        }
        return tokenMapper.update(null, new LambdaUpdateWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getId, tokenId)
            .eq(PersonalAccessTokenDO::getUserId, userId)
            .isNull(PersonalAccessTokenDO::getRevokedAt)
            .set(PersonalAccessTokenDO::getRevokedAt, revokedAt)
            .set(PersonalAccessTokenDO::getModifier, modifier)
            .set(PersonalAccessTokenDO::getGmtModified, revokedAt)) > 0;
    }

    @Override
    public boolean rotateOwned(PersonalAccessToken token) {
        if (token == null || token.getId() == null || token.getUserId() == null
            || token.getGmtModified() == null) {
            return false;
        }
        PersonalAccessTokenDO changes = new PersonalAccessTokenDO();
        changes.setTokenUid(token.getTokenUid());
        changes.setSecretHash(token.getSecretHash());
        changes.setSecretValue(token.getSecretValue());
        changes.setHashVersion(token.getHashVersion());
        changes.setTokenPrefix(token.getTokenPrefix());
        changes.setModifier(token.getModifier());
        changes.setGmtModified(token.getGmtModified());
        return tokenMapper.update(changes, new LambdaUpdateWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getId, token.getId())
            .eq(PersonalAccessTokenDO::getUserId, token.getUserId())
            .isNull(PersonalAccessTokenDO::getRevokedAt)
            .and(expiry -> expiry.isNull(PersonalAccessTokenDO::getExpiresAt)
                .or()
                .gt(PersonalAccessTokenDO::getExpiresAt, token.getGmtModified()))) > 0;
    }

    @Override
    @Transactional
    public boolean deleteOwned(Long tokenId, Long userId) {
        if (tokenId == null || userId == null) {
            return false;
        }
        PersonalAccessTokenDO owned = tokenMapper.selectOne(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getId, tokenId)
            .eq(PersonalAccessTokenDO::getUserId, userId));
        if (owned == null) {
            return false;
        }
        permissionMapper.delete(new LambdaQueryWrapper<PersonalAccessTokenPermissionDO>()
            .eq(PersonalAccessTokenPermissionDO::getTokenId, tokenId));
        return tokenMapper.delete(new LambdaQueryWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getId, tokenId)
            .eq(PersonalAccessTokenDO::getUserId, userId)) > 0;
    }

    @Override
    public boolean updateLastUsedIfBefore(
        Long tokenId,
        LocalDateTime usedAt,
        String sourceIp,
        LocalDateTime writeThreshold
    ) {
        if (tokenId == null || usedAt == null || writeThreshold == null) {
            return false;
        }
        return tokenMapper.update(null, new LambdaUpdateWrapper<PersonalAccessTokenDO>()
            .eq(PersonalAccessTokenDO::getId, tokenId)
            .isNull(PersonalAccessTokenDO::getRevokedAt)
            .and(lastUsed -> lastUsed.isNull(PersonalAccessTokenDO::getLastUsedAt)
                .or()
                .le(PersonalAccessTokenDO::getLastUsedAt, writeThreshold))
            .set(PersonalAccessTokenDO::getLastUsedAt, usedAt)
            .set(PersonalAccessTokenDO::getLastUsedIp, sourceIp)
            .set(PersonalAccessTokenDO::getGmtModified, usedAt)) > 0;
    }

    private Map<Long, List<PersonalAccessTokenPermission>> permissionsByTokenIds(List<Long> tokenIds) {
        if (tokenIds == null || tokenIds.isEmpty()) {
            return Map.of();
        }
        return permissionMapper.selectPermissionRecords(tokenIds).stream()
            .collect(Collectors.groupingBy(
                PersonalAccessTokenPermissionRecord::tokenId,
                Collectors.mapping(this::toPermission, Collectors.toList())
            ));
    }

    private PersonalAccessToken toDomain(
        PersonalAccessTokenDO dataObject,
        Map<Long, List<PersonalAccessTokenPermission>> permissions
    ) {
        return PersonalAccessToken.builder()
            .id(dataObject.getId())
            .tokenUid(dataObject.getTokenUid())
            .userId(dataObject.getUserId())
            .name(dataObject.getName())
            .description(dataObject.getDescription())
            .scopeMode(parseScopeMode(dataObject.getScopeMode()))
            .secretHash(dataObject.getSecretHash())
            .secretValue(dataObject.getSecretValue())
            .hashVersion(dataObject.getHashVersion())
            .tokenPrefix(dataObject.getTokenPrefix())
            .expiresAt(dataObject.getExpiresAt())
            .revokedAt(dataObject.getRevokedAt())
            .lastUsedAt(dataObject.getLastUsedAt())
            .lastUsedIp(dataObject.getLastUsedIp())
            .creator(dataObject.getCreator())
            .modifier(dataObject.getModifier())
            .gmtCreate(dataObject.getGmtCreate())
            .gmtModified(dataObject.getGmtModified())
            .permissions(permissions.getOrDefault(dataObject.getId(), Collections.emptyList()))
            .build();
    }

    private PersonalAccessTokenPermission toPermission(PersonalAccessTokenPermissionRecord record) {
        return new PersonalAccessTokenPermission(
            record.permissionId(),
            record.permissionCode(),
            record.permissionName(),
            record.resourcePath(),
            record.action()
        );
    }

    private PersonalAccessTokenDO toDataObject(PersonalAccessToken token) {
        PersonalAccessTokenDO dataObject = new PersonalAccessTokenDO();
        dataObject.setId(token.getId());
        dataObject.setTokenUid(token.getTokenUid());
        dataObject.setUserId(token.getUserId());
        dataObject.setName(token.getName());
        dataObject.setDescription(token.getDescription());
        dataObject.setScopeMode((token.getScopeMode() == null
            ? PersonalAccessTokenScopeMode.FIXED
            : token.getScopeMode()).name());
        dataObject.setSecretHash(token.getSecretHash());
        dataObject.setSecretValue(token.getSecretValue());
        dataObject.setHashVersion(token.getHashVersion());
        dataObject.setTokenPrefix(token.getTokenPrefix());
        dataObject.setExpiresAt(token.getExpiresAt());
        dataObject.setRevokedAt(token.getRevokedAt());
        dataObject.setLastUsedAt(token.getLastUsedAt());
        dataObject.setLastUsedIp(token.getLastUsedIp());
        dataObject.setCreator(token.getCreator());
        dataObject.setModifier(token.getModifier());
        dataObject.setGmtCreate(token.getGmtCreate());
        dataObject.setGmtModified(token.getGmtModified());
        return dataObject;
    }

    private PersonalAccessTokenScopeMode parseScopeMode(String value) {
        return value == null || value.isBlank()
            ? PersonalAccessTokenScopeMode.FIXED
            : PersonalAccessTokenScopeMode.valueOf(value);
    }
}
