package io.github.ruok0214.ingametimer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
    boolean minimal;
    int opacity = 100;
    double hudX;
    double hudY;
    private Corner corner;

    static boolean validDuration(int seconds) {
        return seconds >= 1 && seconds <= 359_999;
    }

    static Store load() {
        if (!Files.isRegularFile(FILE)) return new Store();
        try (var reader = Files.newBufferedReader(FILE)) {
            var json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) return new Store();
            Store store = JSON.fromJson(json, Store.class);
            if (!json.getAsJsonObject().has("game")) store.game = JSON.fromJson(json, TimerSettings.class);
            if (store.game == null) store.game = new TimerSettings();
            store.game.normalize();
            if (!json.getAsJsonObject().has("real") || store.real == null) {
                store.real = new TimerSettings();
                store.real.hudX = store.game.hudX < 0.5 ? 1 : 0;
                store.real.hudY = store.game.hudY < 0.5 ? 1 : 0;
            }
            store.real.normalize();
            return store;
        } catch (IOException | JsonParseException exception) {
            LOGGER.warn("Could not load timer settings", exception);
        }
        return new Store();
    }

    private void normalize() {
        if (!validDuration(seconds)) seconds = 60;
        if (corner != null) {
            hudX = corner.right ? 1 : 0;
            hudY = corner.bottom ? 1 : 0;
            corner = null;
        }
        opacity = Math.clamp(opacity, 0, 100);
        hudX = Math.clamp(hudX, 0, 1);
        hudY = Math.clamp(hudY, 0, 1);
    }

    static final class Store {
        TimerSettings game = new TimerSettings();
        TimerSettings real = new TimerSettings();
        boolean legacyBindingsCleared;

        Store() {
            real.hudX = 1;
            real.hudY = 1;
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
}
