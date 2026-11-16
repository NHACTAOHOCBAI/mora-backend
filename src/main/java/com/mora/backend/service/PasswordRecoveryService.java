package com.mora.backend.service;

public interface PasswordRecoveryService {
    void request(String email, String remoteAddress);
    void reset(String token, String newPassword, String remoteAddress);
}
