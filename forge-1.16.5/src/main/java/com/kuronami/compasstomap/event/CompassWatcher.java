package com.kuronami.compasstomap.event;

import com.kuronami.compasstomap.CompassToMap;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CompassWatcher {
    private static final Map<String, String> ACTIVE_RESULTS = new HashMap<>();
    private static final Set<String> LOGGED_SKIPPED_RESULTS = new HashSet<>();

    private static final String EXPLORERS_COMPASS_ID = "explorerscompass:explorerscompass";
    private static final String NATURES_COMPASS_ID = "naturescompass:naturescompass";

    private static final Set<String> VANILLA_NETHER_STRUCTURES = new HashSet<>(Arrays.asList(
            "minecraft:fortress",
            "minecraft:bastion_remnant",
            "minecraft:nether_fossil"
    ));

    private static final Set<String> VANILLA_END_STRUCTURES = new HashSet<>(Arrays.asList(
            "minecraft:end_city"
    ));

    private static final Set<String> VANILLA_NETHER_BIOMES = new HashSet<>(Arrays.asList(
            "minecraft:nether_wastes",
            "minecraft:soul_sand_valley",
            "minecraft:crimson_forest",
            "minecraft:warped_forest",
            "minecraft:basalt_deltas"
    ));

    private static final Set<String> VANILLA_END_BIOMES = new HashSet<>(Arrays.asList(
            "minecraft:the_end",
            "minecraft:small_end_islands",
            "minecraft:end_midlands",
            "minecraft:end_highlands",
            "minecraft:end_barrens"
    ));

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

        checkStack(player, player.getHeldItemMainhand(), "main_hand");
        checkStack(player, player.getHeldItemOffhand(), "off_hand");
    }

    private void checkStack(ServerPlayerEntity player, ItemStack stack, String slotName) {
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
            clearActiveResult(player, idString, slotName);
            return;
        }

        if (idString.equals(EXPLORERS_COMPASS_ID)) {
            handleExplorersCompass(player, tag, slotName);
            return;
        }

        if (idString.equals(NATURES_COMPASS_ID)) {
            handleNaturesCompass(player, tag, slotName);
        }
    }

    private void handleExplorersCompass(ServerPlayerEntity player, CompoundNBT tag, String slotName) {
        int state = tag.getInt("State");

        // If the compass is not currently holding a completed result, allow the next result to create a waypoint.
        if (state != 2) {
            clearActiveResult(player, EXPLORERS_COMPASS_ID, slotName);
            return;
        }

        if (!tag.contains("FoundX") || !tag.contains("FoundZ") || !tag.contains("StructureKey")) {
            clearActiveResult(player, EXPLORERS_COMPASS_ID, slotName);
            return;
        }

        int x = tag.getInt("FoundX");
        int z = tag.getInt("FoundZ");
        int y = Math.max(64, player.getPosition().getY());

        String structureKey = tag.getString("StructureKey");
        String dimension = player.world.getDimensionKey().getLocation().toString();

        if (!isStructureValidForDimension(structureKey, dimension)) {
            logSkippedResult(player, "Explorer's Compass", structureKey, dimension, x, y, z);
            return;
        }

        String prettyName = prettyNameFromKey(structureKey);

        createJourneyMapWaypoint(
                player,
                EXPLORERS_COMPASS_ID,
                slotName,
                "Explorer's Compass",
                structureKey,
                prettyName,
                "aqua",
                x,
                y,
                z
        );
    }

    private void handleNaturesCompass(ServerPlayerEntity player, CompoundNBT tag, String slotName) {
        int state = tag.getInt("State");

        // If the compass is not currently holding a completed result, allow the next result to create a waypoint.
        if (state != 2) {
            clearActiveResult(player, NATURES_COMPASS_ID, slotName);
            return;
        }

        if (!tag.contains("FoundX") || !tag.contains("FoundZ")) {
            clearActiveResult(player, NATURES_COMPASS_ID, slotName);
            return;
        }

        String biomeKey = getFirstStringTag(tag, "BiomeID", "BiomeKey", "Biome", "BiomeName");

        if (biomeKey == null || biomeKey.isEmpty()) {
            CompassToMap.LOGGER.info("Nature's Compass found result but biome key was unknown. Full tag: {}", tag);
            return;
        }

        int x = tag.getInt("FoundX");
        int z = tag.getInt("FoundZ");
        int y = Math.max(64, player.getPosition().getY());

        String dimension = player.world.getDimensionKey().getLocation().toString();

        if (!isBiomeValidForDimension(biomeKey, dimension)) {
            logSkippedResult(player, "Nature's Compass", biomeKey, dimension, x, y, z);
            return;
        }

        String prettyName = prettyNameFromKey(biomeKey);

        createJourneyMapWaypoint(
                player,
                NATURES_COMPASS_ID,
                slotName,
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
            String compassId,
            String slotName,
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

        String activeKey = player.getUniqueID()
                + "|" + compassId
                + "|" + slotName;

        String resultKey = source
                + "|" + dimension
                + "|" + targetKey
                + "|" + x
                + "|" + z;

        String previousResult = ACTIVE_RESULTS.get(activeKey);

        if (resultKey.equals(previousResult)) {
            CompassToMap.LOGGER.info(
                    "Skipping already-active compass result: source={}, target={}, x={}, y={}, z={}, dimension={}",
                    source,
                    targetKey,
                    x,
                    y,
                    z,
                    dimension
            );
            return;
        }

        ACTIVE_RESULTS.put(activeKey, resultKey);

        /*
         * JourneyMap 1.16.5 scales Nether command coordinates by 1/8.
         * To make a waypoint appear at the real Nether coordinate, send x/z * 8.
         */
        int commandX = x;
        int commandZ = z;

        if (dimension.equals("minecraft:the_nether")) {
            commandX = x * 8;
            commandZ = z * 8;
        }

        CompassToMap.LOGGER.info(
                "{} found target: target={}, x={}, y={}, z={}, dimension={}, color={}, commandX={}, commandZ={}",
                source,
                targetKey,
                x,
                y,
                z,
                dimension,
                color,
                commandX,
                commandZ
        );

        String command = "waypoint create \"" + safeName + "\" "
                + dimension + " "
                + commandX + " " + y + " " + commandZ + " "
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

    private void clearActiveResult(ServerPlayerEntity player, String compassId, String slotName) {
        String activeKey = player.getUniqueID()
                + "|" + compassId
                + "|" + slotName;

        ACTIVE_RESULTS.remove(activeKey);
    }

    private boolean isStructureValidForDimension(String structureKey, String dimension) {
        if (!structureKey.startsWith("minecraft:")) {
            return true;
        }

        if (dimension.equals("minecraft:the_nether")) {
            return VANILLA_NETHER_STRUCTURES.contains(structureKey)
                    || structureKey.equals("minecraft:ruined_portal");
        }

        if (dimension.equals("minecraft:the_end")) {
            return VANILLA_END_STRUCTURES.contains(structureKey);
        }

        // Overworld: reject clearly Nether/End-only vanilla structures.
        return !VANILLA_NETHER_STRUCTURES.contains(structureKey)
                && !VANILLA_END_STRUCTURES.contains(structureKey);
    }

    private boolean isBiomeValidForDimension(String biomeKey, String dimension) {
        if (!biomeKey.startsWith("minecraft:")) {
            return true;
        }

        if (dimension.equals("minecraft:the_nether")) {
            return VANILLA_NETHER_BIOMES.contains(biomeKey);
        }

        if (dimension.equals("minecraft:the_end")) {
            return VANILLA_END_BIOMES.contains(biomeKey);
        }

        // Overworld: reject clearly Nether/End-only vanilla biomes.
        return !VANILLA_NETHER_BIOMES.contains(biomeKey)
                && !VANILLA_END_BIOMES.contains(biomeKey);
    }

    private void logSkippedResult(
            ServerPlayerEntity player,
            String source,
            String targetKey,
            String dimension,
            int x,
            int y,
            int z
    ) {
        String key = player.getUniqueID()
                + "|" + source
                + "|" + targetKey
                + "|" + dimension
                + "|" + x
                + "|" + y
                + "|" + z;

        if (LOGGED_SKIPPED_RESULTS.add(key)) {
            CompassToMap.LOGGER.info(
                    "Skipping stale or wrong-dimension compass result: source={}, target={}, x={}, y={}, z={}, currentDimension={}",
                    source,
                    targetKey,
                    x,
                    y,
                    z,
                    dimension
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