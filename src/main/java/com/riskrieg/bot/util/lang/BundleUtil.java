package com.riskrieg.bot.util.lang;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import net.dv8tion.jda.api.interactions.DiscordLocale;

public class BundleUtil {

  public static ResourceBundle getExternalBundle(Command command, DiscordLocale locale) {
    try {
      return new PropertyResourceBundle(Files.newInputStream(Path.of(BotConstants.LANG_COMMAND_PROPERTIES_PATH
          + command.settings().name() + "/" + command.settings().name() + "_" + locale.getLocale() + ".properties")));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
