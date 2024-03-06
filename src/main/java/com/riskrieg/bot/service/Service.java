package com.riskrieg.bot.service;

import net.dv8tion.jda.api.sharding.ShardManager;

public interface Service {

    String name();

    void start(ShardManager manager);

}
