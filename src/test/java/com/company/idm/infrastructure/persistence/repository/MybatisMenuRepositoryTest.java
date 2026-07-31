package com.company.idm.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.infrastructure.persistence.dataobject.MenuDO;
import com.company.idm.infrastructure.persistence.mapper.MenuMapper;
import com.company.idm.infrastructure.persistence.mapper.MenuPermissionMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MybatisMenuRepositoryTest {

    @Test
    void resolvesVisibleMenusFromMenuPermissionCodesOnly() {
        MenuMapper menuMapper = mock(MenuMapper.class);
        MenuPermissionMapper menuPermissionMapper = mock(MenuPermissionMapper.class);
        MybatisMenuRepository repository = new MybatisMenuRepository(menuMapper, menuPermissionMapper);
        Set<String> permissionCodes = Set.of("MENU_VIEW_USER_MANAGEMENT");
        MenuDO userManagement = new MenuDO();
        userManagement.setId(3L);
        userManagement.setMenuCode("USER_MANAGEMENT");
        userManagement.setMenuType("MENU");
        userManagement.setStatus(1);
        userManagement.setVisible(1);
        when(menuMapper.selectVisibleByPermissionCodes(permissionCodes)).thenReturn(List.of(userManagement));

        assertThat(repository.findVisibleByPermissionCodes(permissionCodes))
            .extracting(menu -> menu.getMenuCode())
            .containsExactly("USER_MANAGEMENT");
        verify(menuMapper).selectVisibleByPermissionCodes(permissionCodes);
    }
}
