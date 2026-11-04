package com.chick.app.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UserRolesRequest(@NotEmpty Set<@NotBlank String> roles) {
}
