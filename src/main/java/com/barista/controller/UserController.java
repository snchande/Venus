package com.barista.controller;

import com.barista.model.OAuthConfig;
import com.barista.model.UserProfile;
import com.barista.service.OAuthConfigService;
import com.barista.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints for user identity and OAuth configuration.
 *
 * GET  /api/user/me                — current user (or guest info if not logged in)
 * PUT  /api/user/me/email          — save email address
 * GET  /api/user/oauth-config      — OAuth provider config (credentials masked)
 * PUT  /api/user/oauth-config      — save OAuth credentials (requires restart to take effect)
 * POST /api/user/logout            — invalidate OAuth session
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final OAuthConfigService oauthConfigService;

    public UserController(UserService userService, OAuthConfigService oauthConfigService) {
        this.userService = userService;
        this.oauthConfigService = oauthConfigService;
    }

    /** Returns current user or a guest descriptor when not authenticated. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        UserProfile user = userService.getCurrentUser();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("authMode", userService.isLocalMode() ? "local" : "oauth");

        if (user != null) {
            resp.put("authenticated", true);
            resp.put("id", user.getId());
            resp.put("name", user.getName());
            resp.put("firstName", user.getFirstName());
            resp.put("email", user.getEmail());
            resp.put("avatarUrl", user.getAvatarUrl());
            resp.put("authProvider", user.getAuthProvider());
        } else {
            resp.put("authenticated", false);
        }
        return ResponseEntity.ok(resp);
    }

    /** Save / update the user's email address. */
    @PutMapping("/me/email")
    public ResponseEntity<?> updateEmail(@RequestBody Map<String, String> body) {
        UserProfile user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Email required"));
        try {
            UserProfile updated = userService.updateEmail(user.getId(), email.trim());
            return ResponseEntity.ok(Map.of("email", updated.getEmail()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Returns OAuth config with secrets masked for display. */
    @GetMapping("/oauth-config")
    public ResponseEntity<Map<String, Object>> getOAuthConfig() {
        OAuthConfig cfg = oauthConfigService.load();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("googleClientId", cfg.getGoogleClientId());
        resp.put("googleClientSecret", mask(cfg.getGoogleClientSecret()));
        resp.put("microsoftClientId", cfg.getMicrosoftClientId());
        resp.put("microsoftClientSecret", mask(cfg.getMicrosoftClientSecret()));
        resp.put("microsoftTenantId", cfg.getMicrosoftTenantId());
        resp.put("facebookClientId", cfg.getFacebookClientId());
        resp.put("facebookClientSecret", mask(cfg.getFacebookClientSecret()));
        resp.put("googleConfigured", cfg.isGoogleConfigured());
        resp.put("microsoftConfigured", cfg.isMicrosoftConfigured());
        resp.put("facebookConfigured", cfg.isFacebookConfigured());
        resp.put("restartRequired", true);
        return ResponseEntity.ok(resp);
    }

    /** Save OAuth credentials. A server restart is required for changes to take effect. */
    @PutMapping("/oauth-config")
    public ResponseEntity<?> saveOAuthConfig(@RequestBody Map<String, String> body) {
        try {
            OAuthConfig existing = oauthConfigService.load();

            // Only update secrets when the user submits a real value (not a masked placeholder)
            set(body, "googleClientId",      existing::setGoogleClientId);
            setSecret(body, "googleClientSecret",   existing.getGoogleClientSecret(), existing::setGoogleClientSecret);
            set(body, "microsoftClientId",   existing::setMicrosoftClientId);
            setSecret(body, "microsoftClientSecret",existing.getMicrosoftClientSecret(), existing::setMicrosoftClientSecret);
            set(body, "microsoftTenantId",   existing::setMicrosoftTenantId);
            set(body, "facebookClientId",    existing::setFacebookClientId);
            setSecret(body, "facebookClientSecret", existing.getFacebookClientSecret(), existing::setFacebookClientSecret);

            OAuthConfig saved = oauthConfigService.save(existing);
            return ResponseEntity.ok(Map.of(
                "saved", true,
                "restartRequired", true,
                "googleConfigured", saved.isGoogleConfigured(),
                "microsoftConfigured", saved.isMicrosoftConfigured(),
                "facebookConfigured", saved.isFacebookConfigured()
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Invalidate the current HTTP session (OAuth logout). */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(jakarta.servlet.http.HttpServletRequest request) throws Exception {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.ok(Map.of("loggedOut", true));
    }

    // ── helpers ──────────────────────────────────────────────────

    private static String mask(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.length() <= 8) return "••••••••";
        return s.substring(0, 4) + "••••••••" + s.substring(s.length() - 4);
    }

    private static void set(Map<String, String> body, String key, java.util.function.Consumer<String> setter) {
        String val = body.get(key);
        if (val != null) setter.accept(val);
    }

    private static void setSecret(Map<String, String> body, String key, String existing,
                                   java.util.function.Consumer<String> setter) {
        String val = body.get(key);
        if (val != null && !val.contains("••")) setter.accept(val); // ignore masked placeholder
    }
}
