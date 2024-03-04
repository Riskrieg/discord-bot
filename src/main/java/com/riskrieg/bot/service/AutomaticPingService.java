package com.riskrieg.bot.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.service.AutomaticPingConfig;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.GamePhase;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.group.Group;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.core.util.io.RkJsonUtil;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutomaticPingService implements Service {

    public static Duration MIN_PING_INTERVAL = Duration.of(1, ChronoUnit.HOURS);
    public static Duration MAX_PING_INTERVAL = Duration.of(7, ChronoUnit.DAYS);

    private List<ScheduledExecutorService> executorServices;

    public AutomaticPingService() {
        this.executorServices = new ArrayList<>();
    }

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

            for(var pair : setupGamePairs) {
                Game game = pair.getKey();
                AutomaticPingConfig config = pair.getValue();

                Set<PlayerIdentifier> playersToPing = game.nations().stream().filter(nation -> game.claims().stream().noneMatch(claim -> nation.identifier().equals(claim.identifier())))
                        .map(Nation::leaderIdentifier)
                        .collect(Collectors.toSet());

                ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
            }

            // TODO: Need to handle the case where the game goes from SETUP to ACTIVE, have to update executor

            for(var pair : activeGamePairs) {
                Game game = pair.getKey();
                AutomaticPingConfig config = pair.getValue();

                game.getCurrentPlayer().ifPresent(player -> {
                    PlayerIdentifier playerToPing = player.identifier();

                    ScheduledExecutorService pingService = Executors.newSingleThreadScheduledExecutor();
                    // TODO: Schedule service. Need instance of JDA API in order to do this properly.
                });
            }

        } catch(IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        }

        System.out.println("\r[Services] " + name() + " service running.");
    }

}
