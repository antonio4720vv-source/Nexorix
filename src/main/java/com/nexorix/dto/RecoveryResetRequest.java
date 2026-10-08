package com.nexorix.dto;

/** Contrasena nueva y/o PIN nuevo. Lo que venga vacio no se cambia. */
public record RecoveryResetRequest(
        String newPassword,
        String newPasswordConfirm,
        String newPin,
        String newPinConfirm
) {
}
