package com.company.idm.application.rolegroup;

import java.time.LocalDateTime;
import java.util.List;

public record RoleSupplySnapshot(long snapshotCursor, LocalDateTime generatedAt, List<RoleSupplyRoleSnapshot> roles) {
}
