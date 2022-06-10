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

package com.riskrieg.bot.util;

import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.mode.Conquest;
import com.riskrieg.map.metadata.Alignment;
import com.riskrieg.map.metadata.Availability;
import com.riskrieg.map.metadata.Flavor;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class ParseUtil {

  public static Optional<Boolean> parseEnable(String str) {
    if (str.equals("false") || str.equals("disabled") || str.equals("disable") || str.equals("d") || str.equals("no") || str.equals("n")) {
      return Optional.of(false);
    } else if (str.equals("true") || str.equals("enabled") || str.equals("enable") || str.equals("e") || str.equals("yes") || str.equals("y")) {
      return Optional.of(true);
    }
    return Optional.empty();
  }

  @NonNull
  public static Class<? extends Game> parseGameMode(String mode) {
    return switch (mode.toLowerCase()) {
      case "conquest" -> Conquest.class; // TODO: Add other modes
      default -> Conquest.class;
    };
  }

  @NonNull
  public static RkpColor parseColor(String colorStr, RkpPalette palette) {
    RkpColor result = palette.last();
    int lowestDistance = Integer.MAX_VALUE;
    for (RkpColor color : palette.sortedColorSet()) {
      int distance = LevenshteinDistance.getDefaultInstance().apply(colorStr.toLowerCase(), color.name().toLowerCase());
      if (distance < 5 && distance < lowestDistance) {
        lowestDistance = distance;
        result = color;
      }
    }
    return result;
  }

  public static Optional<String> parseMapCodename(Path optionsPath, String requestedCodename) {
    if (requestedCodename == null || requestedCodename.isEmpty()) {
      return Optional.empty();
    }

    try (var pathStream = Files.list(optionsPath)) {
      Set<String> allMapCodenames = pathStream.map(path -> path.getFileName().toString().split("\\.")[0].trim()).collect(Collectors.toSet());

      String closestCodename = null;
      int lowestDistance = Integer.MAX_VALUE;
      for (String codename : allMapCodenames) {
        int distance = LevenshteinDistance.getDefaultInstance().apply(requestedCodename, codename);
        if (distance < 5 && distance < lowestDistance) {
          lowestDistance = distance;
          closestCodename = codename;
        }
      }
      return Optional.ofNullable(closestCodename);
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  public static Optional<String> parseMapNameExact(Path optionsPath, String requestedName) {
    if (requestedName == null || requestedName.isEmpty()) {
      return Optional.empty();
    }

    try (var pathStream = Files.list(optionsPath)) {
      return pathStream.map(path -> path.getFileName().toString().split("\\.")[0]).filter(name -> name.equalsIgnoreCase(requestedName)).findAny();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public static Alignment.Vertical parseVerticalAlignment(String alignment) {
    for (Alignment.Vertical v : Alignment.Vertical.values()) {
      if (v.name().equalsIgnoreCase(alignment)) {
        return v;
      }
    }
    return Alignment.Vertical.BOTTOM;
  }

  public static Alignment.Horizontal parseHorizontalAlignment(String alignment) {
    for (Alignment.Horizontal h : Alignment.Horizontal.values()) {
      if (h.name().equalsIgnoreCase(alignment)) {
        return h;
      }
    }
    return Alignment.Horizontal.LEFT;
  }

  public static Availability parseAvailability(String availability) {
    for (Availability a : Availability.values()) {
      if (a.name().equalsIgnoreCase(availability)) {
        return a;
      }
    }
    return Availability.UNAVAILABLE;
  }

  public static Flavor parseFlavor(String flavor) {
    for (Flavor f : Flavor.values()) {
      if (f.name().equalsIgnoreCase(flavor)) {
        return f;
      }
    }
    return Flavor.UNKNOWN;
  }

}
