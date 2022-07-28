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

package com.riskrieg.bot.command.commands.riskrieg.setup;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.bot.util.StringUtil;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.entity.player.Player;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.time.Instant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class Join implements Command {

  private final Settings settings;

  public Join() {
    this.settings = new StandardSettings(
        "Join a game.",
        "join")
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
        .addOption(OptionType.STRING, "color", "Provide a color from the current game palette.", true)
        .addOption(OptionType.STRING, "player_name", "Choose a name for your player.", false)
        .setGuildOnly(true)
        .setLocalizationFunction(
            RkLocalizationFunction.fromExternalBundles(this,
                DiscordLocale.ENGLISH_US,
                DiscordLocale.SPANISH, // TODO: Update localization for "color" option description
                DiscordLocale.TURKISH // TODO: Update localization for "color" option description
            ).build()
        );
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "You have been added to the game."); // First message has to be ephemeral, so send this.

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
      if (colorOpt == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid color.")).queue();
        return;
      }
      String colorStr = colorOpt.getAsString(); // Parse color from string once we can access the game's palette

      OptionMapping nameOpt = event.getOption("name");
      final String playerName = nameOpt == null ? member.getEffectiveName() : nameOpt.getAsString();

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            game.addPlayer(PlayerIdentifier.of(member.getId()), playerName).queue(player -> {
              RkpColor chosenColor = ParseUtil.parseColor(colorStr, game.palette());
              game.createNation(chosenColor, player.identifier()).queue(nation -> {

                hook.sendMessage(genericSuccess).queue(success -> {
                  hook.sendMessageEmbeds(createMessageEmbed(player, nation, game.palette())).queue();
                  group.saveGame(game).queue();
                });

              }, failure -> {
                game.removePlayer(player.identifier()).queue(); // If there's a problem creating the nation, need to remove the player
                group.saveGame(game).queue();
                hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue();
              });
            }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

  private MessageEmbed createMessageEmbed(Player player, Nation nation, RkpPalette palette) {
    EmbedBuilder embedBuilder = new EmbedBuilder();
    RkpColor color = palette.get(nation.colorId()).orElse(palette.last());
    embedBuilder.setColor(color.toAwtColor());
    embedBuilder.setTitle("Join");
    embedBuilder.setDescription("**" + player.name() + "** has joined the game as **" + StringUtil.toTitleCase(color.name()) + "**.");
    embedBuilder.setFooter("Version: " + Riskrieg.VERSION);
    embedBuilder.setTimestamp(Instant.now());
    return embedBuilder.build();
  }

}
