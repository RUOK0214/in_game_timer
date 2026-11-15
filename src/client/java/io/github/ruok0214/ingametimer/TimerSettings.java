package io.github.ruok0214.ingametimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TimerSettings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("in_game_timer");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("in_game_timer.json");

    enum Corner {
        TOP_LEFT(false, false), TOP_RIGHT(true, false),
        BOTTOM_RIGHT(true, true), BOTTOM_LEFT(false, true);

        final boolean right;
        final boolean bottom;

        Corner(boolean right, boolean bottom) {
            this.right = right;
            this.bottom = bottom;
        }

    }

    boolean countdown;
    int seconds = 60;
    boolean gameTicks = true;
    boolean redstoneTicks = true;
    boolean realTime = true;
    boolean hudVisible = true;
    int opacity = 100;
    double hudX;
    double hudY;
    boolean legacyBindingsCleared;
    private Corner corner;

    static boolean validDuration(int seconds) {
        return seconds >= 1 && seconds <= 359_999;
    }

    static TimerSettings load() {
        if (!Files.isRegularFile(FILE)) return new TimerSettings();
        try (var reader = Files.newBufferedReader(FILE)) {
            TimerSettings settings = JSON.fromJson(reader, TimerSettings.class);
            if (settings != null) {
                if (!validDuration(settings.seconds)) settings.seconds = 60;
                if (settings.corner != null) {
                    settings.hudX = settings.corner.right ? 1 : 0;
                    settings.hudY = settings.corner.bottom ? 1 : 0;
                    settings.corner = null;
                }
                settings.opacity = Math.clamp(settings.opacity, 0, 100);
                settings.hudX = Math.clamp(settings.hudX, 0, 1);
                settings.hudY = Math.clamp(settings.hudY, 0, 1);
                return settings;
            }
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("Could not load timer settings", exception);
        }
        return new TimerSettings();
    }

    void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (var writer = Files.newBufferedWriter(FILE)) {
                JSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save timer settings", exception);
        }
    }
}
