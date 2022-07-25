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

package com.riskrieg.bot.command.commands.riskrieg.restricted.owner;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.codec.decode.RkmDecoder;
import com.riskrieg.codec.encode.RkmEncoder;
import com.riskrieg.core.util.io.RkJsonUtil;
import com.riskrieg.map.RkmMap;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.map.metadata.Alignment;
import com.riskrieg.map.metadata.Availability;
import com.riskrieg.map.metadata.Flavor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

public class AddMap implements Command {

  private final Settings settings;

  public AddMap() {
    this.settings = new StandardSettings(
        "Owner only. Add a new map to the game.",
        "addmap")
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
    return Commands.slash(settings().name(), settings().description())
        .addOptions(
            new OptionData(OptionType.ATTACHMENT, "file", "A valid .rkm map file.", true),
            OptionDataUtil.verticalAlignment().setRequired(true),
            OptionDataUtil.horizontalAlignment().setRequired(true)
        )
        .addOption(OptionType.BOOLEAN, "overwrite", "Whether the map file should be overwritten if it already exists.",
            false)
        .setDefaultPermissions(DefaultMemberPermissions.DISABLED);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      OptionMapping mapFileOpt = event.getOption("file");
      OptionMapping vAlignOpt = event.getOption("vertical-align");
      OptionMapping hAlignOpt = event.getOption("horizontal-align");
      OptionMapping overwriteOpt = event.getOption("overwrite");

      if (vAlignOpt != null && hAlignOpt != null && mapFileOpt != null) {
        var vAlign = ParseUtil.parseVerticalAlignment(vAlignOpt.getAsString());
        var hAlign = ParseUtil.parseHorizontalAlignment(hAlignOpt.getAsString());
        boolean overwrite = overwriteOpt != null && overwriteOpt.getAsBoolean();

        try {
          RkmMap map = new RkmDecoder().decode(new URL(mapFileOpt.getAsAttachment().getUrl()));
          boolean metadataExists = ParseUtil.parseMapNameExact(Path.of(BotConstants.MAP_METADATA_PATH), map.codename())
              .isPresent();
          if (metadataExists && !overwrite) {
            hook.sendMessage(MessageUtil.error(settings, "A map with that name already exists.")).queue();
            return;
          }

          RkmEncoder encoder = new RkmEncoder();
          OutputStream outputStream = new FileOutputStream(BotConstants.MAP_PATH + map.codename() + ".rkm");

          encoder.encode(map, outputStream);
          outputStream.close();

          Alignment alignment = new Alignment(vAlign, hAlign);
          RkmMetadata metadata = new RkmMetadata(Flavor.COMMUNITY, Availability.COMING_SOON, alignment);
          RkJsonUtil.write(Path.of(BotConstants.MAP_METADATA_PATH + map.codename() + ".json"), RkmMetadata.class,
              metadata);

          hook.sendMessage(MessageUtil.success(settings, "Successfully added map: **" + map.displayName() + "**\n"
                  + "Overwritten: **" + overwrite + "**\n"
                  + "Flavor: **" + metadata.flavor().toString() + "**\n"
                  + "Availability: **" + metadata.availability().name() + "**\n"))
              .queue();
        } catch (Exception e) {
          hook.sendMessage(MessageUtil.error(settings, "Invalid map file.")).queue();
        }
      } else {
        hook.sendMessage(MessageUtil.error(settings, "Invalid arguments.")).queue();
      }

    });
  }

}
