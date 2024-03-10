package com.riskrieg.bot.service;

import com.riskrieg.bot.config.Configuration;
import com.riskrieg.bot.util.Interval;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.Optional;

public class AutomaticSkipService implements StartableService {

    @Override
    public String name() {
        return "AutomaticSkip";
    }

    @Override
    public Configuration createConfig(String groupId, String gameId, Interval interval) {
        return null;
    }

    @Override
    public Optional<Configuration> getConfig(String groupId, String gameId) {
        return Optional.empty();
    }

    @Override
    public Configuration retrieveConfig(String groupId, String gameId, Interval interval) {
        return null;
    }

    @Override
    public void deleteConfig(String groupId, String gameId) {

    }

    @Override
    public void pause() {

    }

    @Override
    public void unpause() {

    }

    @Override
    public void start(ShardManager manager) {
        System.out.println("\r[Services] " + name() + " service running.");
    }

}
