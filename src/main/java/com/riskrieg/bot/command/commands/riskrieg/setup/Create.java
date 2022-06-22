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
import com.riskrieg.bot.util.ImageUtil;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.bot.util.StringUtil;
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
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

public class Create implements Command {

  private final Settings settings;

  public Create() {
    this.settings = new StandardSettings(
        "Creates a new " + Riskrieg.NAME + " game.",
        "create")
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
        .addOptions(OptionDataUtil.modes().setRequired(true), OptionDataUtil.palettes().setRequired(false))
        .addOption(OptionType.ATTACHMENT, "file", "Provide your own palette file.", false)
        .addOption(OptionType.STRING, "features", "List the features you would like to enable.", false);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "A new game was created."); // First message has to be ephemeral, so send this.

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
                      .addFile(generateDynamicColorChoices(game.palette()), "color-choices.png", new AttachmentOption[0])
                      .queue();
                });
              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
          ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

  private RkpPalette getPalette(SlashCommandInteractionEvent event) {
    OptionMapping paletteOpt = event.getOption("palette");
    RkpPalette palette = paletteOpt == null ? RkpPalette.standard() : switch (paletteOpt.getAsString().toLowerCase()) {
      case "original" -> RkpPalette.original();
      default -> RkpPalette.standard();
    };

    OptionMapping paletteOverride = event.getOption("file");
    if (paletteOverride != null) {
      try {
        palette = new RkpDecoder().decode(new URL(paletteOverride.getAsAttachment().getUrl()));
      } catch (Exception e) {
        palette = RkpPalette.standard();
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

  @NonNull
  private byte[] generateDynamicColorChoices(RkpPalette palette) {
    // General UI (Pixels)
    final int borderThickness = 3;

    // Color List UI (Pixels)
    final int colorItemWidth = 92;

    // Other
    final int itemHeight = 28; // Used for both colorItem and nameItem height since they should always match

    // Parameters
    final int colorListWidth = colorItemWidth + 2 * borderThickness;
    final int listHeight = borderThickness + (itemHeight + borderThickness) * palette.size(); // Same height for both lists since they should always match

    /* Draw color list */
    BufferedImage colorListImage = new BufferedImage(colorListWidth, listHeight, BufferedImage.TYPE_INT_ARGB);
    ImageUtil.fillTransparent(colorListImage);

    // Draw border
    drawBorder(colorListImage, palette.borderColor().toAwtColor(), borderThickness + 2); // Need a thickness of 5 to get 3px thickness for some reason
    drawRoundedCornerManually(colorListImage);

    // Draw dividers, add colors, add color names
    Graphics2D g = colorListImage.createGraphics();

    g.setColor(palette.borderColor().toAwtColor());
    g.setStroke(new BasicStroke(borderThickness));
    int x = borderThickness;
    int y;
    for (RkpColor color : palette.sortedColorSet()) {
      int i = color.order(); // Should always start at 0 and go sequentially by 1 from there
      y = (i + 1) * (itemHeight + borderThickness) + 1; // Set y right away so that every fill operation fills in the same number of pixels

      // Draw divider
      g.drawLine(x, y, x + colorItemWidth - 1, y);

      // Fill color
      Filler filler = new BlockFiller(colorListImage);
      filler.fill(borderThickness, borderThickness + (i * (itemHeight + borderThickness)), color.toAwtColor());

      // Draw color name
      int x1 = borderThickness;
      int y1 = borderThickness + i * (itemHeight + borderThickness);
      int x2 = x1 + colorItemWidth - 1;
      int y2 = y1 + itemHeight - 1;
      ImageUtil.drawTextWithBounds(colorListImage, color.name().toUpperCase(), RkpPalette.DEFAULT_TEXT_COLOR.toAwtColor(),
          x1, y1, x2, y2, false, true,
          new Font("Raleway", Font.BOLD, 15), new Font("Noto Mono", Font.BOLD, 15), 12.0F, 15.0F);
    }

    g.dispose();

    return ImageUtil.convertToByteArray(colorListImage);
  }

  private void drawBorder(BufferedImage image, Color color, int thickness) {
    int borderAdjustment = 1;
    if (thickness % 2 == 0) {
      borderAdjustment = 0;
    }
    int width = image.getWidth();
    int height = image.getHeight();

    Graphics2D g2d = image.createGraphics();
    g2d.setColor(color);
    g2d.setStroke(new BasicStroke(thickness));
    g2d.drawLine(0, 0, 0, height);
    g2d.drawLine(0, 0, width, 0);
    g2d.drawLine(0, height - borderAdjustment, width, height - borderAdjustment);
    g2d.drawLine(width - borderAdjustment, height - borderAdjustment, width - borderAdjustment, 0);
    g2d.dispose();
  }

  private void drawRoundedCornerManually(BufferedImage image) { // Only works for border thickness of 3px, just doing manually for now
    int width = image.getWidth();
    int height = image.getHeight();
    int transparentRGB = new Color(0, 0, 0, 0).getRGB();
    // Top left corner
    image.setRGB(0, 0, transparentRGB);
    image.setRGB(1, 0, transparentRGB);
    image.setRGB(0, 1, transparentRGB);

    // Bottom left corner
    image.setRGB(0, height - 1, transparentRGB);
    image.setRGB(1, height - 1, transparentRGB);
    image.setRGB(0, height - 2, transparentRGB);

    // Top right corner
    image.setRGB(width - 1, 0, transparentRGB);
    image.setRGB(width - 2, 0, transparentRGB);
    image.setRGB(width - 1, 1, transparentRGB);

    // Bottom right corner
    image.setRGB(width - 1, height - 1, transparentRGB);
    image.setRGB(width - 2, height - 1, transparentRGB);
    image.setRGB(width - 1, height - 2, transparentRGB);
  }

}
