/*
 *     Riskrieg, an open-source conflict simulation game.
 *     Copyright (C) 2021-2022 Aaron Yoder <aaronjyoder@gmail.com> and Contributors
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

package com.riskrieg.bot.command.commands.riskrieg.restricted.owner;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.codec.decode.RkmDecoder;
import com.riskrieg.core.util.io.RkJsonUtil;
import com.riskrieg.map.RkmMap;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.map.metadata.Alignment;
import com.riskrieg.map.metadata.Availability;
import com.riskrieg.map.metadata.Flavor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;

public class AdjustMap implements Command {

  private final Settings settings;

  public AdjustMap() {
    this.settings = new StandardSettings(
        "Owner only. Set attributes in an existing map's metadata file.",
        "adjustmap")
        .withColor(BotConstants.MOD_CMD_COLOR)
        .makeOwnerOnly();
  }

  @NotNull
  @Override
  public Settings settings() {
    return settings;
  }

  @Override
  public CommandData commandData() {
    SubcommandData adjustAlignment = new SubcommandData("alignment", "Adjust the horizontal and vertical alignment of the UI elements on the map.");
    adjustAlignment.addOption(OptionType.STRING, "map", "The name of the map to adjust the alignment for.", true);
    adjustAlignment.addOptions(OptionDataUtil.verticalAlignment().setRequired(true), OptionDataUtil.horizontalAlignment().setRequired(true));

    SubcommandData adjustAvailability = new SubcommandData("availability", "Adjust the availability of the map.");
    adjustAvailability.addOption(OptionType.STRING, "map", "The name of the map to adjust the availability for.", true);
    adjustAvailability.addOptions(OptionDataUtil.availability().setRequired(true));

    SubcommandData adjustFlavor = new SubcommandData("flavor", "Adjust the flavor of the map.");
    adjustFlavor.addOption(OptionType.STRING, "map", "The name of the map to adjust the flavor for.", true);
    adjustFlavor.addOptions(OptionDataUtil.flavors().setRequired(true));

    return Commands.slash(settings().name(), settings().description())
        .addSubcommands(adjustFlavor, adjustAvailability, adjustAlignment)
        .setDefaultPermissions(DefaultMemberPermissions.DISABLED);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) { // TODO: Rewrite this in a better way
    event.deferReply().queue(hook -> {

      String subcommandName = event.getSubcommandName();
      if (subcommandName == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid subcommand.")).queue();
        return;
      }

      var map = parseMap(event.getOption("map"));
      if (map.isEmpty()) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid map.")).queue();
        return;
      }

      switch (subcommandName) {
        default -> hook.sendMessage(MessageUtil.error(settings, "Invalid subcommand.")).queue();
        case "alignment" -> {
          OptionMapping vAlignOpt = event.getOption("vertical-align");
          OptionMapping hAlignOpt = event.getOption("horizontal-align");
          if (vAlignOpt == null || hAlignOpt == null) {
            hook.sendMessage(MessageUtil.error(settings, "Invalid alignment parameter.")).queue();
            return;
          }

          Alignment.Vertical vAlign = ParseUtil.parseVerticalAlignment(vAlignOpt.getAsString());
          Alignment.Horizontal hAlign = ParseUtil.parseHorizontalAlignment(hAlignOpt.getAsString());

          Alignment alignment = new Alignment(vAlign, hAlign);
          try {
            RkmMetadata currentMetadata = RkJsonUtil.read(Path.of(BotConstants.MAP_METADATA_PATH + map.get().codename() + ".json"), RkmMetadata.class);
            if (currentMetadata != null) {
              currentMetadata = currentMetadata.withAlignment(alignment);
              RkJsonUtil.write(Path.of(BotConstants.MAP_METADATA_PATH + map.get().codename() + ".json"), RkmMetadata.class, currentMetadata);
              hook.sendMessage(MessageUtil.success(settings, "Alignment values for " + map.get().codename() + " have successfully been adjusted.")).queue();
            } else {
              hook.sendMessage(MessageUtil.error(settings, "Could not find current map metadata.")).queue();
            }
          } catch (IOException e) {
            hook.sendMessage(MessageUtil.error(settings, "Could not write file.")).queue();
          }
        }
        case "availability" -> {
          OptionMapping availabilityOpt = event.getOption("availability");
          if (availabilityOpt == null) {
            hook.sendMessage(MessageUtil.error(settings, "Invalid availability parameter.")).queue();
            return;
          }

          Availability availability = ParseUtil.parseAvailability(availabilityOpt.getAsString());

          try {
            RkmMetadata currentMetadata = RkJsonUtil.read(Path.of(BotConstants.MAP_METADATA_PATH + map.get().codename() + ".json"), RkmMetadata.class);
            if (currentMetadata != null) {
              currentMetadata = currentMetadata.withAvailability(availability);
              RkJsonUtil.write(Path.of(BotConstants.MAP_METADATA_PATH + map.get().codename() + ".json"), RkmMetadata.class, currentMetadata);
              hook.sendMessage(MessageUtil.success(settings,
                  "Availability for " + map.get().codename() + " has successfully been adjusted to " + availability.name() + ".")).queue();
            } else {
              hook.sendMessage(MessageUtil.error(settings, "Could not find current map metadata.")).queue();
            }
          } catch (IOException e) {
            hook.sendMessage(MessageUtil.error(settings, "Could not write file.")).queue();
          }
        }
        case "flavor" -> {
          OptionMapping flavorOpt = event.getOption("flavor");
          if (flavorOpt == null) {
            hook.sendMessage(MessageUtil.error(settings, "Invalid flavor parameter.")).queue();
            return;
          }

          Flavor flavor = ParseUtil.parseFlavor(flavorOpt.getAsString());

          try {
            RkmMetadata currentMetadata = RkJsonUtil.read(Path.of(BotConstants.MAP_METADATA_PATH + map.get().codename() + ".json"), RkmMetadata.class);
            if (currentMetadata != null) {
              currentMetadata = currentMetadata.withFlavor(flavor);
              RkJsonUtil.write(Path.of(BotConstants.MAP_METADATA_PATH + map.get().codename() + ".json"), RkmMetadata.class, currentMetadata);
              hook.sendMessage(MessageUtil.success(settings,
                  "Flavor for " + map.get().codename() + " has successfully been adjusted to " + flavor.name() + ".")).queue();
            } else {
              hook.sendMessage(MessageUtil.error(settings, "Could not find current map metadata.")).queue();
            }
          } catch (IOException e) {
            hook.sendMessage(MessageUtil.error(settings, "Could not write file.")).queue();
          }
        }
      }

    });
  }

  private Optional<RkmMap> parseMap(OptionMapping mapping) {
    if (mapping == null) {
      return Optional.empty();
    }
    try {
      var closestName = ParseUtil.parseMapCodename(Path.of(BotConstants.MAP_METADATA_PATH), mapping.getAsString());
      if (closestName.isPresent()) {
        return Optional.of(new RkmDecoder().decode(Path.of(BotConstants.MAP_PATH + closestName.get() + ".rkm")));
      }
      return Optional.empty();
    } catch (Exception e) {
      return Optional.empty();
    }
  }


}
