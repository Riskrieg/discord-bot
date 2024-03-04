package com.riskrieg.bot.service;

import net.dv8tion.jda.api.sharding.ShardManager;

public class AutomaticSkipService implements Service {

    @Override
    public String name() {
        return "AutomaticSkip";
    }

    @Override
    public void run(ShardManager manager) {
        System.out.println("\r[Services] " + name() + " service running.");
    }

}
