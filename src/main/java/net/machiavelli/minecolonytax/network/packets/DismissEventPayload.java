package net.machiavelli.minecolonytax.network.packets;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.Action;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.events.random.EventLogEntry;
import net.machiavelli.minecolonytax.events.random.RandomEventManager;
import net.machiavelli.minecolonytax.gui.data.ColonyTaxData;
import net.machiavelli.minecolonytax.gui.data.VassalIncomeData;
import net.machiavelli.minecolonytax.server.ColonyDataCollector;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sent client -> server to remove a single entry from a colony's event log.
 *
 * <p>Server verifies ACCESS_HUTS permission, removes the entry via
 * {@link RandomEventManager#dismissFromEventLog(int, String)}, then pushes a refreshed
 * {@link ColonyDataResponsePayload} so the GUI reflects the removal immediately.
 *
 * <p>NeoForge analog of the Forge {@code DismissEventPacket}. Mirrors {@code DismissSpyMissionPayload}:
 * a record carrying the dismiss target, a composite {@link StreamCodec}, and a server-side handle()
 * that mutates state and re-sends the data response.
 */
public record DismissEventPayload(int colonyId, String eventId) implements CustomPacketPayload {

    public static final Type<DismissEventPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MineColonyTax.MOD_ID, "dismiss_event"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DismissEventPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,         DismissEventPayload::colonyId,
            ByteBufCodecs.STRING_UTF8, DismissEventPayload::eventId,
            DismissEventPayload::new
        );

    @Override
    public Type<DismissEventPayload> type() {
        return TYPE;
    }

    public static void handle(DismissEventPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.getServer() == null) return;

            // Was pinned to the overworld, so events could not be dismissed for a colony in
            // any other dimension.
            IColony colony = net.machiavelli.minecolonytax.util.ColonyLookup.byId(payload.colonyId(), player);
            if (colony == null) return;

            if (!colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS)) return;

            RandomEventManager.dismissFromEventLog(payload.colonyId(), payload.eventId());

            // Refresh colony data so the GUI reflects the removal immediately.
            // Must rebuild the event-log map the same way RequestColonyDataPayload does so the
            // client's Events view stays consistent after the dismiss.
            List<ColonyTaxData> colonyData = ColonyDataCollector.collectColonyData(player);
            List<VassalIncomeData> vassalData = ColonyDataCollector.collectVassalIncomeData(player);
            Map<Integer, List<EventLogEntry>> eventLogData = new HashMap<>();
            for (ColonyTaxData data : colonyData) {
                eventLogData.put(data.getColonyId(),
                        RequestColonyDataPayload.buildEventLog(data.getColonyId(), player));
            }

            PacketDistributor.sendToPlayer(player,
                    new ColonyDataResponsePayload(colonyData, vassalData, eventLogData));
        });
    }
}
