package com.tradeguide.repository.auth;

import com.tradeguide.domain.auth.AuthIdentity;
import com.tradeguide.domain.auth.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {
    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
