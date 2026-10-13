package com.ultimate.inventory.filters;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;


class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final String email;

    public JwtAuthenticationToken(String email) {
        super(AuthorityUtils.createAuthorityList("user"));
        this.email = email;
    }

    @Override
    public @Nullable Object getCredentials() {
        return null;
    }

    @Override
    public @Nullable Object getPrincipal() {
        return this.email;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        throw new RuntimeException("YOu cannot mod this....");
    }
}
