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
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.ParseUtil;
import com.riskrieg.bot.util.RiskriegUtil;
import com.riskrieg.bot.util.lang.RkLocalizationFunction;
import com.riskrieg.codec.decode.RkmDecoder;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.core.util.io.RkJsonUtil;
import com.riskrieg.map.RkmMap;
import com.riskrieg.map.RkmMetadata;
import com.riskrieg.map.metadata.Availability;
import com.riskrieg.palette.RkpPalette;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class MapSelect implements Command {

  private final Settings settings;

  public MapSelect() {
    this.settings = new StandardSettings(
        "Select a " + Riskrieg.NAME + " map.",
        "map")
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
        .addOption(OptionType.STRING, "title", "Type the name of the map you want to select.", true)
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

      MessageCreateData genericSuccess = MessageUtil.success(settings, "You have selected a map."); // First message has to be ephemeral, so send this.

      // Guard clauses
      Member member = event.getMember();
      if (member == null) {
        hook.sendMessage(MessageUtil.error(settings, "Could not find member.")).queue();
        return;
      }

      Guild guild = event.getGuild();
      if (guild == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid guild.")).queue();
        return;
      }

      OptionMapping mapCodenameOpt = event.getOption("title");
      if (mapCodenameOpt == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid map name.")).queue();
        return;
      }

      Optional<String> mapCodename = ParseUtil.parseMapCodename(Path.of(BotConstants.MAP_METADATA_PATH), mapCodenameOpt.getAsString());
      if (mapCodename.isEmpty()) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid map name.")).queue();
        return;
      }

      try {
        RkmMap selectedMap = new RkmDecoder().decode(Path.of(BotConstants.MAP_PATH + mapCodename.get() + ".rkm"));
        RkmMetadata metadata = RkJsonUtil.read(Path.of(BotConstants.MAP_METADATA_PATH + mapCodename.get() + ".json"), RkmMetadata.class);

        if (metadata != null && metadata.availability().equals(Availability.AVAILABLE)) {

          // Command execution
          Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
          api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
                if (game.players().stream().anyMatch(player -> player.identifier().equals(PlayerIdentifier.of(member.getId())))) {
                  game.selectMap(selectedMap).queue(map -> {
                    String fileName = map.codename() + ".png";
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setColor(settings.embedColor());
                    embedBuilder.setAuthor("Map created by " + map.author());
                    embedBuilder.setTitle("Map Selected: " + map.displayName());
                    embedBuilder.setDescription(member.getAsMention() + " has selected this map.");
                    embedBuilder.setFooter("If you have not selected a territory, you may select one.");
                    embedBuilder.setImage("attachment://" + fileName);

                    hook.sendMessage(genericSuccess).queue(success -> {
                      hook.sendMessageEmbeds(embedBuilder.build()).addFiles(FileUpload.fromData(RiskriegUtil.constructMapImageData(game), fileName)).queue();
                      group.saveGame(game).queue();
                    });
                  });
                } else {
                  hook.sendMessage(MessageUtil.error(settings, "Maps can only be selected by players in the game.")).queue();
                }
              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
          ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

        } else {
          hook.sendMessage(MessageUtil.error(settings, "That map is not available.")).queue();
        }
      } catch (IOException | NoSuchAlgorithmException e) {
        hook.sendMessage(MessageUtil.error(settings, "Could not load map or map metadata: " + e.getMessage())).queue();
      }

    });
  }

}
