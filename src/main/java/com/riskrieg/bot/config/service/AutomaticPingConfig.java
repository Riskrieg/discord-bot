package com.riskrieg.bot.config.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.Configuration;
import com.riskrieg.bot.service.AutomaticPingService;
import com.riskrieg.bot.util.Interval;
import com.riskrieg.core.api.identifier.GameIdentifier;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public record AutomaticPingConfig(long guildId, GameIdentifier identifier, boolean enabled, Interval interval, Instant lastPing) implements Configuration {

    public AutomaticPingConfig {
        if(interval.compareTo(AutomaticPingService.MIN_PING_INTERVAL) < 0) {
            interval = AutomaticPingService.MIN_PING_INTERVAL;
        } else if(interval.compareTo(AutomaticPingService.MAX_PING_INTERVAL) > 0) {
            interval = AutomaticPingService.MAX_PING_INTERVAL;
        }
    }

    @Override
    public Path path() {
        return Path.of(BotConstants.CONFIG_PATH + "service/automatic-ping/" + guildId + "/" + identifier.id() + ".json");
    }

    public AutomaticPingConfig withLastPing(Instant lastPing) {
        return new AutomaticPingConfig(guildId, identifier, enabled, interval, lastPing);
    }

}
