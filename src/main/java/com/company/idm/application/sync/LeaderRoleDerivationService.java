package com.company.idm.application.sync;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeaderRoleDerivationService {

    private static final String DIRECT_MANAGER_ROLE = "DIRECT_MANAGER";
    private static final String TREE_MANAGER_ROLE = "TREE_MANAGER";
    private static final int MAX_DEPTH = 10;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public void syncDerivedRoles() {
        List<User> activeUsers = userRepository.findActiveEmployees();
        Map<String, User> usersByUserId = new HashMap<>();
        for (User user : activeUsers) {
            usersByUserId.put(user.getUserId(), user);
        }

        Map<String, List<String>> directSubordinates = buildDirectSubordinates(activeUsers, usersByUserId);
        Set<String> directManagerUserIds = new HashSet<>(directSubordinates.keySet());
        Set<String> treeManagerUserIds = findTreeManagerUserIds(directSubordinates);

        syncRole(DIRECT_MANAGER_ROLE, toDatabaseIds(directManagerUserIds, usersByUserId));
        syncRole(TREE_MANAGER_ROLE, toDatabaseIds(treeManagerUserIds, usersByUserId));
    }

    private Map<String, List<String>> buildDirectSubordinates(List<User> activeUsers, Map<String, User> usersByUserId) {
        Map<String, List<String>> result = new HashMap<>();
        for (User user : activeUsers) {
            String leaderRef = normalize(user.getLeaderRef());
            if (leaderRef == null || leaderRef.equals(user.getUserId()) || !usersByUserId.containsKey(leaderRef)) {
                continue;
            }
            result.computeIfAbsent(leaderRef, ignored -> new ArrayList<>()).add(user.getUserId());
        }
        return result;
    }

    private Set<String> findTreeManagerUserIds(Map<String, List<String>> directSubordinates) {
        Set<String> result = new HashSet<>();
        for (String managerUserId : directSubordinates.keySet()) {
            if (hasSubordinateBelowDirectLevel(managerUserId, directSubordinates)) {
                result.add(managerUserId);
            }
        }
        return result;
    }

    private boolean hasSubordinateBelowDirectLevel(String managerUserId, Map<String, List<String>> directSubordinates) {
        ArrayDeque<NodeDepth> queue = new ArrayDeque<>();
        for (String directSubordinate : directSubordinates.getOrDefault(managerUserId, List.of())) {
            queue.add(new NodeDepth(directSubordinate, 1));
        }
        Set<String> visited = new HashSet<>();
        visited.add(managerUserId);
        while (!queue.isEmpty()) {
            NodeDepth current = queue.removeFirst();
            if (!visited.add(current.userId())) {
                continue;
            }
            if (current.depth() >= 2) {
                return true;
            }
            if (current.depth() >= MAX_DEPTH) {
                continue;
            }
            for (String next : directSubordinates.getOrDefault(current.userId(), List.of())) {
                queue.addLast(new NodeDepth(next, current.depth() + 1));
            }
        }
        return false;
    }

    private Set<Long> toDatabaseIds(Set<String> userIds, Map<String, User> usersByUserId) {
        Set<Long> result = new HashSet<>();
        for (String userId : userIds) {
            User user = usersByUserId.get(userId);
            if (user != null && user.getId() != null) {
                result.add(user.getId());
            }
        }
        return result;
    }

    private void syncRole(String roleCode, Set<Long> expectedUserIds) {
        Role role = roleRepository.findByCode(roleCode)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "组织派生角色不存在：" + roleCode));
        userRepository.syncRoleBindings(role.getId(), expectedUserIds, "system:leader-role-derivation");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NodeDepth(String userId, int depth) {
    }
}
