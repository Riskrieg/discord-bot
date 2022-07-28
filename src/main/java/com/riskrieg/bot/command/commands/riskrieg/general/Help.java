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

package com.riskrieg.bot.command.commands.riskrieg.general;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.game.feature.Feature;
import com.riskrieg.core.api.game.mode.Brawl;
import com.riskrieg.core.api.game.mode.Conquest;
import com.riskrieg.core.api.game.mode.Regicide;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class Help implements Command {

  private final Settings settings;

  public Help() {
    this.settings = new StandardSettings(
        "Learn how to use the bot.",
        "help")
        .withColor(BotConstants.GENERIC_CMD_COLOR)
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
        .addOptions(OptionDataUtil.modes())
        .setLocalizationFunction(
            RkLocalizationFunction.fromExternalBundles(this,
                DiscordLocale.ENGLISH_US
            ).build()
        );
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      OptionMapping modeOpt = event.getOption("mode");
      String modeStr = modeOpt == null ? null : modeOpt.getAsString();

      EmbedBuilder embedBuilder = new EmbedBuilder();
      embedBuilder.setColor(settings.embedColor());
      embedBuilder.setTitle("Help");
      embedBuilder.setFooter(Riskrieg.NAME + " v" + Riskrieg.VERSION);

      StringBuilder description = new StringBuilder();
      description.append("Riskrieg is a game that lets you simulate wars, battles, alternate history, and more, all through Discord!").append("\n\n");
      description.append("You can interact with the bot via [slash commands](https://support.discord.com/hc/en-us/articles/1500000368501-Slash-Commands-FAQ).").append("\s");
      description.append("You can use `/help` to get started, and you can use `/help mode:[name]` to see specific help for the given game mode.").append("\s");
      description.append("On mobile, your device must be in the [portrait orientation](https://bugs.discord.com/T1989) to use slash commands.").append("\n");
      description.append("\n");
      description.append("Have you found a bug? Join the [official Riskrieg server](https://discord.gg/weU8jYDbW4) and report it in the appropriate channel!").append("\n");
      embedBuilder.setDescription(description.toString());

      StringBuilder featureDescription = new StringBuilder();
      for (Feature feature : Feature.values()) {
        switch (feature) {
          case ALLIANCES -> {
            featureDescription.append(BotConstants.BULLET_POINT_EMOJI + " **Alliances**: ");
            featureDescription.append(
                "Allies cannot attack each other. If only allies are left in a game, then the game ends, and the allies win!");
          }
          default -> featureDescription.append("[Uncategorized Feature]: This description should be updated.");
        }
      }
      embedBuilder.addField("Features", featureDescription.toString(), false);

      if (modeStr != null) {
        var mode = ParseUtil.parseGameMode(modeStr);

        StringBuilder modeDescription = new StringBuilder();
        if (mode.equals(Conquest.class)) {
          modeDescription.append("In the Conquest game mode, you start by choosing a capital.");
          modeDescription.append("\n");
          modeDescription.append("Once the game begins, you claim territories and attack other players until a win condition is met.");

          embedBuilder.addField("Conquest", modeDescription.toString(), false);
        } else if (mode.equals(Regicide.class)) {
          modeDescription.append("In the Regicide game mode, you start by choosing a capital.");
          modeDescription.append("\n");
          modeDescription.append("Once the game begins, you claim territories and attack other players until a win condition is met.");
          modeDescription.append("\n\n");
          modeDescription.append("In this mode, if you lose your capital, you are defeated and will be removed from the game.");

          embedBuilder.addField("Regicide", modeDescription.toString(), false);
        } else if (mode.equals(Brawl.class)) {
          modeDescription.append("In the Brawl game mode, you start by choosing a capital.");
          modeDescription.append("\n");
          modeDescription.append("Once the game begins, each player takes turns claiming one territory at a time until all territories are claimed.");
          modeDescription.append("\s");
          modeDescription.append("From there, you can attack others until a win condition is met.");

          embedBuilder.addField("Brawl", modeDescription.toString(), false);
        } else {
          hook.sendMessage(MessageUtil.error(settings, "Unknown game mode.")).queue();
          return;
        }
      }

      hook.sendMessageEmbeds(embedBuilder.build()).queue();
    });
  }


}
