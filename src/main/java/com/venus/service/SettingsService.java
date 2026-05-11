package com.venus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.venus.model.VenusSettings;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages application settings.
 *
 * Settings are stored in data/settings.json and loaded on startup.
 * The ANTHROPIC_API_KEY environment variable takes precedence over stored settings.
 *
 * data/settings.json is in .gitignore - API keys won't be committed.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    @Value("${venus.data.dir:data}")
    private String dataDir;

    private final ObjectMapper objectMapper;
    private VenusSettings settings;

    public SettingsService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        settings = loadSettings();

        // Environment variable takes precedence over stored API key
        String envApiKey = System.getenv("ANTHROPIC_API_KEY");
        if (envApiKey != null && !envApiKey.isBlank()) {
            settings.setAnthropicApiKey(envApiKey);
            log.info("Using ANTHROPIC_API_KEY from environment variable");
        }
    }

    public VenusSettings getSettings() {
        return settings;
    }

    public VenusSettings updateSettings(VenusSettings newSettings) {
        // Preserve API key if new settings has empty key but we have one stored
        if ((newSettings.getAnthropicApiKey() == null || newSettings.getAnthropicApiKey().isBlank())
                && settings.getAnthropicApiKey() != null && !settings.getAnthropicApiKey().isBlank()) {
            newSettings.setAnthropicApiKey(settings.getAnthropicApiKey());
        }
        this.settings = newSettings;
        saveSettings(newSettings);
        return settings;
    }

    public String getApiKey() {
        return settings.getAnthropicApiKey();
    }

    private VenusSettings loadSettings() {
        Path settingsPath = getSettingsPath();
        if (Files.exists(settingsPath)) {
            try {
                VenusSettings loaded = objectMapper.readValue(settingsPath.toFile(), VenusSettings.class);
                log.info("Loaded settings from {}", settingsPath);
                return loaded;
            } catch (IOException e) {
                log.warn("Failed to load settings, using defaults: {}", e.getMessage());
            }
        }
        log.info("No settings file found, using defaults");
        return new VenusSettings();
    }

    private void saveSettings(VenusSettings settingsToSave) {
        Path settingsPath = getSettingsPath();
        try {
            Files.createDirectories(settingsPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(settingsPath.toFile(), settingsToSave);
            log.debug("Saved settings to {}", settingsPath);
        } catch (IOException e) {
            log.error("Failed to save settings: {}", e.getMessage());
        }
    }

    private Path getSettingsPath() {
        return Paths.get(dataDir, "settings.json");
    }
}
