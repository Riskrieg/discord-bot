package com.riskrieg.bot.command.commands.riskrieg.restricted.server;

import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.service.AutomaticPingService;
import com.riskrieg.bot.service.Service;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.palette.RkpPalette;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class Services implements Command {

    private final Settings settings;
    private final Service pingService;

    public Services(AutomaticPingService pingService) {
        this.pingService = pingService;
        this.settings = new StandardSettings(
                "Configuration command for bot services.",
                "services")
                .withColor(RkpPalette.DEFAULT_BORDER_COLOR.toAwtColor())
                .makeGuildOnly()
                .withAuthorPermissions(Permission.MANAGE_SERVER);
    }

    @NonNull
    @Override
    public Settings settings() {
        return settings;
    }

    @Override
    public CommandData commandData() {
        return Commands.slash(settings().name(), settings().description())
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .setLocalizationFunction(
                        RkLocalizationFunction.fromExternalBundles(this,
                                DiscordLocale.ENGLISH_US
                        ).build()
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue(hook -> {

            Guild guild = event.getGuild();
            if (guild == null) {
                hook.sendMessage(MessageUtil.error(settings, "Invalid guild.")).queue();
                return;
            }



        });
    }

}
