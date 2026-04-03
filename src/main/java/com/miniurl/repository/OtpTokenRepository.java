package com.miniurl.repository;

import com.miniurl.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findByEmailAndOtpCodeAndUsedFalse(String email, String otpCode);
    List<OtpToken> findByEmail(String email);
    void deleteByEmail(String email);
}
