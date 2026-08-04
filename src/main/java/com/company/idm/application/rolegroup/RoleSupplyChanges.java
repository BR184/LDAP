package com.company.idm.application.rolegroup;

import com.company.idm.domain.rolegroup.RoleMembershipChange;
import java.util.List;

public record RoleSupplyChanges(long nextCursor, boolean hasMore, List<RoleMembershipChange> changes) {
}
