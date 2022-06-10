/*
 *     Riskrieg, an open-source conflict simulation game.
 *     Copyright (C) 2022 Aaron Yoder <aaronjyoder@gmail.com> and Contributors
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

package com.riskrieg.bot.command.commands.riskrieg.running;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.GamePhase;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class Stats implements Command {

  private final Settings settings;

  public Stats() {
    this.settings = new StandardSettings(
        "Show statistics about current players.",
        "stats")
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
        .addOption(OptionType.STRING, "color", "Select a color from the palette that was provided.", false);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      // Guard clauses
      Member member = event.getMember();
      if (member == null) {
        hook.sendMessage(MessageUtil.error(settings, "Could not find member.")).queue();
        return;
      }

      Guild guild = event.getGuild();
      if (guild == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid guild.")).queue();
        return;
      }

      OptionMapping colorOpt = event.getOption("color");
      final Optional<String> colorStr = colorOpt == null ? Optional.empty() : Optional.of(colorOpt.getAsString()); // Parse color from string once we can access the game's palette

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            if (game.phase().equals(GamePhase.ACTIVE)) {
              if (colorStr.isEmpty()) { // General stats
                // TODO: Implement
                hook.sendMessage(MessageUtil.error(settings, "This isn't implemented yet. Please select a color.")).queue();
              } else { // Specific player stats
                RkpColor chosenColor = ParseUtil.parseColor(colorStr.get(), game.palette());
                var nation = game.getNation(chosenColor);
                if (nation.isPresent()) {
                  var player = game.getPlayer(nation.get().leaderIdentifier());
                  if (player.isPresent()) {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setColor(chosenColor.toAwtColor());
                    embedBuilder.setTitle(player.get().name() + " | " + Riskrieg.NAME + " Statistics");
                    embedBuilder.addField("Territories", nation.get().getClaimedTerritories(game.claims()).size() + "", true);
                    hook.sendMessageEmbeds(embedBuilder.build()).queue();
                  } else {
                    hook.sendMessage(MessageUtil.error(settings, "No player with that color could be found.")).queue();
                  }
                } else {
                  hook.sendMessage(MessageUtil.error(settings, "No nation with that color could be found.")).queue();
                }
              }
            } else {
              hook.sendMessage(MessageUtil.error(settings, "The game must be in the active phase to use this command.")).queue();
            }
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

}
