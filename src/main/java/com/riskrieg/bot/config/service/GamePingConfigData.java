package com.riskrieg.bot.config.service;

import com.riskrieg.core.api.identifier.GameIdentifier;

public record GamePingConfigData(GameIdentifier identifier, boolean enabled, boolean pingInterval, boolean lastPing) {
}
