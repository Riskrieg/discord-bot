package com.riskrieg.bot.config.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.Configuration;

import java.nio.file.Path;
import java.util.Set;

public record AutomaticPingConfig(long guildId, Set<GamePingConfigData> gamePingConfigData) implements Configuration {

    @Override
    public Path path() {
        return Path.of(BotConstants.CONFIG_PATH + "service/automatic-ping/config-" + guildId + ".json");
    }

}
