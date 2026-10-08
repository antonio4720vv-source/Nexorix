package com.nexorix.dto;

import com.nexorix.user.User;

public class UserResponse {

    private final String name;
    private final String username;
    private final String email;
    private final String kycStatus;
    private final boolean active;
    private final String publicId;
    private final boolean pinConfigured;

    public UserResponse(
            String name,
            String username,
            String email,
            String kycStatus,
            boolean active,
            String publicId,
            boolean pinConfigured
    ) {
        this.name = name;
        this.username = username;
        this.email = email;
        this.kycStatus = kycStatus;
        this.active = active;
        this.publicId = publicId;
        this.pinConfigured = pinConfigured;
    }

    public static UserResponse fromUser(User user) {
        return new UserResponse(
                user.getName(),
                user.getUsername(),
                user.getEmail(),
                user.getKycStatus().name(),
                user.isActive(),
                user.getPublicId(),
                user.hasPin()
        );
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getKycStatus() {
        return kycStatus;
    }

    public boolean isActive() {
        return active;
    }

    public String getPublicId() {
        return publicId;
    }

    public boolean isPinConfigured() {
        return pinConfigured;
    }
}
