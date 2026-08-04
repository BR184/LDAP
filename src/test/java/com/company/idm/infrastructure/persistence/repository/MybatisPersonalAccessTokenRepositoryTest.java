package com.company.idm.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenPermission;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.infrastructure.persistence.dataobject.PersonalAccessTokenDO;
import com.company.idm.infrastructure.persistence.dataobject.PersonalAccessTokenPermissionDO;
import com.company.idm.infrastructure.persistence.mapper.PersonalAccessTokenMapper;
import com.company.idm.infrastructure.persistence.mapper.PersonalAccessTokenPermissionMapper;
import com.company.idm.infrastructure.persistence.record.PersonalAccessTokenPermissionRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisPersonalAccessTokenRepositoryTest {

    private final PersonalAccessTokenMapper tokenMapper = mock(PersonalAccessTokenMapper.class);
    private final PersonalAccessTokenPermissionMapper permissionMapper = mock(PersonalAccessTokenPermissionMapper.class);
    private final MybatisPersonalAccessTokenRepository repository = new MybatisPersonalAccessTokenRepository(
        tokenMapper,
        permissionMapper
    );

    @Test
    void createsPermissionRelationsInTheSelectedOrder() {
        PersonalAccessTokenPermission second = permission(2L, "SECOND");
        PersonalAccessTokenPermission first = permission(1L, "FIRST");
        PersonalAccessToken token = token(List.of(second, first));
        PersonalAccessTokenDO persisted = persistedToken();

        when(tokenMapper.insert(any(PersonalAccessTokenDO.class))).thenAnswer(invocation -> {
            invocation.<PersonalAccessTokenDO>getArgument(0).setId(10L);
            return 1;
        });
        when(tokenMapper.selectOne(any())).thenReturn(persisted);
        when(permissionMapper.selectPermissionRecords(List.of(10L))).thenReturn(List.of(
            record(10L, second),
            record(10L, first)
        ));

        PersonalAccessToken created = repository.create(token);

        ArgumentCaptor<PersonalAccessTokenPermissionDO> relationCaptor =
            ArgumentCaptor.forClass(PersonalAccessTokenPermissionDO.class);
        verify(permissionMapper, org.mockito.Mockito.times(2)).insert(relationCaptor.capture());
        assertThat(relationCaptor.getAllValues())
            .extracting(PersonalAccessTokenPermissionDO::getPermissionId)
            .containsExactly(2L, 1L);
        assertThat(created.getPermissions()).extracting(PersonalAccessTokenPermission::code)
            .containsExactly("SECOND", "FIRST");
        assertThat(created.getDescription()).isEqualTo("deployment automation");
        assertThat(created.getScopeMode()).isEqualTo(PersonalAccessTokenScopeMode.FIXED);
        assertThat(created.getSecretValue()).isEqualTo("idm_pat_token-uid_secret");
    }

    @Test
    void rotatesSecretMaterialWithoutChangingOwnership() {
        PersonalAccessToken rotated = token(List.of()).toBuilder()
            .id(10L)
            .tokenUid("new-token-uid")
            .secretHash("new-hash")
            .secretValue("idm_pat_new-token-uid_secret")
            .gmtModified(LocalDateTime.of(2026, 8, 4, 10, 0))
            .build();
        when(tokenMapper.update(any(PersonalAccessTokenDO.class), any())).thenReturn(1);

        assertThat(repository.rotateOwned(rotated)).isTrue();

        ArgumentCaptor<PersonalAccessTokenDO> changesCaptor = ArgumentCaptor.forClass(PersonalAccessTokenDO.class);
        verify(tokenMapper).update(changesCaptor.capture(), any());
        assertThat(changesCaptor.getValue().getSecretValue()).isEqualTo("idm_pat_new-token-uid_secret");
    }

    @Test
    void rejectsRotationWithoutModificationTimestamp() {
        PersonalAccessToken rotated = token(List.of()).toBuilder()
            .id(10L)
            .tokenUid("new-token-uid")
            .secretHash("new-hash")
            .gmtModified(null)
            .build();

        assertThat(repository.rotateOwned(rotated)).isFalse();
        verify(tokenMapper, times(0)).update(any(PersonalAccessTokenDO.class), any());
    }

    @Test
    void physicallyDeletesOwnedTokenAndPermissionRelations() {
        PersonalAccessTokenDO owned = persistedToken();
        when(tokenMapper.selectOne(any())).thenReturn(owned);
        when(permissionMapper.delete(any())).thenReturn(2);
        when(tokenMapper.delete(any())).thenReturn(1);

        assertThat(repository.deleteOwned(10L, 7L)).isTrue();

        verify(permissionMapper, times(1)).delete(any());
        verify(tokenMapper, times(1)).delete(any());
    }

    @Test
    void mapsRevocationAndExpiryWithoutTreatingEitherAsActive() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 18, 0);
        PersonalAccessToken active = token(List.of()).toBuilder().expiresAt(now.plusDays(1)).build();
        PersonalAccessToken expired = token(List.of()).toBuilder().expiresAt(now.minusSeconds(1)).build();
        PersonalAccessToken revoked = token(List.of()).toBuilder().revokedAt(now.minusMinutes(1)).build();

        assertThat(active.isActiveAt(now)).isTrue();
        assertThat(expired.isActiveAt(now)).isFalse();
        assertThat(revoked.isActiveAt(now)).isFalse();
    }

    private PersonalAccessToken token(List<PersonalAccessTokenPermission> permissions) {
        return PersonalAccessToken.builder()
            .tokenUid("token-uid")
            .userId(7L)
            .name("automation")
            .description("deployment automation")
            .secretHash("hash")
            .hashVersion(1)
            .tokenPrefix("idm_pat_token-uid_...")
            .secretValue("idm_pat_token-uid_secret")
            .scopeMode(PersonalAccessTokenScopeMode.FIXED)
            .creator("employee")
            .modifier("employee")
            .gmtModified(LocalDateTime.of(2026, 8, 4, 10, 0))
            .permissions(permissions)
            .build();
    }

    private PersonalAccessTokenPermission permission(Long id, String code) {
        return new PersonalAccessTokenPermission(id, code, code, "/api", "GET");
    }

    private PersonalAccessTokenDO persistedToken() {
        PersonalAccessTokenDO dataObject = new PersonalAccessTokenDO();
        dataObject.setId(10L);
        dataObject.setTokenUid("token-uid");
        dataObject.setUserId(7L);
        dataObject.setName("automation");
        dataObject.setDescription("deployment automation");
        dataObject.setSecretHash("hash");
        dataObject.setHashVersion(1);
        dataObject.setTokenPrefix("idm_pat_token-uid_...");
        dataObject.setSecretValue("idm_pat_token-uid_secret");
        dataObject.setScopeMode(PersonalAccessTokenScopeMode.FIXED.name());
        dataObject.setCreator("employee");
        dataObject.setModifier("employee");
        return dataObject;
    }

    private PersonalAccessTokenPermissionRecord record(
        Long tokenId,
        PersonalAccessTokenPermission permission
    ) {
        return new PersonalAccessTokenPermissionRecord(
            tokenId,
            permission.id(),
            permission.code(),
            permission.name(),
            permission.resourcePath(),
            permission.action()
        );
    }
}
