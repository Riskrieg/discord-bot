package com.riskrieg.bot.command.commands.riskrieg.running.alliances;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.feature.alliance.AllianceStatus;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.time.Instant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class Unally implements Command {

  private final Settings settings;

  public Unally() {
    this.settings = new StandardSettings(
        "Break or deny an alliance with a player.",
        "unally", "deny")
        .withColor(RkpPalette.DEFAULT_BORDER_COLOR.toAwtColor())
        .makeGuildOnly();
  }

  @NotNull
  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public CommandData commandData() {
    return Commands.slash(settings().name(), settings().description())
        .addOption(OptionType.USER, "player", "Select the player you would like to break or deny an alliance with.", true);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "Command successfully processed."); // First message has to be ephemeral, so send this.

      // Guard clauses
      Member requester = event.getMember();
      if (requester == null) {
        hook.sendMessage(MessageUtil.error(settings, "Could not find member.")).queue();
        return;
      }

      Guild guild = event.getGuild();
      if (guild == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid guild.")).queue();
        return;
      }

      OptionMapping playerOption = event.getOption("player");
      if (playerOption == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid player.")).queue();
        return;
      }

      Member requestee = playerOption.getAsMember();
      if (requestee == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid player.")).queue();
        return;
      }

      // Command execution

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            boolean wasAllied = game.allianceStatus(PlayerIdentifier.of(requester.getId()), PlayerIdentifier.of(requestee.getId())).equals(AllianceStatus.COMPLETE);
            game.unally(PlayerIdentifier.of(requester.getId()), PlayerIdentifier.of(requestee.getId())).queue(allianceEvent -> {

              EmbedBuilder embedBuilder = new EmbedBuilder();
              embedBuilder.setColor(settings.embedColor());

              if (wasAllied) {
                embedBuilder.setTitle("Alliance Broken"); // TODO: notify about updated claim count
                embedBuilder.setDescription("**" + allianceEvent.allyLeader().name() + "** and **" + allianceEvent.coallyLeader().name() + "** have broken their alliance.");
              } else {
                embedBuilder.setTitle("Alliance Request Denied");
                embedBuilder.setDescription(
                    "**The alliance request between" + allianceEvent.allyLeader().name() + "** and **" + allianceEvent.coallyLeader().name() + " has been rejected**.");
              }
              embedBuilder.setFooter("Version: " + Riskrieg.VERSION);
              embedBuilder.setTimestamp(Instant.now());

              hook.sendMessage(genericSuccess).queue(success -> {
                hook.sendMessageEmbeds(embedBuilder.build()).queue();
                group.saveGame(game).queue();
              });

            }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

}
