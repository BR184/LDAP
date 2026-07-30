package com.company.idm.application.sync.importplan;

import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.TargetType;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportConflictImpactResolver {

    private final ImportJsonService jsonService;

    public List<Long> resolveRelatedItemIds(ChangeItem conflict, List<ChangeItem> items) {
        if (conflict == null || items == null) {
            return List.of();
        }
        Set<String> conflictIdentifiers = stableIdentifiers(conflict);
        return items.stream()
            .filter(item -> item.getId() != null)
            .filter(item -> item.getChangeType() != ChangeType.CONFLICT)
            .filter(item -> item.getStatus() == ChangeItemStatus.PENDING)
            .filter(item -> belongsToSameTargetFamily(conflict.getTargetType(), item.getTargetType()))
            .filter(item -> matchesStableIdentifier(conflictIdentifiers, item))
            .map(ChangeItem::getId)
            .distinct()
            .toList();
    }

    private boolean belongsToSameTargetFamily(TargetType conflictType, TargetType itemType) {
        if (conflictType == TargetType.USER || conflictType == TargetType.LDAP_USER) {
            return itemType == TargetType.USER;
        }
        if (conflictType == TargetType.DEPARTMENT || conflictType == TargetType.LDAP_GROUP) {
            return itemType == TargetType.DEPARTMENT;
        }
        return false;
    }

    private boolean matchesStableIdentifier(Set<String> conflictIdentifiers, ChangeItem item) {
        if (conflictIdentifiers.contains(item.getTargetKey())) {
            return true;
        }
        if (item.getTargetType() == TargetType.USER) {
            return userSnapshots(item).stream()
                .flatMap(snapshot -> List.of(snapshot.userId(), snapshot.employeeNo()).stream())
                .filter(Objects::nonNull)
                .anyMatch(conflictIdentifiers::contains);
        }
        return departmentSnapshots(item).stream()
            .flatMap(snapshot -> List.of(snapshot.deptCode(), snapshot.externalId()).stream())
            .filter(Objects::nonNull)
            .anyMatch(conflictIdentifiers::contains);
    }

    private Set<String> stableIdentifiers(ChangeItem conflict) {
        Set<String> identifiers = new LinkedHashSet<>();
        addIdentifier(identifiers, conflict.getTargetKey());
        if (conflict.getTargetType() == TargetType.USER || conflict.getTargetType() == TargetType.LDAP_USER) {
            userSnapshots(conflict).forEach(snapshot -> {
                addIdentifier(identifiers, snapshot.userId());
                addIdentifier(identifiers, snapshot.employeeNo());
            });
        } else {
            departmentSnapshots(conflict).forEach(snapshot -> {
                addIdentifier(identifiers, snapshot.deptCode());
                addIdentifier(identifiers, snapshot.externalId());
            });
        }
        return identifiers;
    }

    private List<UserImportSnapshot> userSnapshots(ChangeItem item) {
        return java.util.stream.Stream.of(item.getBeforeJson(), item.getAfterJson())
            .filter(Objects::nonNull)
            .filter(json -> !json.isBlank())
            .map(jsonService::readUserSnapshot)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<DepartmentImportSnapshot> departmentSnapshots(ChangeItem item) {
        return java.util.stream.Stream.of(item.getBeforeJson(), item.getAfterJson())
            .filter(Objects::nonNull)
            .filter(json -> !json.isBlank())
            .map(jsonService::readDepartmentSnapshot)
            .filter(Objects::nonNull)
            .toList();
    }

    private void addIdentifier(Set<String> identifiers, String value) {
        if (value != null && !value.isBlank()) {
            identifiers.add(value.trim());
        }
    }
}
