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

package com.riskrieg.bot.command.commands.riskrieg.running.alliances;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.feature.alliance.AllianceStatus;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.time.Instant;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class Unally implements Command {

  private final Settings settings;

  public Unally() {
    this.settings = new StandardSettings(
        "Break or deny an alliance with another player.",
        "unally", "deny")
        .withColor(RkpPalette.DEFAULT_BORDER_COLOR.toAwtColor())
        .makeGuildOnly();
  }

  @NonNull
  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public CommandData commandData() {
    return Commands.slash(settings().name(), settings().description())
        .addOption(OptionType.USER, "player", "Select the player you would like to break or deny an alliance with.", true)
        .setGuildOnly(true)
        .setLocalizationFunction(
            RkLocalizationFunction.fromExternalBundles(this,
                DiscordLocale.ENGLISH_US
            ).build()
        );
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      MessageCreateData genericSuccess = MessageUtil.success(settings, "Command successfully processed."); // First message has to be ephemeral, so send this.

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
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            AllianceStatus previousStatus = game.allianceStatus(PlayerIdentifier.of(requester.getId()), PlayerIdentifier.of(requestee.getId()));

            if (previousStatus == AllianceStatus.NONE) {
              hook.sendMessage(MessageUtil.error(settings, "You are not in an alliance with that player.")).queue();
              return;
            }

            game.unally(PlayerIdentifier.of(requester.getId()), PlayerIdentifier.of(requestee.getId())).queue(allianceEvent -> {

              EmbedBuilder embedBuilder = new EmbedBuilder();
              embedBuilder.setColor(settings.embedColor());

              if (previousStatus == AllianceStatus.COMPLETE) {
                embedBuilder.setTitle("Alliance Broken"); // TODO: notify about updated claim count
                embedBuilder.setDescription("**" + allianceEvent.allyLeader().name() + "** and **" + allianceEvent.coallyLeader().name() + "** have broken their alliance.");
              } else if (previousStatus == AllianceStatus.INCOMING) {
                embedBuilder.setTitle("Alliance Request Denied");
                embedBuilder.setDescription("**" + allianceEvent.allyLeader().name() + "** has rejected **" + allianceEvent.coallyLeader().name() + "**'s alliance request.");
              } else if (previousStatus == AllianceStatus.OUTGOING) {
                embedBuilder.setTitle("Alliance Request Cancelled");
                embedBuilder.setDescription("**" + allianceEvent.allyLeader().name() + "** has cancelled their alliance request to **" + allianceEvent.coallyLeader().name() + "**.");
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
