package vn.restauranttycoon.build;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class StageManifestLoader {
    public StageManifest load(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
        if (yaml.getInt("schema-version") != 1) {
            throw new IllegalArgumentException(
                    "Unsupported stage manifest schema-version: " + yaml.getInt("schema-version"));
        }
        ConfigurationSection plots = requiredSection(yaml, "plots");
        List<AuthoredStage> stages = new ArrayList<>();
        for (String plotId : plots.getKeys(false)) {
            ConfigurationSection plot = requiredSection(plots, plotId);
            ConfigurationSection stageSection = requiredSection(plot, "stages");
            for (String revisionText : stageSection.getKeys(false)) {
                long revision;
                try {
                    revision = Long.parseLong(revisionText);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "Stage revision must be a positive integer: " + revisionText, exception);
                }
                stages.add(readStage(plotId, revision, requiredSection(stageSection, revisionText)));
            }
        }
        return new StageManifest(stages);
    }

    private AuthoredStage readStage(String plotId, long revision, ConfigurationSection section) {
        ConfigurationSection volumeSection = requiredSection(section, "volume");
        StageVolume volume = new StageVolume(
                requiredPositiveInt(volumeSection, "x"),
                requiredPositiveInt(volumeSection, "y"),
                requiredPositiveInt(volumeSection, "z"));
        List<?> rawBlocks = section.getList("blocks");
        if (rawBlocks == null) {
            throw new IllegalArgumentException("Missing blocks list for " + plotId + " revision " + revision);
        }
        List<StageBlock> blocks = new ArrayList<>();
        for (Object rawBlock : rawBlocks) {
            if (!(rawBlock instanceof String encoded)) {
                throw new IllegalArgumentException("Stage block entries must be strings");
            }
            blocks.add(parseBlock(encoded));
        }
        return new AuthoredStage(plotId, revision, volume, blocks);
    }

    private StageBlock parseBlock(String encoded) {
        String[] parts = encoded.trim().split("\\s+", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException(
                    "Stage block must use '<x> <y> <z> <block-data>': " + encoded);
        }
        try {
            return new StageBlock(
                    new BlockOffset(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])),
                    parts[3]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stage block coordinate: " + encoded, exception);
        }
    }

    private ConfigurationSection requiredSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Missing configuration section: " + path);
        }
        return section;
    }

    private int requiredPositiveInt(ConfigurationSection section, String path) {
        if (!section.isInt(path) || section.getInt(path) < 1) {
            throw new IllegalArgumentException(path + " must be a positive integer");
        }
        return section.getInt(path);
    }
}
