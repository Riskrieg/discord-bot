package com.riskrieg.bot.config.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.Configuration;
import com.riskrieg.core.api.identifier.GameIdentifier;

import java.nio.file.Path;

public record AutomaticPingConfig(long guildId, GameIdentifier identifier, boolean enabled, boolean pingInterval, boolean lastPing) implements Configuration {

    @Override
    public Path path() {
        return Path.of(BotConstants.CONFIG_PATH + "service/automatic-ping/" + guildId + "/" + identifier.id() + ".json");
    }

}
