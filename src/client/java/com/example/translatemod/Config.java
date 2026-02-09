package com.example.translatemod;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class Config {
    private static class ConfigStructure {
        public String apiKey = null;
    }

    private static final String MOD_ID = "translatormod";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String PLACEHOLDER = "<REPLACE THIS>";
    private static final Gson GSON = new Gson();

    private ConfigStructure inner = new ConfigStructure();

    public Config(String modId) {
        Path configFile = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(modId + ".json");

        try {
            if (Files.exists(configFile)) {
                String fileContent = Files.readString(configFile);
                this.inner = GSON.fromJson(fileContent, ConfigStructure.class);

                LOGGER.info("{}: config loaded successfully.", MOD_ID);
            } else {
                LOGGER.info("{}: Creating a config template.", MOD_ID);

                Files.createFile(configFile);
                Files.writeString(configFile,
                        "{\n  \"apiKey\": \"" + PLACEHOLDER + "\"\n}",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error("{}: Could not create config file.", MOD_ID);
        } catch (JsonSyntaxException e) {
            LOGGER.error("{}: Could not decode JSON config file.", MOD_ID);
        }
    }

    public Optional<String> getApiKey() {
        if (this.inner.apiKey.equals(PLACEHOLDER)) {
            return Optional.empty();
        }

        return Optional.of(this.inner.apiKey);
    }

    public void setApiKey(String key) {
        this.inner.apiKey = key;
    }
}
