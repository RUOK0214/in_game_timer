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
    private final TimerClient.KeyBindings bindings;
    private KeyMapping pending;

    TimerKeyScreen(Screen parent, TimerSession timer, TimerClient.KeyBindings bindings) {
        super(text("keybindings.title", timer.title()));
        this.parent = parent;
        this.bindings = bindings;
    }

    @Override
    protected void init() {
        int x = width / 2 - 112;
        int y = top();
        bindingButton("toggle", bindings.toggle(), x, y + 24);
        bindingButton("reset", bindings.reset(), x, y + 46);
        bindingButton("menu", bindings.menu(), x, y + 68);
        addRenderableWidget(Button.builder(text("done"), button -> onClose())
                .bounds(x, y + 100, 224, 20).build());
    }

    private void bindingButton(String action, KeyMapping binding, int x, int y) {
        var key = binding == pending ? text("keybindings.waiting")
                : binding.isUnbound() ? text("keybindings.unbound") : binding.getTranslatedKeyMessage();
        addRenderableWidget(Button.builder(text("keybindings." + action, key), button -> {
            pending = binding;
            button.setMessage(text("keybindings." + action, text("keybindings.waiting")));
        }).bounds(x, y, 224, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pending == null) return super.keyPressed(event);
        bind(event.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(event));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (pending == null) return super.mouseClicked(event, doubleClick);
        bind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
        return true;
    }

    private void bind(InputConstants.Key key) {
        pending.setKey(key);
        pending = null;
        KeyMapping.resetMapping();
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
        return Math.max(0, (height - 120) / 2);
    }
}
