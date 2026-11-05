package com.chick.app.identity.dto;

import jakarta.validation.constraints.Size;

/**
 * Leave {@code newPassword} out (or send no body) to have the server generate one.
 * {@code mustChangePassword} defaults to true.
 */
public record PasswordResetRequest(@Size(min = 8, max = 128) String newPassword, Boolean mustChangePassword) {
}
