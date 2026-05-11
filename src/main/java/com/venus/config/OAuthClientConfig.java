package com.venus.config;

import com.venus.model.OAuthConfig;
import com.venus.service.OAuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmatically registers OAuth2 providers from data/oauth-config.json.
 * Credentials are loaded at startup. Changing them requires a server restart.
 */
@Configuration
public class OAuthClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuthClientConfig.class);

    @Autowired
    private OAuthConfigService oauthConfigService;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        OAuthConfig cfg = oauthConfigService.load();
        List<ClientRegistration> registrations = new ArrayList<>();

        if (cfg.isGoogleConfigured()) {
            registrations.add(googleRegistration(cfg));
            log.info("[Auth] Google OAuth2 provider enabled");
        }
        if (cfg.isMicrosoftConfigured()) {
            registrations.add(microsoftRegistration(cfg));
            log.info("[Auth] Microsoft OAuth2 provider enabled");
        }
        if (cfg.isFacebookConfigured()) {
            registrations.add(facebookRegistration(cfg));
            log.info("[Auth] Facebook OAuth2 provider enabled");
        }

        if (registrations.isEmpty()) {
            log.info("[Auth] No OAuth2 providers configured — local mode only");
            // Return a dummy repository (no providers); OAuth login will not be invoked
            return registrationId -> null;
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    // ── Provider builders ────────────────────────────────────────

    private ClientRegistration googleRegistration(OAuthConfig cfg) {
        return ClientRegistration.withRegistrationId("google")
                .clientId(cfg.getGoogleClientId())
                .clientSecret(cfg.getGoogleClientSecret())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL)
                .clientName("Google")
                .build();
    }

    private ClientRegistration microsoftRegistration(OAuthConfig cfg) {
        String tenant = cfg.getMicrosoftTenantId();
        return ClientRegistration.withRegistrationId("microsoft")
                .clientId(cfg.getMicrosoftClientId())
                .clientSecret(cfg.getMicrosoftClientSecret())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token")
                .jwkSetUri("https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys")
                .userInfoUri("https://graph.microsoft.com/oidc/userinfo")
                .userNameAttributeName("sub")
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL)
                .clientName("Microsoft")
                .build();
    }

    private ClientRegistration facebookRegistration(OAuthConfig cfg) {
        return ClientRegistration.withRegistrationId("facebook")
                .clientId(cfg.getFacebookClientId())
                .clientSecret(cfg.getFacebookClientSecret())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://www.facebook.com/v16.0/dialog/oauth")
                .tokenUri("https://graph.facebook.com/v16.0/oauth/access_token")
                .userInfoUri("https://graph.facebook.com/me?fields=id,name,email,picture")
                .userNameAttributeName("id")
                .scope("public_profile", "email")
                .clientName("Facebook")
                .build();
    }
}
