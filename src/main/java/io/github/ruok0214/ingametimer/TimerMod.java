package io.github.ruok0214.ingametimer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class TimerMod implements ModInitializer {
    private long lastBroadcast = Long.MIN_VALUE;

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("in_game_timer", path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(WorldTick.TYPE, WorldTick.CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.player, WorldTick.TYPE)) {
                sender.sendPacket(new WorldTick(server.overworld().getGameTime()));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Vanilla advances this counter only when the world simulates a tick.
            long tick = server.overworld().getGameTime();
            if (tick == lastBroadcast) return;
            lastBroadcast = tick;
            WorldTick payload = new WorldTick(tick);
            for (var player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, WorldTick.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });
    }

    record WorldTick(long tick) implements CustomPacketPayload {
        static final Type<WorldTick> TYPE = new Type<>(id("world_tick"));
        static final StreamCodec<RegistryFriendlyByteBuf, WorldTick> CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeVarLong(payload.tick()),
                buffer -> new WorldTick(buffer.readVarLong())
        );

        @Override
        public Type<WorldTick> type() {
            return TYPE;
        }
    }
}
