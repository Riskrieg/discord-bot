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

package com.riskrieg.bot.util.view;

import com.riskrieg.bot.util.ImageUtil;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.entity.player.Player;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import io.github.aaronjyoder.fill.Filler;
import io.github.aaronjyoder.fill.recursive.BlockFiller;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Optional;

public class GameView {

  public static void drawTerritoryNames(BufferedImage mapImage, BufferedImage textLayer) {
    final BufferedImage imageTerritoryNames = new BufferedImage(textLayer.getWidth(), textLayer.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = imageTerritoryNames.createGraphics();
    g2d.drawImage(textLayer, 0, 0, null);
    g2d.dispose();
    Graphics g = mapImage.getGraphics();
    g.drawImage(imageTerritoryNames, 0, 0, null);
    g.dispose();
  }

  public static void drawDynamicGameUI(BufferedImage mapImage, RkmMetadata metadata, RkpPalette palette, String mapTitle,
      Collection<Player> players, Collection<Nation> nations) {
    // General UI (Pixels)
    final int edgeMargin = 10;
    final int borderThickness = 3;

    // Color List UI (Pixels)
    final int colorItemWidth = 92;

    // Player List UI
    final int nameEdgeMargin = 4; // Margin from the edge for either side of the name (not included in width calculations; only for text drawing purposes)
    final int nameItemWidth = 194;

    // Other
    final int itemHeight = 28; // Used for both colorItem and nameItem height since they should always match
    final int dividerWidth = 2;
    final int titleHeight;
    if (metadata.autogenTitle()) {
      titleHeight = 32;
    } else {
      titleHeight = 0;
    }

    // Parameters
    final int colorListWidth = colorItemWidth + 2 * borderThickness;
    final int nameListWidth = nameItemWidth + 2 * borderThickness;
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

    /* Draw player name list */
    BufferedImage nameListImage = new BufferedImage(nameListWidth, listHeight, BufferedImage.TYPE_INT_ARGB);
    ImageUtil.fillTransparent(nameListImage);

    // Draw border
    drawBorder(nameListImage, palette.borderColor().toAwtColor(), borderThickness + 2); // Need a thickness of 5 to get 3px thickness for some reason
    drawRoundedCornerManually(nameListImage);

    // Fill inside with transparent black background
    Color transparentBlack = new Color(0, 0, 0, 175);
    Filler filler = new BlockFiller(nameListImage);
    filler.fill(borderThickness, borderThickness, transparentBlack);

    /* Draw player names */
    boolean rightAlignNames = switch (metadata.alignment().horizontal()) {
      case LEFT, CENTER -> false;
      case RIGHT -> true;
    };

    Graphics2D nmlGraphics = nameListImage.createGraphics();
    for (Nation nation : nations) {
      Optional<Player> player = players.stream().filter(p -> p.identifier().equals(nation.leaderIdentifier())).findFirst();
      RkpColor color = palette.get(nation.colorId()).orElse(palette.last());

      int x1 = nameEdgeMargin + borderThickness;
      int y1 = borderThickness + color.order() * (itemHeight + borderThickness);
      int x2 = x1 + nameItemWidth - 1 - (nameEdgeMargin * 2);
      int y2 = y1 + itemHeight - 1;

      ImageUtil.drawTextWithBounds(nameListImage, player.isPresent() ? player.get().name() : "[Unknown]", color.toAwtColor(),
          x1, y1, x2, y2, rightAlignNames, false,
          new Font("Open Sans", Font.PLAIN, 26), new Font("Noto Sans", Font.PLAIN, 26), 14.0F, 26.0F);
    }
    nmlGraphics.dispose();

    /* Combine and draw map title */
    BufferedImage combinedImage = new BufferedImage(colorListWidth + dividerWidth + nameListWidth, listHeight + titleHeight, BufferedImage.TYPE_INT_ARGB);
    ImageUtil.fillTransparent(combinedImage);

    if (metadata.autogenTitle()) {
      ImageUtil.drawTextWithBounds(combinedImage, "\u2014" + " " + mapTitle + " " + "\u2014", RkpPalette.DEFAULT_TEXT_COLOR.toAwtColor(),
          0, 0, combinedImage.getWidth() - 1, titleHeight - 1, false, true,
          new Font("Spectral", Font.PLAIN, 21), new Font("Noto Serif", Font.PLAIN, 21), 14.0F, 26.0F);
    }

    switch (metadata.alignment().horizontal()) {
      case LEFT, CENTER -> { // Draw color list on left
        Graphics2D cmbGraphics = combinedImage.createGraphics();
        cmbGraphics.drawImage(colorListImage, 0, titleHeight, colorListWidth, listHeight, null);
        cmbGraphics.drawImage(nameListImage, colorListWidth + dividerWidth, titleHeight, nameListWidth, listHeight, null);
        cmbGraphics.dispose();
      }
      case RIGHT -> { // Draw color list on right
        Graphics2D cmbGraphics = combinedImage.createGraphics();
        cmbGraphics.drawImage(nameListImage, 0, titleHeight, nameListWidth, listHeight, null);
        cmbGraphics.drawImage(colorListImage, nameListWidth + dividerWidth, titleHeight, colorListWidth, listHeight, null);
        cmbGraphics.dispose();
      }
    }

    // Draw combined image on top of map image
    int mapX = switch (metadata.alignment().horizontal()) {
      case LEFT -> edgeMargin;
      case CENTER -> mapImage.getWidth() / 2 - combinedImage.getWidth() / 2;
      case RIGHT -> mapImage.getWidth() - combinedImage.getWidth() - edgeMargin;
    };

    int mapY = switch (metadata.alignment().vertical()) {
      case TOP -> edgeMargin;
      case MIDDLE -> mapImage.getHeight() / 2 - combinedImage.getHeight() / 2;
      case BOTTOM -> mapImage.getHeight() - combinedImage.getHeight() - edgeMargin;
    };

    Graphics2D mapGraphics = mapImage.createGraphics();
    mapGraphics.drawImage(combinedImage, mapX, mapY, combinedImage.getWidth(), combinedImage.getHeight(), null);
    mapGraphics.dispose();
  }

  private static void drawBorder(BufferedImage image, Color color, int thickness) {
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

  private static void drawRoundedCornerManually(BufferedImage image) { // Only works for border thickness of 3px, just doing manually for now
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
