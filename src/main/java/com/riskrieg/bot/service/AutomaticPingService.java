package com.riskrieg.bot.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.service.AutomaticPingConfig;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.GamePhase;
import com.riskrieg.core.api.group.Group;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.util.io.RkJsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutomaticPingService implements Service {

    @Override
    public String name() {
        return "AutomaticPing";
    }

    @Override
    public void run() {
        // Load configs
        Path configDirectory = Paths.get(BotConstants.CONFIG_PATH + "service/automatic-ping");
        try(Stream<Path> configFilesStream = Files.walk(configDirectory, 2)) {
            var configFiles = configFilesStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(path -> {
                        try {
                            return RkJsonUtil.read(path, AutomaticPingConfig.class);
                        } catch (IOException e) {
                            System.err.println("Error reading config file, file will be ignored: " + path + " - " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();

            Collection<Group> groups = api.retrieveAllGroups().complete();
            Stream<Game> allGamesStream = groups.stream().flatMap(group -> group.retrieveAllGames().complete().stream());

            Map<Boolean, List<Game>> partitionedGames = allGamesStream.collect(Collectors.partitioningBy(game -> game.phase().equals(GamePhase.SETUP)));

            List<Game> allSetupGames = partitionedGames.get(true);
            List<Game> allActiveGames = partitionedGames.get(false);
        } catch(IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        }

        System.out.println("\r[Services] " + name() + " service running.");
    }

}
