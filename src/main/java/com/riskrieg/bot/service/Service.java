package com.riskrieg.bot.service;

import com.riskrieg.bot.config.Configuration;
import com.riskrieg.bot.util.Interval;

import java.io.IOException;
import java.util.Optional;

public interface Service {

    String name();

    Configuration createConfig(String groupId, String gameId, Interval interval);

    Optional<Configuration> getConfig(String groupId, String gameId);

    Configuration retrieveConfig(String groupId, String gameId, Interval interval);

    void deleteConfig(String groupId, String gameId);

    void pause();

    void unpause();

}
