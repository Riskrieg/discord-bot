package com.riskrieg.bot.service;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.config.Configuration;
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
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutomaticPingService implements StartableService {

    public static Interval MIN_PING_INTERVAL = new Interval(30, TimeUnit.MINUTES);
    public static Interval MAX_PING_INTERVAL = new Interval(7, TimeUnit.DAYS);
    public static Interval DEFAULT_PING_INTERVAL = new Interval(4, TimeUnit.HOURS);

    private static boolean isPaused = false;

    private static final ConcurrentHashMap<String, ScheduledExecutorService> tasks = new ConcurrentHashMap<>();

    public AutomaticPingService() {

    }

    @Override
    public String name() {
        return "AutomaticPing";
    }

    @Override
    public Configuration createConfig(String groupId, String gameId, Interval interval) {
        Path path = AutomaticPingConfig.formPath(groupId, gameId);
        Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
        try {
            if(Files.notExists(path)) {
                Group group = api.retrieveGroup(GroupIdentifier.of(groupId)).complete();
                Game game = group.retrieveGame(GameIdentifier.of(gameId)).complete();

                AutomaticPingConfig config = new AutomaticPingConfig(groupId, GameIdentifier.of(gameId), true, interval, game.updatedTime());
                RkJsonUtil.write(path, AutomaticPingConfig.class, config);
                return config;
            }
        } catch(Exception e) { // game doesn't exist or had error writing config
            return null;
        }
        return null;
    }

    @Override
    public Optional<Configuration> getConfig(String groupId, String gameId) {
        try {
            AutomaticPingConfig config = RkJsonUtil.read(AutomaticPingConfig.formPath(groupId, gameId), AutomaticPingConfig.class);
            return Optional.ofNullable(config);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Configuration retrieveConfig(String groupId, String gameId, Interval interval) {
        return getConfig(groupId, gameId).orElseGet(() -> {
            deleteConfig(groupId, gameId); // just in case of corrupted config or something
            return createConfig(groupId, gameId, interval);
        });
    }

    @Override
    public Configuration retrieveConfig(String groupId, String gameId) {
        return retrieveConfig(groupId, gameId, AutomaticPingService.DEFAULT_PING_INTERVAL);
    }

    @Override
    public void deleteConfig(String groupId, String gameId) {
        Path path = AutomaticPingConfig.formPath(groupId, gameId);
        endTask(groupId, gameId, false);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // fail silently
        }
    }

    @Override
    public void pause() {
        isPaused = true;
    }

    @Override
    public void unpause() {
        isPaused = false;
    }

    @Override
    public void start(ShardManager manager) {
        // Load configs
        Path configDirectory = AutomaticPingConfig.baseDirectory();
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

                Guild guild = manager.getGuildCache().getElementById(config.groupId());
                if(guild != null) {
                    Group group = api.retrieveGroup(GroupIdentifier.of(config.groupId())).complete();
                    GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, config.identifier().id());
                    if(channel != null) {
                        Game game = group.retrieveGame(identifier).complete();

                        updateConfigLastPing(group.identifier().id(), identifier.id(), game.updatedTime().isAfter(config.lastPing()) ? game.updatedTime() : config.lastPing());

                        switch(game.phase()) {
                            case GamePhase.SETUP -> createTask(config, runSetup(group, identifier, guild, channel));
                            case GamePhase.ACTIVE -> createTask(config, runActive(group, identifier, guild, channel));
                            default -> {}
                        }
                    }
                }
            }

            // TODO: Shutdown and remove tasks when a game ends.

        } catch(IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        }
        String tasks = AutomaticPingService.tasks.size() == 1 ? "task" : "tasks";
        System.out.println("\r[Services] " + name() + " service running with " + AutomaticPingService.tasks.size() + " " + tasks + ".");
    }

    private Runnable runSetup(Group group, GameIdentifier identifier, Guild guild, GuildMessageChannel channel) {
        return () -> {
            if(isPaused) {
                return;
            }
            try {
                Path path = AutomaticPingConfig.formPath(group.identifier().id(), identifier.id());
                AutomaticPingConfig config = RkJsonUtil.read(path, AutomaticPingConfig.class);
                if(config == null || isConfigDisabled(group.identifier().id(), identifier.id())) {
                    endTask(group.identifier().id(), identifier.id(), false);
                    return;
                }

                Game currentGame = group.retrieveGame(identifier).complete();
                if(currentGame.phase().equals(GamePhase.ACTIVE)) { // Switch tasks when phase changes
                    endTask(group.identifier().id(), identifier.id(), true);
                    createTask(config, runActive(group, identifier, guild, channel));
                    return;
                }

                Set<String> mentionableMembers = currentGame.nations().stream().filter(nation -> currentGame.claims().stream().noneMatch(claim -> nation.identifier().equals(claim.identifier())))
                        .map(Nation::leaderIdentifier)
                        .map(PlayerIdentifier::id)
                        .map(id -> guild.retrieveMemberById(id).complete())
                        .map(Member::getAsMention)
                        .collect(Collectors.toSet());
                if(!mentionableMembers.isEmpty()) {
                    channel.sendMessage("Reminder to finish setting up this game: " + String.join(", ", mentionableMembers)).queue();
                    updateConfigLastPing(group.identifier().id(), identifier.id(), Instant.now());
                }
            } catch(Exception e) {
                System.err.println("\r[Services] " + name() + " service failed to load game with ID " + identifier.id() + ". Config disabled. Ending task with error: " + e.getMessage());
                endTask(group.identifier().id(), identifier.id(), false);
            }
        };
    }

    private Runnable runActive(Group group, GameIdentifier identifier, Guild guild, GuildMessageChannel channel) {
        return () -> {
            if(isPaused) {
                return;
            }
            try {
                if(isConfigDisabled(group.identifier().id(), identifier.id())) {
                    endTask(group.identifier().id(), identifier.id(), false);
                    return;
                }
                Game currentGame = group.retrieveGame(identifier).complete();
                currentGame.getCurrentPlayer().ifPresent(player -> {
                    String mention = guild.retrieveMemberById(player.identifier().id()).complete().getAsMention();
                    channel.sendMessage("Reminder that it is your turn " + mention + ".").queue();
                    updateConfigLastPing(group.identifier().id(), identifier.id(), Instant.now());
                });
            } catch(Exception e) {
                System.err.println("\r[Services] " + name() + " service failed to load game with ID " + identifier.id() + ". Config disabled. Ending task with error: " + e.getMessage());
                endTask(group.identifier().id(), identifier.id(), false);
            }
        };
    }

    private ScheduledExecutorService getTask(GameIdentifier identifier) {
        return tasks.get(identifier.id()); // Null if service with ID doesn't exist
    }

    private void createTask(AutomaticPingConfig config, Runnable task) {
        if(tasks.containsKey(config.identifier().id())) {
            return;
        }
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        // Initial delay has to be the same unit as period, so convert to minutes to account for minimum period being 30 minutes.
        long minutesSinceLastPing = Duration.between(config.lastPing(), Instant.now()).toMinutes();
        long initialDelay;
        if(minutesSinceLastPing >= config.interval().asMinutes()) {
            initialDelay = 0;
        } else {
            initialDelay = config.interval().asMinutes() - minutesSinceLastPing;
        }
        service.scheduleAtFixedRate(task, initialDelay, config.interval().asMinutes(), TimeUnit.MINUTES);
        tasks.put(config.identifier().id(), service);
    }

    private ScheduledExecutorService retrieveTask(GameIdentifier identifier, AutomaticPingConfig config, Runnable task) {
        return tasks.computeIfAbsent(identifier.id(), id -> {
            ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
            service.scheduleAtFixedRate(task, 0, config.interval().period(), config.interval().unit());
            return service;
        });
    }

    private boolean isConfigDisabled(String groupId, String gameId) {
        try {
            Path path = AutomaticPingConfig.formPath(groupId, gameId);
            AutomaticPingConfig config = RkJsonUtil.read(path, AutomaticPingConfig.class);
            return config == null || !config.enabled();
        } catch (IOException e) {
            return true;
        }
    }

    private void endTask(String groupId, String gameId, boolean configEnabled) {
        try (var service = tasks.remove(gameId)) {
            if(service != null) {
                updateConfigEnabled(groupId, gameId, configEnabled);
                service.shutdown();
            }
        }
    }

    private void updateConfigLastPing(String groupId, String gameId, Instant instant) {
        try {
            Path path = AutomaticPingConfig.formPath(groupId, gameId);
            AutomaticPingConfig config = RkJsonUtil.read(path, AutomaticPingConfig.class);
            if(config != null) {
                RkJsonUtil.write(path, AutomaticPingConfig.class, config.withLastPing(instant));
            }
        } catch (IOException e) {
            System.err.println("\r[Services] " + name() + " service failed to update 'lastPing' parameter with config ID " + gameId + ". Error: " + e.getMessage());
        }
    }

    private void updateConfigEnabled(String groupId, String gameId, boolean enabled) {
        try {
            Path path = AutomaticPingConfig.formPath(groupId, gameId);
            AutomaticPingConfig config = RkJsonUtil.read(path, AutomaticPingConfig.class);
            if(config != null) {
                RkJsonUtil.write(path, AutomaticPingConfig.class, config.withEnabled(enabled));
            }
        } catch (IOException e) {
            System.err.println("\r[Services] " + name() + " service failed to update 'enabled' parameter with config ID " + gameId + ". Error: " + e.getMessage());
        }
    }


}
