package com.kuronami.compasstomap;

import com.kuronami.compasstomap.event.CompassWatcher;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CompassToMap.MODID)
public class CompassToMap {
    public static final String MODID = "compasstomap";
    public static final Logger LOGGER = LogManager.getLogger();

    public CompassToMap() {
        MinecraftForge.EVENT_BUS.register(new CompassWatcher());
        LOGGER.info("Compass to Map Legacy loaded for Forge 1.16.5");
    }
}