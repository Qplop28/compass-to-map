package com.kuronami.compasstomap.event;

import com.kuronami.compasstomap.CompassToMap;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CompassWatcher {

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

        // This may need changing after we verify the exact 1.16.5 Explorer's Compass item ID.
        if (!itemId.toString().equals("explorerscompass:explorers_compass")) {
            return;
        }

        CompoundNBT tag = stack.getTag();

        if (tag == null) {
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