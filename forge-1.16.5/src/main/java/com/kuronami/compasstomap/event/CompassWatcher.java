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
    private static final Set<String> LOGGED_STACK_STATES = new HashSet<>();

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
        CompoundNBT tag = stack.getTag();
        String tagString = tag == null ? "null" : tag.toString();

        /*
         * Debug phase:
         * Log any compass-like held item once per unique state so we can find
         * Explorer's Compass' exact registry id and saved tag keys.
         */
        if (idString.contains("compass")) {
            String stateKey = player.getUniqueID() + "|" + slotName + "|" + idString + "|" + tagString;

            if (LOGGED_STACK_STATES.add(stateKey)) {
                CompassToMap.LOGGER.info(
                        "Held compass-like item: slot={}, id={}, display={}, tag={}",
                        slotName,
                        idString,
                        stack.getDisplayName().getString(),
                        tagString
                );
            }
        }

        boolean isExplorersCompass =
                idString.equals("explorerscompass:explorers_compass")
                        || idString.equals("explorerscompass:explorerscompass");

        if (!isExplorersCompass) {
            return;
        }

        if (tag == null) {
            CompassToMap.LOGGER.info("Explorer's Compass detected, but it has no NBT tag yet.");
            return;
        }

        // First goal: print the compass NBT so we can discover the real coordinate keys.
        CompassToMap.LOGGER.info("Explorer's Compass NBT: {}", tag);

        player.sendMessage(
                new StringTextComponent("Compass to Map found Explorer's Compass NBT. Check latest.log."),
                player.getUniqueID()
        );
    }
}