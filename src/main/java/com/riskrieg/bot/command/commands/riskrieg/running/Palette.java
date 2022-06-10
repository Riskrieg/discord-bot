package com.riskrieg.bot.command.commands.riskrieg.running;

import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.bot.util.OptionDataUtil;
import com.riskrieg.codec.decode.RkpDecoder;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpPalette;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;

public class Palette implements Command {

  private final Settings settings;

  public Palette() {
    this.settings = new StandardSettings(
        "Changes the palette of the current game.",
        "palette")
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
        .addOptions(OptionDataUtil.palettes().setRequired(false))
        .addOption(OptionType.ATTACHMENT, "custom", "Provide your own palette file.", false);
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      Message genericSuccess = MessageUtil.success(settings, "Received command request."); // First message has to be ephemeral, so send this.

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

      Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();

      Optional<RkpPalette> optPalette = getPalette(event);
      if (optPalette.isEmpty()) {
        api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
              // TODO: Send a palette preview
              hook.sendMessage(MessageUtil.success(settings, "The current palette is **" + game.palette().name() + "**.")).queue();
            }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
        ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
        return;
      }
      final RkpPalette palette = optPalette.get();

      // Command execution
      api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(event.getChannel().getId())).queue(game -> {
            if (game.players().stream().anyMatch(player -> player.identifier().equals(PlayerIdentifier.of(member.getId())))) {
              game.setPalette(palette).queue(success -> {

                hook.sendMessage(genericSuccess).queue(success2 -> {
                  hook.sendMessage(MessageUtil.success(settings, "The palette was successfully updated to **" + palette.name() + "**.")).queue();
                });
                group.saveGame(game).queue();
              }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
            } else {
              hook.sendMessage(MessageUtil.error(settings, "Palettes can only be selected by players in the game.")).queue();
            }
          }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
      ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());

    });
  }

  private Optional<RkpPalette> getPalette(SlashCommandInteractionEvent event) {
    OptionMapping paletteOpt = event.getOption("palette");
    OptionMapping paletteOverride = event.getOption("custom");
    if (paletteOpt == null && paletteOverride == null) {
      return Optional.empty();
    }

    RkpPalette palette = paletteOpt == null ? RkpPalette.standard() : switch (paletteOpt.getAsString().toLowerCase()) {
      case "original" -> RkpPalette.original();
      default -> RkpPalette.standard();
    };

    if (paletteOverride != null) {
      try {
        palette = new RkpDecoder().decode(new URL(paletteOverride.getAsAttachment().getUrl()));
      } catch (Exception e) {
        palette = RkpPalette.standard();
      }
    }
    return Optional.of(palette);
  }

}
