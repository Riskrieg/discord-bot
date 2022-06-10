/*
 *     Riskrieg, an open-source conflict simulation game.
 *     Copyright (C) 2021 Aaron Yoder <aaronjyoder@gmail.com> and Contributors
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
import com.riskrieg.bot.game.StandardAttack;
import com.riskrieg.bot.util.ConfigUtil;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.RiskriegUtil;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.ClaimOverride;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.entity.player.Player;
import com.riskrieg.core.api.game.event.ClaimEvent;
import com.riskrieg.core.api.game.event.UpdateEvent;
import com.riskrieg.core.api.game.territory.Claim;
import com.riskrieg.core.api.game.territory.GameTerritory;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.map.territory.TerritoryIdentity;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

public class ClaimCommand implements Command {

  private final Settings settings;

  public ClaimCommand() {
    this.settings = new StandardSettings(
        "Claim some territory.",
        "claim", "attack", "take")
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
    OptionData territories = new OptionData(OptionType.STRING, "territories", "Specify which territories you would like to claim.", true);
    OptionData override = new OptionData(OptionType.STRING, "override", "Override the default claim functionality", false)
        .addChoice("Auto", "auto")
        .addChoice("Exact", "exact");

    return Commands.slash(settings().name(), settings().description()).addOptions(territories, override);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "Claim successfully processed."); // First message has to be ephemeral, so send this.

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

      OptionMapping territoriesOpt = event.getOption("territories");
      List<TerritoryIdentity> territoryList = territoriesOpt == null ? Collections.emptyList() : parseTerritoryList(territoriesOpt.getAsString());

      TerritoryIdentity[] territories = territoryList.toArray(TerritoryIdentity[]::new);

      OptionMapping overrideOpt = event.getOption("override");
      ClaimOverride override = overrideOpt == null ? ClaimOverride.NONE : switch (overrideOpt.getAsString()) {
        default -> ClaimOverride.NONE;
        case "auto" -> ClaimOverride.AUTO;
        case "exact" -> ClaimOverride.EXACT;
      };

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            game.claim(new StandardAttack(), PlayerIdentifier.of(member.getId()), override, territories).queue(claimEvent -> {
              game.update(true).queue(updateEvent -> {

                String fileName = game.map().codename() + ".png";
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setColor(settings.embedColor());
                embedBuilder.setTitle(game.map().displayName());
                embedBuilder.setDescription(buildTurnDescription(claimEvent, updateEvent));
                embedBuilder.setImage("attachment://" + fileName);

                switch (game.phase()) {
                  case ENDED -> {
                    embedBuilder.setFooter("Thank you for playing!");

                    hook.sendMessage(genericSuccess).queue(success -> {
                      hook.sendMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), fileName, new AttachmentOption[0]).queue();
                      group.deleteGame(GameIdentifier.of(event.getChannel().getId())).queue();
                    });
                  }
                  case ACTIVE -> {
                    var currentPlayer = updateEvent.currentPlayer();
                    Optional<Nation> optCurrent = currentPlayer.flatMap(player -> game.getNation(player.identifier()));
                    String claimStr = "They may claim an unknown amount of territories this turn.";
                    if (optCurrent.isPresent()) {
                      long allowedClaimAmount = optCurrent.get().getAllowedClaimAmount(game.claims(), game.constants(), game.map());
                      claimStr = "They may claim " + allowedClaimAmount + " " + (allowedClaimAmount == 1 ? "territory" : "territories") + " this turn.";
                    }
                    embedBuilder.setFooter("It is " + (currentPlayer.isPresent() ? currentPlayer.get().name() : "someone") + "'s turn. " + claimStr);

                    if (ConfigUtil.canMention(hook)) {
                      hook.sendMessage(genericSuccess).queue(success -> {
                        currentPlayer.ifPresent(player -> ConfigUtil.sendWithMention(hook, player.identifier().id(), message -> {
                          message.editMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), fileName, new AttachmentOption[0]).queue();
                        }));
                      });
                    } else {
                      hook.sendMessage(genericSuccess).queue(success -> {
                        hook.sendMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), fileName, new AttachmentOption[0]).queue();
                      });
                    }
                    group.saveGame(game).queue();
                  }
                  case SETUP -> {
                    embedBuilder.setFooter("");

                    hook.sendMessage(genericSuccess).queue(success -> {
                      hook.sendMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), fileName, new AttachmentOption[0]).queue();
                    });
                    group.saveGame(game).queue();
                  }
                }

              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
            }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

  private List<TerritoryIdentity> parseTerritoryList(String input) {
    List<TerritoryIdentity> territoryList = new ArrayList<>();
    String[] territoryArray = input.split("[\s,|/\\\\]+");
    for (String territory : territoryArray) {
      if (!territory.isBlank()) {
        territoryList.add(new TerritoryIdentity(territory));
      }
    }
    return territoryList;
  }

  private String buildTurnDescription(ClaimEvent claimEvent, UpdateEvent updateEvent) {
    StringBuilder description = new StringBuilder();
    if (claimEvent.freeClaims().size() > 0) {
      description.append("**").append(claimEvent.leader().name()).append("** has claimed: ")
          .append(claimEvent.freeClaims().stream()
              .map(Claim::territory)
              .map(GameTerritory::identity)
              .map(TerritoryIdentity::toString).collect(Collectors.joining(", ")).trim()).append("\n");
    }
    if (claimEvent.wonClaims().size() > 0) {
      description.append("**").append(claimEvent.leader().name()).append("** has taken: ")
          .append(claimEvent.wonClaims().stream()
              .map(Claim::territory)
              .map(GameTerritory::identity)
              .map(TerritoryIdentity::toString).collect(Collectors.joining(", ")).trim()).append("\n");
    }
    if (claimEvent.defendedClaims().size() > 0) {
      // TODO: Say who defended what
      description.append("Territories defended: ")
          .append(claimEvent.defendedClaims().stream()
              .map(Claim::territory)
              .map(GameTerritory::identity)
              .map(TerritoryIdentity::toString).collect(Collectors.joining(", ")).trim()).append("\n");
    }
    description.append("\n");
    for (Player player : updateEvent.defeatedPlayers()) {
      description.append("**").append(player.name()).append("** has been defeated!").append("\n");
    }
    description.append("\n");
    var currentPlayer = updateEvent.currentPlayer();
    switch (updateEvent.endReason()) {
      case NONE -> { // Do nothing
      }
      case DEFEAT -> description.append("**").append(currentPlayer.isPresent() ? currentPlayer.get().name() : "The remaining player").append("** has won the game!");
      case STALEMATE -> description.append("A stalemate has been reached! The game is now over.");
      default -> description.append("The game is now over.");
    }
    return description.toString().trim();
  }

}
