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
import com.riskrieg.bot.util.*;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.codec.decode.RkpDecoder;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.GameConstants;
import com.riskrieg.core.api.game.feature.Feature;
import com.riskrieg.core.api.game.feature.FeatureFlag;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.aaronjyoder.fill.Filler;
import io.github.aaronjyoder.fill.recursive.BlockFiller;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class Create implements Command {

  private final Settings settings;

  public Create() {
    this.settings = new StandardSettings(
        "Creates a new game.",
        "create")
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
        .addOptions(OptionDataUtil.modes().setRequired(true), OptionDataUtil.palettes().setRequired(false))
        .addOption(OptionType.ATTACHMENT, "file", "Provide your own palette file.", false)
        .addOption(OptionType.STRING, "features", "List the features you would like to enable.", false)
        .setGuildOnly(true)
        .setLocalizationFunction(
            RkLocalizationFunction.fromExternalBundles(this,
                DiscordLocale.ENGLISH_US,
                DiscordLocale.SPANISH,
                DiscordLocale.TURKISH
            ).build()
        );
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      MessageCreateData genericSuccess = MessageUtil.success(settings, "A new game was created."); // First message has to be ephemeral, so send this.

      // Guard clauses
      Guild guild = event.getGuild();
      if (guild == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid guild.")).queue();
        return;
      }

      OptionMapping modeOpt = event.getOption("mode");
      if (modeOpt == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid game mode.")).queue();
        return;
      }
      String modeStr = modeOpt.getAsString();
      var mode = ParseUtil.parseGameMode(modeStr);

      final RkpPalette palette = getPalette(event);

      OptionMapping featuresOpt = event.getOption("features");
      final FeatureFlag[] featureFlags;
      if (featuresOpt != null) {
        String featuresString = featuresOpt.getAsString();
        featureFlags = ParseUtil.parseFeatures(featuresString);
      } else {
        featureFlags = new FeatureFlag[0];
      }

      // Command execution
      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
      api.createGroup(GroupIdentifier.of(guild.getId()))
          .queue(group -> group.createGame(GameConstants.standard().clampTo(palette), palette, GameIdentifier.of(event.getChannel().getId()), mode, featureFlags).queue(game -> {
                hook.sendMessage(genericSuccess).queue(success -> {
                  hook.sendMessageEmbeds(createMessage(event.getMember(), modeStr, palette.name(), featureFlags))
                      .addFiles(FileUpload.fromData(PaletteUtil.generatePaletteDisplay(game.palette()), "color-choices.png"))
                      .queue();
                });
              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
          ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

  private RkpPalette getPalette(SlashCommandInteractionEvent event) {
    OptionMapping paletteOpt = event.getOption("palette");
    RkpPalette palette = paletteOpt == null ? RkpPalette.standard16() : switch (paletteOpt.getAsString().toLowerCase()) {
      case "original" -> RkpPalette.original16();
      case "desatur" -> RkpPalette.desatur8();
      case "pollen" -> RkpPalette.pollen8();
      case "gothic" -> RkpPalette.gothic6();
      default -> RkpPalette.standard16();
    };

    OptionMapping paletteOverride = event.getOption("file");
    if (paletteOverride != null) {
      try {
        palette = new RkpDecoder().decode(new URL(paletteOverride.getAsAttachment().getUrl()));
      } catch (Exception e) {
        palette = RkpPalette.standard16();
      }
    }
    return palette;
  }

  private MessageEmbed createMessage(Member creator, String modeString, String paletteName, FeatureFlag[] featureFlags) {
    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setColor(settings.embedColor());
    embedBuilder.setTitle("Join Game");
    embedBuilder.setImage("attachment://color-choices.png");

    String featuresString = "Features: " + (featureFlags.length == 0 ? "*None*" : Arrays.stream(featureFlags)
        .filter(FeatureFlag::enabled)
        .map(FeatureFlag::feature)
        .map(Feature::name)
        .map(name -> "**" + StringUtil.toTitleCase(name) + "**")
        .collect(Collectors.joining(", ")).trim());

    if (creator != null) {
      embedBuilder.setDescription("Creator: " + creator.getAsMention()
          + "\nMode: **" + StringUtil.toTitleCase(modeString) + "**"
          + "\nPalette: **" + paletteName + "**"
          + "\n" + featuresString);
    } else {
      embedBuilder.setDescription("Creator: Unknown"
          + "\nMode: **" + StringUtil.toTitleCase(modeString) + "**"
          + "\nPalette: **" + paletteName + "**"
          + "\n" + featuresString);
    }
    embedBuilder.setFooter("Please select a color to join the game. You may also choose a map at any time before starting the game.");
    return embedBuilder.build();
  }

}
