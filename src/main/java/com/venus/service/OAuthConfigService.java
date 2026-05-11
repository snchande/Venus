package com.venus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venus.model.OAuthConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
public class OAuthConfigService {

    private static final Logger log = LoggerFactory.getLogger(OAuthConfigService.class);
    private static final String FILE = "oauth-config.json";

    @Value("${venus.data.dir:data}")
    private String dataDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OAuthConfig config;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get(dataDir));
        Path file = configPath();
        if (Files.exists(file)) {
            try {
                config = objectMapper.readValue(file.toFile(), OAuthConfig.class);
            } catch (IOException e) {
                log.warn("Could not read oauth-config.json, using defaults: {}", e.getMessage());
                config = new OAuthConfig();
            }
        } else {
            config = new OAuthConfig();
        }
    }

    public OAuthConfig load() {
        return config;
    }

    public OAuthConfig save(OAuthConfig updated) throws IOException {
        this.config = updated;
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath().toFile(), updated);
        return updated;
    }

    private Path configPath() {
        return Paths.get(dataDir, FILE);
    }
}
