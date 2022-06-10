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

package com.riskrieg.bot.command.commands.riskrieg.general;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.codec.decode.RkmDecoder;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.util.io.RkJsonUtil;
import com.riskrieg.map.RkmMap;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.map.metadata.Flavor;
import com.riskrieg.palette.RkpPalette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class Maps implements Command {

  private final Settings settings;

  public Maps() {
    this.settings = new StandardSettings(
        "Display a list of maps.",
        "maps")
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
        .addOptions(OptionDataUtil.mapFlavorsDisplay());
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Set<MapBundle> bundles = loadMapBundles();

      String mapFlavorTitle = Riskrieg.NAME;
      StringBuilder description = new StringBuilder();

      Flavor mapFlavorChoice = Flavor.OFFICIAL;
      OptionMapping option = event.getOption("flavor");
      if (option != null) {
        switch (option.getAsString()) {
          default -> {
            mapFlavorTitle = "Official " + Riskrieg.NAME;
            mapFlavorChoice = Flavor.OFFICIAL;
            description.append("These are official " + Riskrieg.NAME + " maps that live up to high quality standards.").append("\n\n");
          }
          case "community" -> {
            mapFlavorTitle = "Community " + Riskrieg.NAME;
            mapFlavorChoice = Flavor.COMMUNITY;
            description.append("These are community-made " + Riskrieg.NAME + " maps that may or may not live up to the same quality standards as official maps.").append("\n\n");
          }
        }
      }

      description.append("*Map names are in bold and the number of map territories appears in parentheses after the map name.*");

      EmbedBuilder embedBuilder = new EmbedBuilder();
      embedBuilder.setColor(settings.embedColor());
      embedBuilder.setTitle(mapFlavorTitle + " Maps | v" + Riskrieg.VERSION);

      Set<MessageEmbed.Field> fields = getFields(bundles, mapFlavorChoice);
      if (!fields.isEmpty()) {
        fields.forEach(embedBuilder::addField);
      } else {
        embedBuilder.addField("Maps Unavailable", "*There are currently no maps available.*", false);
      }
      embedBuilder.setDescription(description.toString());
      embedBuilder.setFooter("If you would like to contribute, join the official " + Riskrieg.NAME + " server!");
      hook.sendMessageEmbeds(embedBuilder.build()).queue();

    });
  }

  private Set<MessageEmbed.Field> getFields(Set<MapBundle> bundles, Flavor flavorChoice) {
    Set<MessageEmbed.Field> result = new LinkedHashSet<>();
    StringBuilder epicSb = new StringBuilder();
    StringBuilder largeSb = new StringBuilder();
    StringBuilder mediumSb = new StringBuilder();
    StringBuilder smallSb = new StringBuilder();
    StringBuilder comingSoonSb = new StringBuilder();

    bundles.forEach(bundle -> {
      switch (bundle.metadata().availability()) {
        case AVAILABLE -> {
          if (bundle.metadata().flavor().equals(flavorChoice)) {
            int size = bundle.map().vertices().size();
            String displayName = bundle.map().displayName();
            displaySorted(displayName, size, smallSb, mediumSb, largeSb, epicSb);
          }
        }
        case COMING_SOON -> {
          if (bundle.metadata().flavor().equals(flavorChoice)) {
            comingSoonSb.append("**").append(bundle.map().displayName()).append("**").append("\n");
          }
        }
        case RESTRICTED -> {
          // TODO: Riskrieg-server only
        }
        case UNAVAILABLE -> {
          // Do nothing for now
        }
      }
    });

    if (!epicSb.isEmpty()) {
      result.add(new MessageEmbed.Field("Epic", epicSb.toString(), false));
    }
    if (!largeSb.isEmpty()) {
      result.add(new MessageEmbed.Field("Large", largeSb.toString(), true));
    }
    if (!mediumSb.isEmpty()) {
      result.add(new MessageEmbed.Field("Medium", mediumSb.toString(), true));
    }
    if (!smallSb.isEmpty()) {
      result.add(new MessageEmbed.Field("Small", smallSb.toString(), true));
    }
    if (!comingSoonSb.isEmpty()) {
      result.add(new MessageEmbed.Field("Coming Soon", comingSoonSb.toString(), false));
    }
    return result;
  }

  private void displaySorted(String displayName, int size, StringBuilder smallSb, StringBuilder mediumSb, StringBuilder largeSb, StringBuilder epicSb) {
    if (size > 0 && size < 65) {
      smallSb.append("**").append(displayName).append("** (").append(size).append(")").append("\n");
    } else if (size >= 65 && size < 125) {
      mediumSb.append("**").append(displayName).append("** (").append(size).append(")").append("\n");
    } else if (size >= 125 && size < 200) {
      largeSb.append("**").append(displayName).append("** (").append(size).append(")").append("\n");
    } else if (size >= 200) {
      epicSb.append("**").append(displayName).append("** (").append(size).append(")").append("\n");
    }
  }

  private Set<MapBundle> loadMapBundles() {
    Set<MapBundle> result = new TreeSet<>();

    try (var pathStream = Files.list(Path.of(BotConstants.MAP_OPTIONS_PATH))) {
      Set<String> allMapCodenames = pathStream.map(path -> path.getFileName().toString().split("\\.")[0].trim()).collect(Collectors.toSet());

      for (String codename : allMapCodenames) {
        RkmMap map = new RkmDecoder().decode(Path.of(BotConstants.MAP_PATH + codename + ".rkm"));
        RkmMetadata metadata = RkJsonUtil.read(Path.of(BotConstants.MAP_OPTIONS_PATH + codename + ".json"), RkmMetadata.class);
        if (metadata != null) {
          result.add(new MapBundle(map, metadata));
        }
      }
      return result;
    } catch (Exception e) {
      return new TreeSet<>();
    }
  }

}

record MapBundle(@Nonnull RkmMap map, @Nonnull RkmMetadata metadata) implements Comparable<MapBundle> {

  @Override
  public int compareTo(@Nonnull MapBundle o) {
    int n = Integer.compare(o.map().vertices().size(), this.map().vertices().size());
    if (n == 0) {
      n = o.map().codename().compareTo(this.map().codename());
    }
    return n;
  }

}
