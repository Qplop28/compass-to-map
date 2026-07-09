package com.kuronami.compasstomap.event;

import com.kuronami.compasstomap.CompassToMap;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

public class CompassWatcher {
    private static final Set<String> SEEN_RESULTS = new HashSet<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) event.player;

        // Once per second instead of every tick.
        if (player.ticksExisted % 20 != 0) {
            return;
        }

        checkStack(player, player.getHeldItemMainhand());
        checkStack(player, player.getHeldItemOffhand());
    }

    private void checkStack(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = stack.getItem().getRegistryName();
        if (itemId == null) {
            return;
        }

        if (!itemId.toString().equals("explorerscompass:explorerscompass")) {
            return;
        }

        CompoundNBT tag = stack.getTag();
        if (tag == null) {
            return;
        }

        if (!tag.contains("FoundX") || !tag.contains("FoundZ") || !tag.contains("StructureKey")) {
            return;
        }

        int state = tag.getInt("State");

        // Explorer's Compass uses State:2 when a result has been found.
        if (state != 2) {
            return;
        }

        int x = tag.getInt("FoundX");
        int z = tag.getInt("FoundZ");
        int y = Math.max(64, player.getPosition().getY());

        String structureKey = tag.getString("StructureKey");
        String prettyName = prettyStructureName(structureKey);

        String dimension = player.world.getDimensionKey().getLocation().toString();

        String resultKey = player.getUniqueID() + "|" + dimension + "|" + structureKey + "|" + x + "|" + z;
        if (!SEEN_RESULTS.add(resultKey)) {
            return;
        }

        CompassToMap.LOGGER.info(
                "Explorer's Compass found structure: structure={}, x={}, y={}, z={}, dimension={}",
                structureKey,
                x,
                y,
                z,
                dimension
        );

        createJourneyMapWaypoint(player, prettyName, dimension, x, y, z);
    }

    private void createJourneyMapWaypoint(ServerPlayerEntity player, String name, String dimension, int x, int y, int z) {
        String safeName = name.replace("\"", "'");
        String playerName = player.getScoreboardName();

        /*
         * JourneyMap 1.16.5 exposes a server command as:
         * /waypoint create "name" <dimension> <x> <y> <z> <color> <player> [announce]
         *
         * We run it without the slash because CommandManager expects the raw command text.
         */
        String command = "waypoint create \"" + safeName + "\" "
                + dimension + " "
                + x + " " + y + " " + z + " "
                + "aqua "
                + playerName + " "
                + "false";

        try {
            CompassToMap.LOGGER.info("Creating JourneyMap waypoint with command: /{}", command);

            player.getServer().getCommandManager().handleCommand(
                    player.getCommandSource().withPermissionLevel(4).withFeedbackDisabled(),
                    command
            );
        } catch (Exception exception) {
            CompassToMap.LOGGER.error("Failed to create JourneyMap waypoint automatically. Falling back to clickable chat.", exception);

            String journeyMapLocation = "[name:\"" + safeName + "\", x:" + x + ", y:" + y + ", z:" + z + "]";

            player.sendMessage(
                    new StringTextComponent("Compass to Map: " + journeyMapLocation),
                    player.getUniqueID()
            );
        }
    }

    private String prettyStructureName(String structureKey) {
        String name = structureKey;

        int colonIndex = name.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < name.length()) {
            name = name.substring(colonIndex + 1);
        }

        String[] parts = name.split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));

            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.length() == 0 ? structureKey : builder.toString();
    }
}