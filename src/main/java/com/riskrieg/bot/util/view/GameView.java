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

package com.riskrieg.bot.util.view;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.util.ImageUtil;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.entity.player.Player;
import com.riskrieg.core.util.io.RkJsonUtil;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.map.metadata.Alignment;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import javax.imageio.ImageIO;

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

  public static void drawPlayerUI(BufferedImage mapImage, Alignment alignment, String mapTitle, String mapName,
      Collection<Player> players, Collection<Nation> nations, RkpPalette palette) throws IOException { // TODO: Support anywhere from 2-16 colors
    final int marginSide = 10;
    final int marginTopBottom = 10;
    final int marginBetween = 2;
    int borderThickness = 3;
    int marginNameSide = 4;

    int titleHeight = 32;
    int titleBuffer = 4;

    final BufferedImage imageColorList = ImageIO.read(new File(BotConstants.COLOR_CHOICES_VERTICAL_BLANK));
    final BufferedImage imagePlayerNameRegion = ImageIO.read(new File(BotConstants.PLAYER_NAME_BACKGROUND));

    Graphics g = mapImage.getGraphics();
    Point pointColorList = new Point(0, 0);
    Point pointPlayerNameRegion = new Point(0, 0);

    Point pointPlayerRectTopLeft = new Point(0, 0);
    Point pointPlayerRectBottomRight = new Point(0, 0);
    switch (alignment.horizontal()) {
      case LEFT -> {
        pointColorList.setLocation(marginSide, pointColorList.y);
        pointPlayerNameRegion.setLocation(pointColorList.x + imageColorList.getWidth() + marginBetween, pointPlayerNameRegion.y);
      }
      case CENTER -> {
        pointColorList.setLocation(mapImage.getWidth() / 2 - imageColorList.getWidth() / 2 - imagePlayerNameRegion.getWidth() / 2 - marginBetween / 2, pointColorList.y);
        pointPlayerNameRegion.setLocation(mapImage.getWidth() / 2 - imagePlayerNameRegion.getWidth() / 2 + imageColorList.getWidth() / 2 + marginBetween / 2,
            pointPlayerNameRegion.y);
      }
      case RIGHT -> {
        pointColorList.setLocation(mapImage.getWidth() - imageColorList.getWidth() - marginSide, pointColorList.y);
        pointPlayerNameRegion.setLocation(pointColorList.x - marginBetween - imagePlayerNameRegion.getWidth(), pointPlayerNameRegion.y);
      }
    }

    pointPlayerRectTopLeft.setLocation(pointPlayerNameRegion.x + borderThickness + marginNameSide, pointPlayerRectTopLeft.y);
    pointPlayerRectBottomRight.setLocation(pointPlayerRectTopLeft.x + (imagePlayerNameRegion.getWidth() - marginNameSide * 2 - borderThickness * 2 - 1),
        pointPlayerRectBottomRight.y); // Have to subtract 1 because one edge is exclusive

    switch (alignment.vertical()) {
      case TOP -> {
        pointColorList.setLocation(pointColorList.x, marginTopBottom + titleBuffer * 2 + titleHeight);
        pointPlayerNameRegion.setLocation(pointPlayerNameRegion.x, marginTopBottom + titleBuffer * 2 + titleHeight);
      }
      case MIDDLE -> {
        pointColorList.setLocation(pointColorList.x, mapImage.getHeight() / 2 - imageColorList.getHeight() / 2);
        pointPlayerNameRegion.setLocation(pointPlayerNameRegion.x, mapImage.getHeight() / 2 - imagePlayerNameRegion.getHeight() / 2);
      }
      case BOTTOM -> {
        pointColorList.setLocation(pointColorList.x, mapImage.getHeight() - imageColorList.getHeight() - marginTopBottom);
        pointPlayerNameRegion.setLocation(pointPlayerNameRegion.x, mapImage.getHeight() - imagePlayerNameRegion.getHeight() - marginTopBottom);
      }
    }

    // Draw imageColorList
    Graphics icGraphics = mapImage.getGraphics();
    icGraphics.drawImage(imageColorList, pointColorList.x, pointColorList.y, null);
    icGraphics.dispose();
    // Draw colors and text
    int startX = pointColorList.x + 3;
    int startY = pointColorList.y + 3;
    int vBuffer = 0;
    int hBuffer = 3;
    int jumpY = 31;

    for (RkpColor color : palette.sortedColorSet()) {
      // Fill in the player color
      Graphics pcGraphics = mapImage.getGraphics();
      Color prev = pcGraphics.getColor();
      pcGraphics.setColor(color.toAwtColor());
      pcGraphics.fillRect(startX, startY, 92, 28);
      pcGraphics.setColor(prev);
      pcGraphics.dispose();

      // Draw color name
      Graphics cnGraphics = mapImage.getGraphics();
      ImageUtil.paintTextWithBounds((Graphics2D) cnGraphics, color.name().toUpperCase(), RkpPalette.DEFAULT_TEXT_COLOR.toAwtColor(),
          startX + hBuffer, startY + 3, startX + 91 - hBuffer, startY + 27 - vBuffer,
          false, true, new Font("Raleway", Font.BOLD, 15), new Font("Noto Mono", Font.BOLD, 15), 12.0F, 15.0F);
      cnGraphics.dispose();
      // Move to next square down
      startY += jumpY;
    }

    g.drawImage(imagePlayerNameRegion, pointPlayerNameRegion.x, pointPlayerNameRegion.y, null);
    g.dispose();

    RkmMetadata currentMetadata = RkJsonUtil.read(Path.of(BotConstants.MAP_OPTIONS_PATH + mapName + ".json"), RkmMetadata.class);
    if (currentMetadata != null && currentMetadata.autogenTitle()) {
      Point mapTitleTopLeft = new Point(5, pointColorList.y - titleBuffer - titleHeight);
      if (alignment.horizontal().equals(Alignment.Horizontal.RIGHT)) {
        mapTitleTopLeft.setLocation(pointPlayerNameRegion.x, mapTitleTopLeft.y);
      } else {
        mapTitleTopLeft.setLocation(pointColorList.x, mapTitleTopLeft.y);
      }

      Graphics tGraphics = mapImage.getGraphics();
      ImageUtil.paintTextWithBounds((Graphics2D) tGraphics, "\u2014" + " " + mapTitle + " " + "\u2014", RkpPalette.DEFAULT_TEXT_COLOR.toAwtColor(),
          mapTitleTopLeft.x, mapTitleTopLeft.y,
          mapTitleTopLeft.x + (imageColorList.getWidth() + marginBetween + imagePlayerNameRegion.getWidth() - 1),
          mapTitleTopLeft.y - titleBuffer - titleHeight,
          false, true, new Font("Spectral", Font.PLAIN, 21), new Font("Noto Serif", Font.PLAIN, 21));
      tGraphics.dispose();
    }

    int colorCellHeight = (imageColorList.getHeight() - (palette.size() + 1) * borderThickness) / palette.size();
    pointPlayerRectTopLeft.setLocation(pointPlayerRectTopLeft.x, pointPlayerNameRegion.y + borderThickness);
    pointPlayerRectBottomRight.setLocation(pointPlayerRectBottomRight.x, pointPlayerRectTopLeft.y + colorCellHeight - 1); // Subtract 1 because one edge is exclusive

    int nameRectHeight = pointPlayerRectBottomRight.y - pointPlayerRectTopLeft.y;

    for (Nation nation : nations) {
      Optional<Player> player = players.stream().filter(p -> p.identifier().equals(nation.leaderIdentifier())).findFirst();
      Graphics pGraphics = mapImage.getGraphics();
      int currentTopLeftY = pointPlayerRectTopLeft.y + ((colorCellHeight + borderThickness) * nation.colorId());
      ImageUtil.paintTextWithBounds((Graphics2D) pGraphics, player.isPresent() ? player.get().name() : "[Unknown]",
          palette.get(nation.colorId()).orElse(palette.last()).toAwtColor(), pointPlayerRectTopLeft.x, currentTopLeftY,
          pointPlayerRectBottomRight.x, currentTopLeftY + (nameRectHeight), alignment.horizontal().equals(Alignment.Horizontal.RIGHT), false,
          new Font("Open Sans", Font.PLAIN, 26), new Font("Noto Sans", Font.PLAIN, 26));
      pGraphics.dispose();
    }

    g.dispose();
  }


}
