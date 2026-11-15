package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import static io.github.ruok0214.ingametimer.TimerClient.text;

final class TimerKeyScreen extends Screen {
    private final Screen parent;
    private final TimerClient owner;
    private final TimerClient.KeyBindings bindings;
    private KeyMapping pending;
    private int pendingModifier = GLFW.GLFW_KEY_UNKNOWN;

    TimerKeyScreen(Screen parent, TimerSession timer, TimerClient owner) {
        super(text("keybindings.title", timer.title()));
        this.parent = parent;
        this.owner = owner;
        bindings = owner.bindings(timer);
    }

    @Override
    protected void init() {
        int x = width / 2 - 112;
        int y = top();
        bindingButton("toggle", bindings.toggle(), x, y + 24);
        bindingButton("reset", bindings.reset(), x, y + 46);
        bindingButton("menu", bindings.menu(), x, y + 68);
        bindingButton("hud", bindings.hud(), x, y + 90);
        bindingButton("both_huds", owner.bothHuds, x, y + 112);
        addRenderableWidget(Button.builder(text("done"), button -> onClose())
                .bounds(x, y + 144, 224, 20).build());
    }

    private void bindingButton(String action, KeyMapping binding, int x, int y) {
        var key = binding == pending ? text("keybindings.waiting")
                : owner.shortcuts.label(binding);
        addRenderableWidget(Button.builder(text("keybindings." + action, key), button -> {
            pending = binding;
            button.setMessage(text("keybindings." + action, text("keybindings.waiting")));
        }).bounds(x, y, 224, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pending == null) return super.keyPressed(event);
        if (TimerShortcuts.modifierKey(event.key()) != 0) {
            pendingModifier = event.key();
        } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            bind(InputConstants.UNKNOWN, 0);
        } else {
            bind(InputConstants.getKey(event), event.modifiers());
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (pending != null && event.key() == pendingModifier) {
            bind(InputConstants.getKey(event), event.modifiers());
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (pending == null) return super.mouseClicked(event, doubleClick);
        bind(InputConstants.Type.MOUSE.getOrCreate(event.button()), event.modifiers());
        return true;
    }

    private void bind(InputConstants.Key key, int modifiers) {
        owner.shortcuts.assign(pending, key, modifiers);
        pending = null;
        pendingModifier = GLFW.GLFW_KEY_UNKNOWN;
        minecraft.options.save();
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, top(), 0xFFFFFFFF);
        graphics.centeredText(font, text("keybindings.hint"), width / 2, top() + 11, 0xFFCCCCCC);
    }

    private int top() {
        return Math.max(0, (height - 164) / 2);
    }
}
