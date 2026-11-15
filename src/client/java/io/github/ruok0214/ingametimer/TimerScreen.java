package io.github.ruok0214.ingametimer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

import static io.github.ruok0214.ingametimer.TimerClient.text;

final class TimerScreen extends Screen {
    private static final int PANEL_WIDTH = 224;
    private static final int PANEL_HEIGHT = 151;
    private final TimerClient timer;
    private final int initialOpacity;
    private EditBox duration;
    private Button apply;
    private Button toggle;

    TimerScreen(TimerClient timer) {
        super(text("title"));
        this.timer = timer;
        initialOpacity = timer.settings.opacity;
    }

    @Override
    protected void init() {
        TimerSettings settings = timer.settings;
        int x = (width - PANEL_WIDTH) / 2;
        int y = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(Button.builder(modeLabel(), button -> {
            settings.countdown = !settings.countdown;
            resetClock();
            settings.save();
            button.setMessage(modeLabel());
        }).bounds(x, y + 15, PANEL_WIDTH, 20).build());

        duration = new EditBox(font, x, y + 39, 110, 20, text("duration"));
        duration.setMaxLength(6);
        duration.setValue(Integer.toString(settings.seconds));
        addRenderableWidget(duration);
        apply = addRenderableWidget(Button.builder(text("apply_duration"), button -> {
            settings.seconds = inputDuration();
            resetClock();
            settings.save();
            updateApplyButton();
        }).bounds(x + 114, y + 39, 110, 20).build());
        duration.setResponder(value -> updateApplyButton());
        updateApplyButton();

        visibilityButton("game", settings.gameTicks, value -> settings.gameTicks = value, x, y + 63);
        visibilityButton("redstone", settings.redstoneTicks, value -> settings.redstoneTicks = value, x + 114, y + 63);
        visibilityButton("real", settings.realTime, value -> settings.realTime = value, x, y + 87);
        visibilityButton("hud", settings.hudVisible, value -> settings.hudVisible = value, x + 114, y + 87)
                .setTooltip(Tooltip.create(text("drag_hint")));
        addRenderableWidget(new AbstractSliderButton(x, y + 111, PANEL_WIDTH, 20,
                text("opacity", settings.opacity), settings.opacity / 100.0) {
            @Override
            protected void updateMessage() {
                setMessage(text("opacity", (int) Math.round(value * 100)));
            }

            @Override
            protected void applyValue() {
                settings.opacity = (int) Math.round(value * 100);
            }
        });

        toggle = addRenderableWidget(Button.builder(timer.toggleLabel(), button -> {
            timer.toggle();
            button.setMessage(timer.toggleLabel());
        })
                .bounds(x, y + 135, 72, 20).build());
        addRenderableWidget(Button.builder(text("reset"), button -> resetClock())
                .bounds(x + 76, y + 135, 72, 20).build());
        addRenderableWidget(Button.builder(text("done"), button -> onClose())
                .bounds(x + 152, y + 135, 72, 20).build());
    }

    private CycleButton<Boolean> visibilityButton(String key, boolean initial, Consumer<Boolean> update, int x, int y) {
        return addRenderableWidget(new CycleButton.Builder<Boolean>(
                value -> text("show." + key, text(value ? "on" : "off")), () -> initial)
                .withValues(true, false)
                .displayOnlyValue()
                .create(x, y, 110, 20, text("show." + key, ""), (button, value) -> {
                    update.accept(value);
                    timer.settings.save();
                }));
    }

    private int inputDuration() {
        try {
            return Integer.parseInt(duration.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void updateApplyButton() {
        int value = inputDuration();
        apply.active = TimerSettings.validDuration(value) && value != timer.settings.seconds;
    }

    private Component modeLabel() {
        return text(timer.settings.countdown ? "mode.countdown" : "mode.stopwatch");
    }

    private void resetClock() {
        timer.clock.reset();
        toggle.setMessage(timer.toggleLabel());
    }

    @Override
    public void tick() {
        toggle.setMessage(timer.toggleLabel());
    }

    @Override
    public void removed() {
        if (timer.settings.opacity != initialOpacity) timer.settings.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, (height - PANEL_HEIGHT) / 2, 0xFFFFFFFF);
    }
}
