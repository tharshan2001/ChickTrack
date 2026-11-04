package com.chick.app.identity.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** Replaces the user's branch assignments. An empty set removes all branches. */
public record UserBranchesRequest(@NotNull Set<@NotNull UUID> branchIds) {
}
