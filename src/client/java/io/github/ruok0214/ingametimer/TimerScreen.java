package io.github.ruok0214.ingametimer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

import static io.github.ruok0214.ingametimer.TimerClient.text;

final class TimerScreen extends Screen {
    private final TimerClient timer;
    private EditBox duration;
    private Button apply;
    private Button toggle;

    TimerScreen(TimerClient timer) {
        super(text("title"));
        this.timer = timer;
    }

    @Override
    protected void init() {
        TimerSettings settings = timer.settings;
        int x = width / 2 - 105;
        int y = (height - 172) / 2;
        addRenderableWidget(Button.builder(modeLabel(), button -> {
            settings.countdown = !settings.countdown;
            resetClock();
            settings.save();
            button.setMessage(modeLabel());
        }).bounds(x, y + 15, 210, 20).build());

        duration = new EditBox(font, x, y + 39, 100, 20, text("duration"));
        duration.setMaxLength(6);
        duration.setValue(Integer.toString(settings.seconds));
        addRenderableWidget(duration);
        apply = addRenderableWidget(Button.builder(text("apply_duration"), button -> {
            settings.seconds = inputDuration();
            resetClock();
            settings.save();
            updateApplyButton();
        }).bounds(x + 104, y + 39, 106, 20).build());
        duration.setResponder(value -> updateApplyButton());
        updateApplyButton();

        visibilityButton("game", settings.gameTicks, value -> settings.gameTicks = value, x, y + 63);
        visibilityButton("redstone", settings.redstoneTicks, value -> settings.redstoneTicks = value, x, y + 85);
        visibilityButton("real", settings.realTime, value -> settings.realTime = value, x, y + 107);
        addRenderableWidget(Button.builder(cornerLabel(), button -> {
            settings.corner = settings.corner.next();
            settings.save();
            button.setMessage(cornerLabel());
        }).bounds(x, y + 129, 210, 20).build());

        toggle = addRenderableWidget(Button.builder(toggleLabel(), button -> {
            timer.toggle();
            button.setMessage(toggleLabel());
        })
                .bounds(x, y + 153, 69, 20).build());
        addRenderableWidget(Button.builder(text("reset"), button -> resetClock())
                .bounds(x + 71, y + 153, 68, 20).build());
        addRenderableWidget(Button.builder(text("done"), button -> onClose())
                .bounds(x + 141, y + 153, 69, 20).build());
    }

    private void visibilityButton(String key, boolean initial, Consumer<Boolean> update, int x, int y) {
        addRenderableWidget(new CycleButton.Builder<Boolean>(
                value -> text("show." + key, text(value ? "on" : "off")), () -> initial)
                .withValues(true, false)
                .displayOnlyValue()
                .create(x, y, 210, 20, text("show." + key, ""), (button, value) -> {
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

    private Component cornerLabel() {
        return text("position", text("corner." + timer.settings.corner.name().toLowerCase(Locale.ROOT)));
    }

    private Component toggleLabel() {
        return text(timer.clock.isRunning() ? "stop" : "start");
    }

    private void resetClock() {
        timer.clock.reset();
        toggle.setMessage(toggleLabel());
    }

    @Override
    public void tick() {
        toggle.setMessage(toggleLabel());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, (height - 172) / 2, 0xFFFFFFFF);
    }
}
