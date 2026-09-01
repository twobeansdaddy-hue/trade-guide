package com.tradeguide.service.auth;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleOidcUserService extends OidcUserService {
    private final AuthIdentityService authIdentityService;

    public GoogleOidcUserService(AuthIdentityService authIdentityService) {
        this.authIdentityService = authIdentityService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);
        authIdentityService.resolveGoogleIdentity(oidcUser.getSubject(), oidcUser.getEmail(), oidcUser.getFullName());
        return oidcUser;
    }
}
