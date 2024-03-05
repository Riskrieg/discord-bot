package com.riskrieg.bot.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.service.AutomaticPingConfig;
import com.riskrieg.bot.util.Interval;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.GamePhase;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.group.Group;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.core.util.io.RkJsonUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutomaticPingService implements Service {

    public static Interval MIN_PING_INTERVAL = new Interval(1, TimeUnit.SECONDS);
    public static Interval MAX_PING_INTERVAL = new Interval(7, TimeUnit.DAYS);

    private final ConcurrentHashMap<String, ScheduledExecutorService> services;

    public AutomaticPingService() {
        this.services = new ConcurrentHashMap<>();
    }

    @Override
    public String name() {
        return "AutomaticPing";
    }

    @Override
    public void run(ShardManager manager) {
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
            var gameConfigPairs = groups.stream()
                    .flatMap(group -> group.retrieveAllGames().complete().stream())
                    .map(Game::identifier)
                    .filter(gameId -> enabledConfigs.stream().anyMatch(c -> c.identifier().equals(gameId)))
                    .map(gameId -> {
                        var config = enabledConfigs.stream()
                                .filter(c -> c.identifier().equals(gameId))
                                .findFirst()
                                .orElse(null); // TODO: Handle properly, but should never happen anyway
                        return new ImmutablePair<>(gameId, config);
                    })
                    .collect(Collectors.toSet());

            for(var pair : gameConfigPairs) {
                GameIdentifier identifier = pair.getKey();
                AutomaticPingConfig config = pair.getValue();

                Guild guild = manager.getGuildCache().getElementById(config.guildId());
                if(guild != null) {
                    TextChannel channel = guild.getChannelById(TextChannel.class, config.identifier().id());
                    if(channel != null) {
                        Group group = api.retrieveGroup(GroupIdentifier.of(String.valueOf(config.guildId()))).complete();
                        Game game = group.retrieveGame(identifier).complete();
                        switch(game.phase()) { // TODO: Need to handle the case where the game goes from SETUP to ACTIVE, have to update executor
                            case GamePhase.SETUP -> {
                                createService(game.identifier(), config, () -> {
                                    Game currentGame = group.retrieveGame(identifier).complete();
                                    Set<String> mentionableMembers = currentGame.nations().stream().filter(nation -> currentGame.claims().stream().noneMatch(claim -> nation.identifier().equals(claim.identifier())))
                                            .map(Nation::leaderIdentifier)
                                            .map(PlayerIdentifier::id)
                                            .map(id -> guild.retrieveMemberById(id).complete())
                                            .map(Member::getAsMention)
                                            .collect(Collectors.toSet());
                                    if(!mentionableMembers.isEmpty()) {
                                        channel.sendMessage("Finish setting up this game: " + String.join(", ", mentionableMembers)).queue();
                                    }
                                });
                            }
                            case GamePhase.ACTIVE -> {
                                createService(game.identifier(), config, () -> {
                                    Game currentGame = group.retrieveGame(identifier).complete();
                                    currentGame.getCurrentPlayer().ifPresent(player -> {
                                        String mention = guild.retrieveMemberById(player.identifier().id()).complete().getAsMention();
                                        channel.sendMessage("Reminder that it is your turn " + mention + ".").queue();
                                    });
                                });
                            }
                            default -> {

                            }
                        }
                    }
                }
            }

            // TODO: Shutdown and remove tasks when a game ends.

        } catch(IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        }

        System.out.println("\r[Services] " + name() + " service running.");
    }

    private ScheduledExecutorService getService(GameIdentifier identifier) {
        return services.get(identifier.id()); // Null if service with ID doesn't exist
    }

    // TODO: Properly calculate initial delay based on game's last update time/lastPing, and also update lastPing accordingly
    private void createService(GameIdentifier identifier, AutomaticPingConfig config, Runnable task) {
        if(services.containsKey(identifier.id())) {
            return;
        }
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(task, 0, config.interval().period(), config.interval().unit());
        services.put(identifier.id(), service);
    }

    private ScheduledExecutorService retrieveService(GameIdentifier identifier, AutomaticPingConfig config, Runnable task) {
        return services.computeIfAbsent(identifier.id(), id -> {
            ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
            service.scheduleAtFixedRate(task, 0, config.interval().period(), config.interval().unit());
            return service;
        });
    }


}
