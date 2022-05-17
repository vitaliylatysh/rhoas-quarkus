package com.ozangunalp;

import java.time.Duration;

public class RhoasTokens {

    public String refresh_token;
    public String access_token;
    public Long access_expiration;
    public Long refresh_expiration;

    public RhoasTokens() {
    }

    boolean accessTokenIsValidFor(Duration duration) {
        return (access_expiration) - duration.toMillis() >= System.currentTimeMillis();
    }
    boolean refreshTokenIsValidFor(Duration duration) {
        return (refresh_expiration) - duration.toMillis() >= System.currentTimeMillis();
    }
}
