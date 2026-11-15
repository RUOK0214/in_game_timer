package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.github.ruok0214.ingametimer.TimerClient.text;

final class TimerShortcuts {
    private static final int MODIFIERS = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT
            | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;

    private final TimerSettings.Store store;
    private final List<Binding> bindings = new ArrayList<>();
    private final Set<InputConstants.Key> captured = new HashSet<>();

    TimerShortcuts(TimerSettings.Store store) {
        this.store = store;
    }

    KeyMapping register(String action, KeyMapping.Category category, Runnable run) {
        var mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.in_game_timer." + action, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        bindings.add(new Binding(mapping, run));
        return mapping;
    }

    boolean dispatch(InputConstants.Key key, int action, int modifiers, boolean gameplay) {
        if (action == GLFW.GLFW_RELEASE) {
            captured.remove(key);
            return false;
        }
        if (action == GLFW.GLFW_REPEAT) return captured.contains(key);
        if (action != GLFW.GLFW_PRESS) return false;
        captured.remove(key);
        if (!gameplay) return false;

        int mask = mask(key, modifiers);
        boolean matched = false;
        for (Binding binding : bindings) {
            KeyMapping mapping = binding.mapping();
            if (!mapping.isUnbound() && mapping.matches(key) && modifiersFor(mapping) == mask) {
                binding.run().run();
                matched = true;
            }
        }
        // Leave native single-key actions alone, but keep a chord from also pressing its bare key.
        return matched && mask != 0 && captured.add(key);
    }

    void assign(KeyMapping mapping, InputConstants.Key key, int modifiers) {
        mapping.setKey(key);
        int mask = mask(key, modifiers);
        if (mask == 0 || mapping.isUnbound()) {
            store.chords.remove(mapping.getName());
        } else {
            store.chords.put(mapping.getName(), new Chord(mapping.saveString(), mask));
        }
        KeyMapping.resetMapping();
        store.save();
    }

    Component label(KeyMapping mapping) {
        if (mapping.isUnbound()) return text("keybindings.unbound");
        int mask = modifiersFor(mapping);
        var label = Component.empty();
        if ((mask & GLFW.GLFW_MOD_CONTROL) != 0) label.append(text("modifier.ctrl")).append("+");
        if ((mask & GLFW.GLFW_MOD_SHIFT) != 0) label.append(text("modifier.shift")).append("+");
        if ((mask & GLFW.GLFW_MOD_ALT) != 0) label.append(text("modifier.alt")).append("+");
        if ((mask & GLFW.GLFW_MOD_SUPER) != 0) label.append(text("modifier.super")).append("+");
        return label.append(mapping.getTranslatedKeyMessage());
    }

    private int modifiersFor(KeyMapping mapping) {
        Chord chord = store.chords.get(mapping.getName());
        return chord != null && mapping.saveString().equals(chord.key()) ? chord.modifiers() & MODIFIERS : 0;
    }

    private static int mask(InputConstants.Key key, int modifiers) {
        int ownModifier = key.getType() == InputConstants.Type.KEYSYM ? modifierKey(key.getValue()) : 0;
        return modifiers & MODIFIERS & ~ownModifier;
    }

    static int modifierKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_MOD_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> GLFW.GLFW_MOD_SUPER;
            default -> 0;
        };
    }

    record Chord(String key, int modifiers) {}
    private record Binding(KeyMapping mapping, Runnable run) {}
}
