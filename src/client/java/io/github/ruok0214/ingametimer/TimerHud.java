package io.github.ruok0214.ingametimer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.ruok0214.ingametimer.TimerClient.text;

final class TimerHud {
    private static final int MARGIN = 4;
    private static final int PADDING = 5;
    private static final int ROW_HEIGHT = 11;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 2;
    private static final int MIN_WIDTH = 178;
    private static final int CHAT_INPUT_HEIGHT = 16;
    private static final String SEPARATOR = " : ";

    private final TimerSession timer;
    private final TimerSettings settings;

    private TimerHud(TimerSession timer) {
        this.timer = timer;
        settings = timer.settings;
    }

    static void register(TimerClient owner) {
        var panels = List.of(new TimerHud(owner.game), new TimerHud(owner.real));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, TimerMod.id("timer"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.gui.screen() instanceof ChatScreen) return;
            for (TimerHud panel : panels) {
                if (!panel.settings.hudVisible) continue;
                var layout = panel.layout(client.font, graphics.guiWidth(), graphics.guiHeight());
                panel.draw(graphics, client.font, layout, false);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (client.level == null || !(screen instanceof ChatScreen chat)) return;
            var controls = panels.stream().filter(panel -> panel.settings.hudVisible)
                    .map(panel -> panel.new ChatControls(owner, chat, client.font)).toList();
            if (controls.isEmpty()) return;
            controls.forEach(ChatControls::addButtons);
            ScreenEvents.beforeExtract(chat).register((s, graphics, x, y, delta) ->
                    controls.forEach(ChatControls::positionButtons));
            ScreenEvents.afterExtract(chat).register((s, graphics, x, y, delta) ->
                    controls.forEach(control -> control.draw(graphics)));
            ScreenMouseEvents.allowMouseClick(chat).register((s, mouse) ->
                    controls.reversed().stream().allMatch(control -> control.click(mouse)));
            ScreenMouseEvents.allowMouseDrag(chat).register((s, mouse, dx, dy) ->
                    controls.reversed().stream().allMatch(control -> control.drag(mouse)));
            ScreenMouseEvents.allowMouseRelease(chat).register((s, mouse) ->
                    mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || controls.stream().noneMatch(ChatControls::finishDrag));
            ScreenEvents.remove(chat).register(s -> controls.forEach(ChatControls::finishDrag));
        });
    }

    private Layout layout(Font font, int screenWidth, int screenHeight) {
        List<Row> rows = rows(System.nanoTime());
        int valueWidth = rows.stream().mapToInt(row -> font.width(row.value())).max().orElse(0);
        int labelWidth = 0;
        int textWidth = valueWidth;
        int width = Math.max(28, textWidth + PADDING * 2);
        if (!settings.minimal) {
            labelWidth = rows.stream().mapToInt(row -> font.width(row.label())).max().orElse(0);
            textWidth += labelWidth + font.width(SEPARATOR);
            width = Math.max(MIN_WIDTH, Math.max(textWidth, font.width(timer.title())) + PADDING * 2);
        }
        int rowCount = settings.minimal ? Math.max(1, rows.size()) : rows.size() + 1;
        int height = PADDING * 2 + rowCount * ROW_HEIGHT - 2;
        int travelX = Math.max(0, screenWidth - MARGIN * 2 - width);
        int travelY = Math.max(0, screenHeight - MARGIN * 2 - CHAT_INPUT_HEIGHT - height);
        int x = MARGIN + (int) Math.round(settings.hudX * travelX);
        int y = MARGIN + (int) Math.round(settings.hudY * travelY);
        int separatorX = x + (width - textWidth) / 2 + labelWidth;
        return new Layout(x, y, width, height, travelX, travelY, separatorX, rows);
    }

    private void draw(GuiGraphicsExtractor graphics, Font font, Layout layout, boolean editing) {
        if (!editing && (layout.rows().isEmpty() || settings.opacity == 0)) return;
        int alpha = Math.round(settings.opacity * 255 / 100f);
        int background = (alpha * 160 / 255) << 24;
        int x = layout.x();
        int y = layout.y();
        graphics.fill(x, y, x + layout.width(), y + layout.height(), background);
        if (editing) graphics.outline(x, y, layout.width(), layout.height(), 0xCFFFFFFF);
        if (alpha < 4) return;
        int color = alpha << 24 | 0xFFFFFF;
        int separatorWidth = font.width(SEPARATOR);
        int colonX = layout.separatorX();
        y += PADDING;
        if (!settings.minimal) {
            Component title = timer.title();
            graphics.text(font, title, x + (layout.width() - font.width(title)) / 2, y, color, false);
            y += ROW_HEIGHT;
        }
        for (Row row : layout.rows()) {
            if (settings.minimal) {
                graphics.text(font, row.value(), x + layout.width() - PADDING - font.width(row.value()), y, color, false);
            } else {
                graphics.text(font, row.label(), colonX - font.width(row.label()), y, color, false);
                graphics.text(font, SEPARATOR, colonX, y, color, false);
                graphics.text(font, row.value(), colonX + separatorWidth, y, color, false);
            }
            y += ROW_HEIGHT;
        }
    }

    private List<Row> rows(long now) {
        long ticks = timer.displayTicks(now);
        long nanos = timer.displayNanos(now);
        boolean available = timer.clock.isAvailable();
        Component unavailable = settings.minimal ? Component.literal("—") : text("hud.no_tick_source");
        List<Row> rows = new ArrayList<>(3);
        if (settings.gameTicks) rows.add(new Row(text("hud.game"), available
                ? Component.literal(Long.toString(ticks)) : unavailable));
        if (settings.redstoneTicks) rows.add(new Row(text("hud.redstone"), available
                ? Component.literal(ticks / 2 + (ticks % 2 == 0 ? "" : ".5")) : unavailable));
        if (settings.realTime) rows.add(new Row(text("hud.real"), available
                ? Component.literal(formatTime(nanos / 1_000_000)) : unavailable));
        return rows;
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d",
                seconds / 3600, seconds / 60 % 60, seconds % 60, millis % 1000);
    }

    private record Row(Component label, Component value) {}

    private record Layout(int x, int y, int width, int height, int travelX, int travelY,
                          int separatorX, List<Row> rows) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final class ChatControls {
        private final ChatScreen screen;
        private final Font font;
        private final List<Button> buttons;
        private Layout currentLayout;
        private boolean dragging;
        private double grabX;
        private double grabY;

        private ChatControls(TimerClient owner, ChatScreen screen, Font font) {
            this.screen = screen;
            this.font = font;
            buttons = List.of(
                    Button.builder(timer.toggleLabel(), button -> {
                        timer.toggle();
                        button.setMessage(timer.toggleLabel());
                    }).build(),
                    Button.builder(text("reset"), button -> {
                        timer.clock.reset();
                        refreshLabel();
                    }).build(),
                    Button.builder(text("settings"), button -> owner.requestMenu(timer)).build());
            buttons.forEach(button -> button.setTooltip(Tooltip.create(timer.title())));
        }

        void addButtons() {
            Screens.getWidgets(screen).addAll(buttons);
            positionButtons();
        }

        private void draw(GuiGraphicsExtractor graphics) {
            TimerHud.this.draw(graphics, font, currentLayout, true);
        }

        private void refreshLabel() {
            buttons.getFirst().setMessage(timer.toggleLabel());
        }

        private void positionButtons() {
            currentLayout = layout(font, screen.width, screen.height);
            refreshLabel();
            int controlsWidth = Math.max(MIN_WIDTH, currentLayout.width());
            int buttonX = Math.clamp(currentLayout.x() + (currentLayout.width() - controlsWidth) / 2,
                    MARGIN, Math.max(MARGIN, screen.width - controlsWidth - MARGIN));
            int buttonY = currentLayout.y() + currentLayout.height() + BUTTON_GAP;
            if (buttonY + BUTTON_HEIGHT > screen.height - CHAT_INPUT_HEIGHT) {
                buttonY = currentLayout.y() - BUTTON_HEIGHT - BUTTON_GAP;
            }
            int available = controlsWidth - BUTTON_GAP * (buttons.size() - 1);
            for (int i = 0; i < buttons.size(); i++) {
                Button button = buttons.get(i);
                int width = available * (i + 1) / buttons.size() - available * i / buttons.size();
                button.setX(buttonX);
                button.setY(buttonY);
                button.setWidth(width);
                buttonX += width + BUTTON_GAP;
            }
        }

        private boolean click(MouseButtonEvent mouse) {
            if (mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            for (Button button : buttons) {
                if (button.mouseClicked(mouse, false)) return false;
            }
            if (!currentLayout.contains(mouse.x(), mouse.y())) return true;
            dragging = true;
            grabX = mouse.x() - currentLayout.x();
            grabY = mouse.y() - currentLayout.y();
            return false;
        }

        private boolean drag(MouseButtonEvent mouse) {
            if (!dragging || mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            if (currentLayout.travelX() > 0) {
                settings.hudX = Math.clamp((mouse.x() - grabX - MARGIN) / currentLayout.travelX(), 0, 1);
            }
            if (currentLayout.travelY() > 0) {
                settings.hudY = Math.clamp((mouse.y() - grabY - MARGIN) / currentLayout.travelY(), 0, 1);
            }
            positionButtons();
            return false;
        }

        private boolean finishDrag() {
            if (!dragging) return false;
            dragging = false;
            timer.store.save();
            return true;
        }
    }
}
