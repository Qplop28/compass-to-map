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
    private static final Set<String> LOGGED_UNKNOWN_TAGS = new HashSet<>();

    private static final String EXPLORERS_COMPASS_ID = "explorerscompass:explorerscompass";
    private static final String NATURES_COMPASS_ID = "naturescompass:naturescompass";

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

        String idString = itemId.toString();

        if (!idString.equals(EXPLORERS_COMPASS_ID) && !idString.equals(NATURES_COMPASS_ID)) {
            return;
        }

        CompoundNBT tag = stack.getTag();
        if (tag == null) {
            return;
        }

        if (idString.equals(EXPLORERS_COMPASS_ID)) {
            handleExplorersCompass(player, tag);
            return;
        }

        if (idString.equals(NATURES_COMPASS_ID)) {
            handleNaturesCompass(player, tag);
        }
    }

    private void handleExplorersCompass(ServerPlayerEntity player, CompoundNBT tag) {
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
        String prettyName = prettyNameFromKey(structureKey);

        createJourneyMapWaypoint(
                player,
                "Explorer's Compass",
                structureKey,
                prettyName,
                "aqua",
                x,
                y,
                z
        );
    }

    private void handleNaturesCompass(ServerPlayerEntity player, CompoundNBT tag) {
        if (!tag.contains("FoundX") || !tag.contains("FoundZ")) {
            return;
        }

        int state = tag.getInt("State");

        // Nature's Compass should also use State:2 when a result has been found.
        if (state != 2) {
            return;
        }

        String biomeKey = getFirstStringTag(tag, "BiomeID", "BiomeKey", "Biome", "BiomeName");

        if (biomeKey == null || biomeKey.isEmpty()) {
            logUnknownCompassTagOnce(player, "Nature's Compass", tag);
            return;
        }

        int x = tag.getInt("FoundX");
        int z = tag.getInt("FoundZ");
        int y = Math.max(64, player.getPosition().getY());

        String prettyName = prettyNameFromKey(biomeKey);

        createJourneyMapWaypoint(
                player,
                "Nature's Compass",
                biomeKey,
                prettyName,
                "green",
                x,
                y,
                z
        );
    }

    private void createJourneyMapWaypoint(
            ServerPlayerEntity player,
            String source,
            String targetKey,
            String name,
            String color,
            int x,
            int y,
            int z
    ) {
        String safeName = name.replace("\"", "'");
        String playerName = player.getScoreboardName();
        String dimension = player.world.getDimensionKey().getLocation().toString();

        String resultKey = player.getUniqueID()
                + "|" + source
                + "|" + dimension
                + "|" + targetKey
                + "|" + x
                + "|" + z;

        if (!SEEN_RESULTS.add(resultKey)) {
            return;
        }

        CompassToMap.LOGGER.info(
                "{} found target: target={}, x={}, y={}, z={}, dimension={}, color={}",
                source,
                targetKey,
                x,
                y,
                z,
                dimension,
                color
        );

        String command = "waypoint create \"" + safeName + "\" "
                + dimension + " "
                + x + " " + y + " " + z + " "
                + color + " "
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

    private String getFirstStringTag(CompoundNBT tag, String... keys) {
        for (String key : keys) {
            if (tag.contains(key)) {
                String value = tag.getString(key);

                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        return null;
    }

    private void logUnknownCompassTagOnce(ServerPlayerEntity player, String source, CompoundNBT tag) {
        String key = player.getUniqueID() + "|" + source + "|" + tag.toString();

        if (LOGGED_UNKNOWN_TAGS.add(key)) {
            CompassToMap.LOGGER.info("{} found result but biome key was unknown. Full tag: {}", source, tag);
        }
    }

    private String prettyNameFromKey(String key) {
        String name = key;

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

        return builder.length() == 0 ? key : builder.toString();
    }
}