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

package com.riskrieg.bot.util;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.util.view.GameView;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.territory.Claim;
import com.riskrieg.core.api.game.territory.TerritoryType;
import com.riskrieg.core.util.game.GameUtil;
import com.riskrieg.core.util.io.RkJsonUtil;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.map.Territory;
import com.riskrieg.map.territory.Nucleus;
import io.github.aaronjyoder.fill.Filler;
import io.github.aaronjyoder.fill.MaskFiller;
import io.github.aaronjyoder.fill.nonrecursive.BasicQueueFiller;
import io.github.aaronjyoder.fill.recursive.BlockFiller;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;

public class RiskriegUtil {

  public static byte[] constructEmptyMapImageData(Game game) {
    return ImageUtil.convertToByteArray(constructMap(game));
  }

  public static byte[] constructMapImageData(Game game) {
    return ImageUtil.convertToByteArray(constructMap(game));
  }

  private static BufferedImage constructMap(Game game) {
    try {
      BufferedImage baseImage = ImageUtil.createCopy(ImageUtil.convert(game.map().baseLayer(), BufferedImage.TYPE_INT_ARGB));

      for (Nation nation : game.nations()) {
        for (Claim claim : nation.getClaimedTerritories(game.claims())) {
          Optional<Territory> optionalTerritory = game.map().get(claim.territory().identity());
          if (optionalTerritory.isPresent()) {
            Territory territory = optionalTerritory.get();
            if (GameUtil.territoryIsOfType(territory.identity(), TerritoryType.CAPITAL, game.claims())) {
              colorCapitalTerritory(baseImage, territory.nuclei(), game.palette().get(nation.colorId()).orElse(game.palette().last()).toAwtColor());
            } else {
              colorTerritory(baseImage, territory.nuclei(), game.palette().get(nation.colorId()).orElse(game.palette().last()).toAwtColor());
            }
          }
        }
      }

      RkmMetadata metadata = RkJsonUtil.read(Path.of(BotConstants.MAP_OPTIONS_PATH + game.map().codename() + ".json"), RkmMetadata.class);

      GameView.drawTerritoryNames(baseImage, game.map().textLayer());
      GameView.drawPlayerUI(baseImage, metadata.alignment(), game.map().displayName(), game.map().codename(), game.players(), game.nations(), game.palette());

      return baseImage;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static void colorTerritory(BufferedImage image, Set<Nucleus> nuclei, Color newColor) {
    Filler bucket = new BlockFiller(image);
    for (Nucleus nucleus : nuclei) {
      bucket.fill(nucleus.toPoint(), newColor);
    }
  }

  private static void colorCapitalTerritory(BufferedImage image, Set<Nucleus> nuclei, Color newColor) throws IOException {
    MaskFiller bucket = new BasicQueueFiller(image);
    BufferedImage mask = ImageIO.read(new File("res/images/capital-mask.png"));
    for (Nucleus nucleus : nuclei) {
      bucket.fill(nucleus.toPoint(), newColor, mask);
    }
  }

}
