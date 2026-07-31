package com.company.idm.application.user;

import com.company.idm.domain.user.User;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReportingHierarchyService {

    public boolean isDirectSubordinate(User manager, User user) {
        if (manager == null || user == null) {
            return false;
        }
        String managerUserId = normalize(manager.getUserId());
        return !managerUserId.isBlank() && managerUserId.equals(normalize(user.getLeaderRef()));
    }

    public boolean isDescendant(User manager, User user, List<User> organizationSnapshot) {
        if (manager == null || user == null || user.getId() == null) {
            return false;
        }
        return findDescendantUserIds(manager, organizationSnapshot).contains(user.getId());
    }

    public Set<Long> findDescendantUserIds(User manager, List<User> organizationSnapshot) {
        if (manager == null || manager.getId() == null || organizationSnapshot == null || organizationSnapshot.isEmpty()) {
            return Set.of();
        }
        String managerUserId = normalize(manager.getUserId());
        if (managerUserId.isBlank()) {
            return Set.of();
        }

        Map<String, List<User>> subordinatesByLeaderRef = new HashMap<>();
        for (User user : organizationSnapshot) {
            String leaderRef = normalize(user.getLeaderRef());
            if (!leaderRef.isBlank()) {
                subordinatesByLeaderRef.computeIfAbsent(leaderRef, ignored -> new ArrayList<>()).add(user);
            }
        }

        Set<Long> descendantUserIds = new HashSet<>();
        ArrayDeque<User> queue = new ArrayDeque<>(subordinatesByLeaderRef.getOrDefault(managerUserId, List.of()));
        while (!queue.isEmpty()) {
            User current = queue.removeFirst();
            if (current.getId() == null || current.getId().equals(manager.getId()) || !descendantUserIds.add(current.getId())) {
                continue;
            }
            String userId = normalize(current.getUserId());
            if (!userId.isBlank()) {
                queue.addAll(subordinatesByLeaderRef.getOrDefault(userId, List.of()));
            }
        }
        return Set.copyOf(descendantUserIds);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
