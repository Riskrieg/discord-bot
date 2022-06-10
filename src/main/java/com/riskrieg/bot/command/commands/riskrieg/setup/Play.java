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

package com.riskrieg.bot.command.commands.riskrieg.setup;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.ConfigUtil;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.bot.util.RiskriegUtil;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.order.ColorOrder;
import com.riskrieg.core.api.game.order.DetailedTurnOrder;
import com.riskrieg.core.api.game.order.RandomOrder;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Path;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

public class Play implements Command {

  private final Settings settings;

  public Play() {
    this.settings = new StandardSettings(
        "Start the game.",
        "play", "begin", "start")
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
        .addOptions(OptionDataUtil.turnOrders())
        .addOption(OptionType.BOOLEAN, "reverse", "Whether the selected order strategy should be reversed.", false)
        .addOption(OptionType.BOOLEAN, "randomize-first", "Whether the player who goes first should be randomized.", false);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "The game has been started."); // First message has to be ephemeral, so send this.

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

      OptionMapping turnOrderOpt = event.getOption("order");
      final DetailedTurnOrder order = turnOrderOpt == null ? new ColorOrder() : switch (turnOrderOpt.getAsString()) {
        case "colors" -> new ColorOrder();
        case "random" -> new RandomOrder();
        default -> new ColorOrder();
      };

      OptionMapping reverseOpt = event.getOption("reverse");
      OptionMapping randomizeFirstOpt = event.getOption("randomize-first");

      final boolean reverseOrder = reverseOpt != null && reverseOpt.getAsBoolean();
      final boolean randomizeStart = randomizeFirstOpt != null && randomizeFirstOpt.getAsBoolean();

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            if (game.players().stream().anyMatch(player -> player.identifier().equals(PlayerIdentifier.of(member.getId())))) {
              game.start(order, reverseOrder, randomizeStart).queue(currentPlayer -> {
                StringBuilder description = new StringBuilder();
                description.append("Turn order: **").append(order.displayName()).append("**.").append("\n");
                description.append("*").append(order.description()).append("*").append("\n");
                description.append("\n");
                description.append("The game has begun!");

                String fileName = game.map().codename() + ".png";
                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setColor(settings.embedColor());
                embedBuilder.setTitle(game.map().displayName());
                embedBuilder.setDescription(description.toString());
                embedBuilder.setFooter("It is " + currentPlayer.name() + "'s turn."); // TODO: Add allowed claim amount?
                embedBuilder.setImage("attachment://" + fileName);

                if (ConfigUtil.canMention(hook)) {
                  hook.sendMessage(genericSuccess).queue(success -> {
                    ConfigUtil.sendWithMention(hook, currentPlayer.identifier().id(), message -> {
                      message.editMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), fileName, new AttachmentOption[0]).queue();
                    });
                  });
                } else {
                  hook.sendMessage(genericSuccess).queue(success -> {
                    hook.sendMessageEmbeds(embedBuilder.build()).addFile(RiskriegUtil.constructMapImageData(game), fileName, new AttachmentOption[0]).queue();
                  });
                }
                group.saveGame(game).queue();

              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
            } else {
              hook.sendMessage(MessageUtil.error(settings, "The game can only be started by players in the game.")).queue();
            }
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

}
