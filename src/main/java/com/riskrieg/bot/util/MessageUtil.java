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

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.core.api.Riskrieg;
import java.time.Instant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

public class MessageUtil {

  public static MessageCreateData error(Settings cmdSettings, String description) {
    MessageCreateBuilder builder = new MessageCreateBuilder();

    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setColor(BotConstants.ERROR_COLOR);
    embedBuilder.setTitle("Error: " + cmdSettings.name());
    embedBuilder.setDescription(description);
    embedBuilder.setFooter("Version: " + Riskrieg.VERSION);
    embedBuilder.setTimestamp(Instant.now());

    builder.setEmbeds(embedBuilder.build());
    return builder.build();
  }

  public static MessageCreateData success(Settings cmdSettings, String description) {
    MessageCreateBuilder builder = new MessageCreateBuilder();

    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setColor(BotConstants.SUCCESS_COLOR);
    embedBuilder.setTitle("Success: " + cmdSettings.name());
    embedBuilder.setDescription(description);
    embedBuilder.setFooter("Version: " + Riskrieg.VERSION);
    embedBuilder.setTimestamp(Instant.now());

    builder.setEmbeds(embedBuilder.build());
    return builder.build();
  }

  public static MessageCreateData success(Settings cmdSettings, String description, FileUpload fileUpload) {
    MessageCreateBuilder builder = new MessageCreateBuilder();

    EmbedBuilder embedBuilder = new EmbedBuilder();
    embedBuilder.setColor(BotConstants.SUCCESS_COLOR);
    embedBuilder.setTitle("Success: " + cmdSettings.name());
    embedBuilder.setDescription(description);
    embedBuilder.setImage("attachment://" + fileUpload.getName());
    embedBuilder.setFooter("Version: " + Riskrieg.VERSION);
    embedBuilder.setTimestamp(Instant.now());

    builder.setEmbeds(embedBuilder.build()).addFiles(fileUpload);
    return builder.build();
  }

}
