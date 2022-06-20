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

package com.riskrieg.bot.command.commands.riskrieg;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.ConfigUtil;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.RiskriegUtil;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.EndReason;
import com.riskrieg.core.api.game.GamePhase;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.entity.player.Player;
import com.riskrieg.core.api.game.mode.Brawl;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

public class Leave implements Command { // TODO: Handle allied victory state

  private final Settings settings;

  public Leave() {
    this.settings = new StandardSettings(
        "Remove yourself from the current game.",
        "leave", "forfeit")
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
    return Commands.slash(settings().name(), settings().description());
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "You have left the game."); // First message has to be ephemeral, so send this.

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

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            Optional<Player> leavingPlayer = game.getPlayer(PlayerIdentifier.of(member.getId()));
            if (leavingPlayer.isPresent()) {
              game.removePlayer(leavingPlayer.get().identifier()).queue(success -> {
                game.update(false).queue(updateEvent -> {
                  EmbedBuilder embedBuilder = new EmbedBuilder();
                  embedBuilder.setColor(RkpPalette.DEFAULT_BORDER_COLOR.toAwtColor());
                  embedBuilder.setTitle("Leave");
                  embedBuilder.setDescription("**" + leavingPlayer.get().name() + "** has left the game.");

                  var messageAction = hook.sendMessageEmbeds(embedBuilder.build()); // Determine if map should be sent before sending

                  if (game.map() != null) {
                    embedBuilder.setImage("attachment://map.png");
                    messageAction = hook.sendMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), "map.png", new AttachmentOption[0]);
                  }

                  var finalMessageAction = messageAction;
                  hook.sendMessage(genericSuccess).queue(success2 -> {
                    finalMessageAction.queue(message -> {
                      StringBuilder description = new StringBuilder();
                      if (updateEvent.endReason().equals(EndReason.NONE)) {
                        if (game.map() != null) {
                          var currentPlayer = updateEvent.currentPlayer();
                          if (game.phase().equals(GamePhase.ACTIVE) && currentPlayer.isPresent()) {
                            Optional<Nation> optCurrent = game.getNation(currentPlayer.get().identifier());
                            String claimStr = "They may claim an unknown amount of territories this turn.";
                            if (optCurrent.isPresent()) {
                              long allowedClaimAmount;
                              if (game.getClass() == Brawl.class && game.claims().size() != game.map().vertices().size()) {
                                allowedClaimAmount = 1;
                              } else {
                                allowedClaimAmount = optCurrent.get()
                                    .getAllowedClaimAmount(game.claims(), game.constants(), game.map(), game.getAllies(optCurrent.get().identifier()));
                              }
                              claimStr = "They may claim " + allowedClaimAmount + " " + (allowedClaimAmount == 1 ? "territory" : "territories") + " this turn.";
                            }
                            embedBuilder.setFooter("It is " + currentPlayer.get().name() + "'s turn. " + claimStr);
                          }
                          message.editMessageEmbeds(embedBuilder.build())
                              .queue(success3 -> currentPlayer.ifPresent(player -> ConfigUtil.sendMentionIfEnabled(hook, player.identifier().toString())));
                        }
                        group.saveGame(game).queue();
                      } else {
                        var currentPlayer = updateEvent.currentPlayer();
                        switch (updateEvent.endReason()) {
                          case NO_PLAYERS -> description.append("There are no players left in the game, so the game has ended.").append("\n");
                          case DEFEAT ->
                              description.append("**").append(currentPlayer.isPresent() ? currentPlayer.get().name() : "The remaining player").append("** has won the game!");
                          case STALEMATE -> description.append("A stalemate has been reached! The game is now over.");
                          default -> description.append("The game is now over.");
                        }
                        embedBuilder.addField("Game Ended", description.toString(), false);
                        embedBuilder.setFooter("Thank you for playing!");
                        message.editMessageEmbeds(embedBuilder.build()).queue();
                        group.deleteGame(GameIdentifier.of(event.getChannel().getId())).queue();
                      }
                    });
                  });


                }, failure -> {
                  hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue();
                  failure.printStackTrace();
                });
              }, failure -> {
                hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue();
                failure.printStackTrace();
              });
            } else {
              hook.sendMessage(MessageUtil.error(settings, "You are not in the game, so you cannot leave it.")).queue();
            }
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

}
