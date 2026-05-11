package com.venus.model;

/**
 * OAuth2 provider credentials — stored in data/oauth-config.json (gitignored).
 * Leave fields blank to disable that provider.
 */
public class OAuthConfig {

    private String googleClientId = "";
    private String googleClientSecret = "";

    private String microsoftClientId = "";
    private String microsoftClientSecret = "";
    private String microsoftTenantId = "common";  // "common" = any MS account

    private String facebookClientId = "";
    private String facebookClientSecret = "";

    public OAuthConfig() {}

    public String getGoogleClientId()        { return googleClientId; }
    public void setGoogleClientId(String v)  { this.googleClientId = v; }

    public String getGoogleClientSecret()        { return googleClientSecret; }
    public void setGoogleClientSecret(String v)  { this.googleClientSecret = v; }

    public String getMicrosoftClientId()        { return microsoftClientId; }
    public void setMicrosoftClientId(String v)  { this.microsoftClientId = v; }

    public String getMicrosoftClientSecret()        { return microsoftClientSecret; }
    public void setMicrosoftClientSecret(String v)  { this.microsoftClientSecret = v; }

    public String getMicrosoftTenantId()        { return microsoftTenantId; }
    public void setMicrosoftTenantId(String v)  { this.microsoftTenantId = v == null || v.isBlank() ? "common" : v; }

    public String getFacebookClientId()        { return facebookClientId; }
    public void setFacebookClientId(String v)  { this.facebookClientId = v; }

    public String getFacebookClientSecret()        { return facebookClientSecret; }
    public void setFacebookClientSecret(String v)  { this.facebookClientSecret = v; }

    public boolean isGoogleConfigured()    { return configured(googleClientId, googleClientSecret); }
    public boolean isMicrosoftConfigured() { return configured(microsoftClientId, microsoftClientSecret); }
    public boolean isFacebookConfigured()  { return configured(facebookClientId, facebookClientSecret); }
    public boolean anyProviderConfigured() { return isGoogleConfigured() || isMicrosoftConfigured() || isFacebookConfigured(); }

    private static boolean configured(String id, String secret) {
        return id != null && !id.isBlank() && secret != null && !secret.isBlank();
    }
}
