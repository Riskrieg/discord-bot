package com.riskrieg.bot.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.service.AutomaticPingConfig;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.GamePhase;
import com.riskrieg.core.api.group.Group;
import com.riskrieg.core.util.io.RkJsonUtil;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        if(Files.notExists(configDirectory)) {
            try {
                Files.createDirectories(configDirectory);
            } catch (IOException e) {
                System.err.println("Failed to create service directory. Check file permissions. Error: " + e.getMessage());
            }
        }
        try(Stream<Path> configFilesStream = Files.walk(configDirectory, 2)) {
            var serviceConfigs = configFilesStream
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

            Set<AutomaticPingConfig> enabledConfigs = serviceConfigs.stream()
                    .filter(AutomaticPingConfig::enabled)
                    .collect(Collectors.toSet());

            // Load all groups
            Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();

            Collection<Group> groups = api.retrieveAllGroups().complete();

            // Load all games with configs, and partition the games into setup phase and active phase
            var partitionedGameConfigPairs = groups.stream()
                    .flatMap(group -> group.retrieveAllGames().complete().stream())
                    .filter(game -> enabledConfigs.stream().anyMatch(c -> c.identifier().equals(game.identifier())))
                    .map(game -> {
                        var config = enabledConfigs.stream()
                                .filter(c -> c.identifier().equals(game.identifier()))
                                .findFirst()
                                .orElse(null); // TODO: Handle properly, but should never happen anyway
                        return new ImmutablePair<>(game, config);
                    })
                    .collect(Collectors.partitioningBy(entry -> entry.getKey().phase().equals(GamePhase.SETUP)));

            List<ImmutablePair<Game, AutomaticPingConfig>> setupGamePairs = partitionedGameConfigPairs.get(true);
            List<ImmutablePair<Game, AutomaticPingConfig>> activeGamePairs = partitionedGameConfigPairs.get(false);

        } catch(IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        }

        System.out.println("\r[Services] " + name() + " service running.");
    }

}
