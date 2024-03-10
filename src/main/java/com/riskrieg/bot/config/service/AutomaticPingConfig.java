package com.riskrieg.bot.config.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.Configuration;
import com.riskrieg.bot.service.AutomaticPingService;
import com.riskrieg.bot.util.Interval;
import com.riskrieg.core.api.identifier.GameIdentifier;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public record AutomaticPingConfig(String groupId, GameIdentifier identifier, boolean enabled, Interval interval, Instant lastPing) implements Configuration {

    public AutomaticPingConfig {
        if(interval.compareTo(AutomaticPingService.MIN_PING_INTERVAL) < 0) {
            interval = AutomaticPingService.MIN_PING_INTERVAL;
        } else if(interval.compareTo(AutomaticPingService.MAX_PING_INTERVAL) > 0) {
            interval = AutomaticPingService.MAX_PING_INTERVAL;
        }
    }

    public static Path formPath(String groupId, String gameId) {
        return Path.of(BotConstants.CONFIG_PATH + "service/automatic-ping/" + groupId + "/" + gameId + ".json");
    }

    public static Path baseDirectory() {
        return Paths.get(BotConstants.CONFIG_PATH + "service/automatic-ping");
    }

    @Override
    public Path path() {
        return Path.of(BotConstants.CONFIG_PATH + "service/automatic-ping/" + groupId + "/" + identifier.id() + ".json");
    }

    public AutomaticPingConfig withLastPing(Instant lastPing) {
        return new AutomaticPingConfig(groupId, identifier, enabled, interval, lastPing);
    }

    public AutomaticPingConfig withEnabled(boolean enabled) {
        return new AutomaticPingConfig(groupId, identifier, enabled, interval, lastPing);
    }

}
