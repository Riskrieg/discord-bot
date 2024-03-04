package com.riskrieg.bot.config.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.Configuration;
import com.riskrieg.bot.service.AutomaticPingService;
import com.riskrieg.core.api.identifier.GameIdentifier;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public record AutomaticPingConfig(long guildId, GameIdentifier identifier, boolean enabled, Duration pingInterval, Instant lastPing) implements Configuration {

    public AutomaticPingConfig {
        if(pingInterval.compareTo(AutomaticPingService.MIN_PING_INTERVAL) < 0) {
            pingInterval = AutomaticPingService.MIN_PING_INTERVAL;
        } else if(pingInterval.compareTo(AutomaticPingService.MAX_PING_INTERVAL) > 0) {
            pingInterval = AutomaticPingService.MAX_PING_INTERVAL;
        }
    }

    @Override
    public Path path() {
        return Path.of(BotConstants.CONFIG_PATH + "service/automatic-ping/" + guildId + "/" + identifier.id() + ".json");
    }

}
