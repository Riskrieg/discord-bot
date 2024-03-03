/*
 *     Riskrieg, an open-source conflict simulation game.
 *     Copyright (C) 2018-2022 Aaron Yoder <aaronjyoder@gmail.com> and Contributors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.riskrieg.bot;

import com.riskrieg.bot.auth.Auth;
import com.riskrieg.bot.service.Service;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RiskriegBot implements Bot {

    private final Auth auth;
    private final DefaultShardManagerBuilder builder;
    private final Set<Service> services;

    public RiskriegBot(Auth auth) {
        this.auth = auth;
        this.builder = DefaultShardManagerBuilder.createLight(auth.token());
        this.services = new HashSet<>();
    }

    @Override
    public void registerListeners(@Nonnull Object... listeners) {
        builder.addEventListeners(listeners);
    }

    @Override
    public void registerServices(@Nonnull Service... services) {
        this.services.addAll(Arrays.asList(services));
        System.out.println("\r[Services] " + this.services.size() + " local services registered.");
    }

    @Override
    public void start() {
        try {
            builder.build(); // TODO: Potentially set chunking policy and such, try to do this without using any intents first though.
            // TODO: Run services
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
