package com.create.productionline.line.mapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Process-wide shared mapping state. The dictionary is (re)loaded whenever a
 * server starts so that edits to
 * {@code config/create_productionline-mappings.json} apply without a restart of
 * the whole game.
 */
public final class Mappers {

    private static volatile MappingDictionary dictionary = new MappingDictionary();
    private static volatile RecipeMapper mapper = new RecipeMapper(dictionary);
    private static volatile String assemblyMode = "sequenced";

    private Mappers() {
    }

    public static RecipeMapper get() {
        return mapper;
    }

    public static MappingDictionary getDictionary() {
        return dictionary;
    }

    /** Assembly mode: {@code "sequenced"} (default) or {@code "mechanical"} (Mechanical Crafter). */
    public static String getAssemblyMode() {
        return assemblyMode;
    }

    /** Reloads the dictionary, optionally merging a JSON config file. */
    public static void reload(Path configDirectory) {
        MappingDictionary fresh = new MappingDictionary();
        String mode = "sequenced";
        if (configDirectory != null) {
            Path file = configDirectory.resolve(MappingDictionary.CONFIG_FILE);
            if (Files.isRegularFile(file)) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                    if (root.has("assemblyMode") && root.get("assemblyMode").isJsonPrimitive()) {
                        String m = root.get("assemblyMode").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
                        if (m.equals("mechanical") || m.equals("sequenced")) {
                            mode = m;
                        }
                    }
                    if (content.trim().equals(fresh.toPrettyJson())) {
                        writeTemplate(fresh, file);
                    } else {
                        fresh.applyUserConfig(root);
                        com.create.productionline.ProductionLineMod.LOGGER.info(
                                "Loaded user recipe mapping from {}", file.toAbsolutePath());
                    }
                } catch (IOException | RuntimeException e) {
                    com.create.productionline.ProductionLineMod.LOGGER.warn(
                            "Could not read {}: {}", file.toAbsolutePath(), e.toString());
                }
            } else {
                writeTemplate(fresh, file);
            }
        }
        dictionary = fresh;
        mapper = new RecipeMapper(fresh);
        assemblyMode = mode;
    }

    private static void writeTemplate(MappingDictionary dictionary, Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, dictionary.toPrettyJson(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // no config dir available — dictionary stays built-in only
        }
    }
}
