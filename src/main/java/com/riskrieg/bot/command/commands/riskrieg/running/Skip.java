/*
 *     Riskrieg, an open-source conflict simulation game.
 *     Copyright (C) 2020-2022 Aaron Yoder <aaronjyoder@gmail.com> and Contributors
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
import com.riskrieg.bot.util.ConfigUtil;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.RiskriegUtil;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.event.UpdateEvent;
import com.riskrieg.core.api.game.mode.Brawl;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.util.Optional;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class Skip implements Command {

  private final Settings settings;

  public Skip() {
    this.settings = new StandardSettings(
        "Skip your turn, or skip the current player's turn if you have the 'Timeout Members' permission.",
        "skip")
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

      MessageCreateData genericSuccess = MessageUtil.success(settings, "Player successfully skipped."); // First message has to be ephemeral, so send this.

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

      // TODO: Handle allied victory state -- Not strictly necessary, but would be a nice touch

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            if (member.hasPermission(Permission.MODERATE_MEMBERS)) { // Force-skip
              game.update(true).queue(updateEvent -> {

                sendSkipMessage(hook, genericSuccess, game, updateEvent);
                group.saveGame(game).queue();

              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
            } else { // Self-skip
              var currentPlayer = game.getCurrentPlayer();
              if (currentPlayer.isPresent() && currentPlayer.get().identifier().equals(PlayerIdentifier.of(member.getId()))) {
                game.update(true).queue(updateEvent -> {

                  sendSkipMessage(hook, genericSuccess, game, updateEvent);
                  group.saveGame(game).queue();

                }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
              } else {
                hook.sendMessage(MessageUtil.error(settings, "You do not have permission to skip this player.")).queue();
              }
            }
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

  private void sendSkipMessage(InteractionHook hook, MessageCreateData genericSuccess, Game game, UpdateEvent updateEvent) {
    if (ConfigUtil.canMention(hook)) {
      hook.sendMessage(genericSuccess).queue(success -> {
        updateEvent.currentPlayer().ifPresent(currentPlayer -> {
          ConfigUtil.sendWithMention(hook, currentPlayer.identifier().id(), message -> {
            message.editMessageEmbeds(skipMessage(game, updateEvent))
                .setFiles(FileUpload.fromData(RiskriegUtil.constructMapImageData(game), "map.png")).queue();
          });
        });
      });
    } else {
      hook.sendMessage(genericSuccess).queue(success -> {
        hook.sendMessageEmbeds(skipMessage(game, updateEvent))
            .addFiles(FileUpload.fromData(RiskriegUtil.constructMapImageData(game), "map.png")).queue();
      });
    }
  }

  private MessageEmbed skipMessage(Game game, UpdateEvent updateEvent) {
    var previousPlayer = updateEvent.previousPlayer();
    var currentPlayer = updateEvent.currentPlayer();
    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setColor(RkpPalette.DEFAULT_BORDER_COLOR.toAwtColor());
    embedBuilder.setTitle("Skip");
    embedBuilder.setDescription("**" + (previousPlayer.isPresent() ? previousPlayer.get().name() : "Someone") + "** has skipped their turn.");
    embedBuilder.setImage("attachment://map.png");

    if (currentPlayer.isPresent()) {
      Optional<Nation> optCurrent = game.getNation(currentPlayer.get().identifier());
      String claimStr = "They may claim an unknown amount of territories this turn.";
      if (optCurrent.isPresent()) {
        long allowedClaimAmount;
        if (game.getClass() == Brawl.class && game.claims().size() != game.map().vertices().size()) {
          allowedClaimAmount = 1;
        } else {
          allowedClaimAmount = optCurrent.get().getAllowedClaimAmount(game.claims(), game.constants(), game.map(), game.getAllies(optCurrent.get().identifier()));
        }
        claimStr = "They may claim " + allowedClaimAmount + " " + (allowedClaimAmount == 1 ? "territory" : "territories") + " this turn.";
      }
      embedBuilder.setFooter("It is " + currentPlayer.get().name() + "'s turn. " + claimStr);
    }

    return embedBuilder.build();
  }


}
