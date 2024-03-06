package com.riskrieg.bot.service;

import net.dv8tion.jda.api.sharding.ShardManager;

public interface StartableService extends Service {

    void start(ShardManager manager);

}
