package net.dungeonz.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "dungeonz")
@Config.Gui.Background("minecraft:textures/block/stone.png")
public class DungeonzConfig implements ConfigData {

    public boolean defaultDungeons = false;
    @Comment("In ticks, used for preloading")
    public int defaultDungeonTeleportCountdown = 200;
    public int countdownX = 0;
    public int countdownY = 0;
    public float countdownSize = 6.0f;
    @Comment("Among other things crops won't grow")
    public boolean devMode = false;

}
