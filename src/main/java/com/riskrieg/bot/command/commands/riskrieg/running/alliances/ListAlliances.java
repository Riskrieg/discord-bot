/*
 *     Riskrieg, an open-source conflict simulation game.
 *     Copyright (C) 2018-2022 Aaron Yoder <aaronjyoder@gmail.com> and Contributors
 *     Copyright (C) 2021 ZeldaXD
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

package com.riskrieg.bot.command.commands.riskrieg.running.alliances;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.riskrieg.bot.BotConstants;
import com.riskrieg.bot.command.Command;
import com.riskrieg.bot.command.settings.Settings;
import com.riskrieg.bot.command.settings.StandardSettings;
import com.riskrieg.bot.util.ImageUtil;
import com.riskrieg.bot.util.MessageUtil;
import com.riskrieg.core.api.Riskrieg;
import com.riskrieg.core.api.RiskriegBuilder;
import com.riskrieg.core.api.game.Game;
import com.riskrieg.core.api.game.entity.nation.Nation;
import com.riskrieg.core.api.game.entity.player.Player;
import com.riskrieg.core.api.game.feature.Feature;
import com.riskrieg.core.api.game.feature.alliance.AllianceStatus;
import com.riskrieg.core.api.identifier.GameIdentifier;
import com.riskrieg.core.api.identifier.GroupIdentifier;
import com.riskrieg.core.api.identifier.PlayerIdentifier;
import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

public class ListAlliances implements Command {

  private final Settings settings;

  public ListAlliances() {
    this.settings = new StandardSettings(
        "List your alliances.",
        "alliances")
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
        .addSubcommands(
            new SubcommandData("list", "Show your alliances as a list.")
                .addOption(OptionType.USER, "player", "Show a specific player's alliances as a list."),
            new SubcommandData("graph", "Show all alliances as a graph.")
        );
  }

  @Override
  public void execute(SlashCommandInteractionEvent event) {
    event.deferReply(true).queue(hook -> {

      // Guard clauses
      Guild guild = event.getGuild();
      if (guild == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid guild.")).queue();
        return;
      }

      String subcommandName = event.getSubcommandName();
      if (subcommandName == null) {
        hook.sendMessage(MessageUtil.error(settings, "Invalid subcommand.")).queue();
        return;
      }

      switch (subcommandName) {
        default -> hook.sendMessage(MessageUtil.error(settings, "Invalid subcommand.")).queue();
        case "list" -> {
          final Member player;
          OptionMapping playerOption = event.getOption("player");
          if (playerOption == null) {
            player = event.getMember();
          } else {
            player = playerOption.getAsMember();
          }
          processListSubcommand(player, guild, hook, event.getChannel());
        }
        case "graph" -> processGraphSubcommand(guild, hook, event.getChannel());
      }

    });
  }

  /* List Subcommand */

  private void processListSubcommand(Member member, Guild guild, InteractionHook hook, MessageChannel channel) {
    if (member == null) {
      hook.sendMessage(MessageUtil.error(settings, "Could not find member.")).queue();
      return;
    }

    Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
    api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(channel.getId())).queue(game -> {
          if (game.isFeatureEnabled(Feature.ALLIANCES)) {
            var optPlayer = game.getPlayer(PlayerIdentifier.of(member.getId()));
            var optNation = game.getNation(PlayerIdentifier.of(member.getId()));
            if (optPlayer.isPresent() && optNation.isPresent()) {
              Player player = optPlayer.get();
              Nation nation = optNation.get();

              EmbedBuilder embedBuilder = new EmbedBuilder();
              embedBuilder.setTitle(player.name() + "'s Allies");
              embedBuilder.setColor(game.palette().get(nation.colorId()).orElse(game.palette().last()).toAwtColor());

              StringBuilder alliedSb = new StringBuilder();
              StringBuilder outgoingRequests = new StringBuilder();
              StringBuilder incomingRequests = new StringBuilder();

              var alliedPlayers = getAlliedPlayers(nation, game);
              var outgoingRequestPlayers = getOutgoingRequests(nation, game);
              var incomingRequestPlayers = getIncomingRequests(nation, game);

              for (Player ally : alliedPlayers) {
                alliedSb.append("**").append(ally.name()).append("**").append("\n");
              }

              for (Player outgoing : outgoingRequestPlayers) {
                outgoingRequests.append("**").append(outgoing.name()).append("**").append("\n");
              }

              for (Player incoming : incomingRequestPlayers) {
                incomingRequests.append("**").append(incoming.name()).append("**").append("\n");
              }

              if (alliedSb.isEmpty()) {
                embedBuilder.addField("Allies", "*None.*", true);
              } else {
                embedBuilder.addField("Allies", alliedSb.toString(), true);
              }
              if (outgoingRequests.isEmpty()) {
                embedBuilder.addField("Outgoing Requests", "*No pending requests.*", true);
              } else {
                embedBuilder.addField("Outgoing Requests", outgoingRequests.toString(), true);
              }
              if (incomingRequests.isEmpty()) {
                embedBuilder.addField("Incoming Requests", "*No pending requests.*", true);
              } else {
                embedBuilder.addField("Incoming Requests", incomingRequests.toString(), true);
              }

              hook.sendMessageEmbeds(embedBuilder.build()).queue();
            } else {
              hook.sendMessage(MessageUtil.error(settings, "Player is not in game.")).queue();
            }
          } else {
            hook.sendMessage(MessageUtil.error(settings, "Alliances are not enabled on this game.")).queue();
          }
        }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
    ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
  }

  private Set<Player> getAlliedPlayers(Nation nation, Game game) {
    Set<Player> alliedPlayers = new HashSet<>();

    Set<Nation> alliedNations = game.getAllies(nation.identifier());
    for (Nation n : alliedNations) {
      var p = game.getPlayer(n.leaderIdentifier());
      p.ifPresent(alliedPlayers::add);
    }

    return Collections.unmodifiableSet(alliedPlayers);
  }

  private Set<Player> getOutgoingRequests(Nation nation, Game game) {
    Set<PlayerIdentifier> identifiers = new HashSet<>();

    game.nations().forEach(n -> {
      if (game.allianceStatus(nation.identifier(), n.identifier()).equals(AllianceStatus.SENT)) {
        identifiers.add(n.leaderIdentifier());
      }
    });

    return new HashSet<>(game.players()).stream().filter(p -> identifiers.contains(p.identifier())).collect(Collectors.toUnmodifiableSet());
  }

  private Set<Player> getIncomingRequests(Nation nation, Game game) {
    Set<PlayerIdentifier> identifiers = new HashSet<>();

    game.nations().forEach(n -> {
      if (game.allianceStatus(nation.identifier(), n.identifier()).equals(AllianceStatus.RECEIVED)) {
        identifiers.add(n.leaderIdentifier());
      }
    });

    return new HashSet<>(game.players()).stream().filter(p -> identifiers.contains(p.identifier())).collect(Collectors.toUnmodifiableSet());
  }

  /* Graph Subcommand */

  private void processGraphSubcommand(Guild guild, InteractionHook hook, MessageChannel channel) {
    Riskrieg api = RiskriegBuilder.createLocal(Path.of(BotConstants.REPOSITORY_PATH)).build();
    api.retrieveGroup(GroupIdentifier.of(guild.getId())).queue(group -> group.retrieveGame(GameIdentifier.of(channel.getId())).queue(game -> {
          if (game.isFeatureEnabled(Feature.ALLIANCES)) {
            //Create the image from the graph
            mxGraph graph = generateAllyGraph(game);

            mxCircleLayout layout = new mxCircleLayout(graph);
            layout.setDisableEdgeStyle(false);
            layout.execute(graph.getDefaultParent());

            BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 2, RkpPalette.DEFAULT_TERRITORY_COLOR.toAwtColor(), true, null);
            if (image == null) {
              image = new BufferedImage(256, 144, BufferedImage.TYPE_INT_ARGB);
              Graphics2D graphics = image.createGraphics();
              graphics.setColor(RkpPalette.DEFAULT_TERRITORY_COLOR.toAwtColor());
              graphics.fillRect(0, 0, 256, 144);
            }
            String fileName = "ally-graph.png";

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle("Alliances Graph");
            embedBuilder.setImage("attachment://" + fileName);
            embedBuilder.setColor(settings.embedColor());
            hook.sendMessageEmbeds(embedBuilder.build()).addFile(ImageUtil.convertToByteArray(image), fileName, new AttachmentOption[0]).queue();
          } else {
            hook.sendMessage(MessageUtil.error(settings, "Alliances are not enabled on this game.")).queue();
          }
        }, failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue()
    ), failure -> hook.sendMessage(MessageUtil.error(settings, failure.getMessage())).queue());
  }

  private mxGraph generateAllyGraph(Game game) {
    mxGraph graph = new mxGraph();

    String borderColor = String.format("%06x", 0xFFFFFF & RkpPalette.DEFAULT_BORDER_COLOR.toAwtColor().getRGB());
    HashMap<String, Object> vertices = new HashMap<>();

    // Create the graph by adding the player names as the vertices and then associating them
    for (Player player : game.players()) {
      String name = player.name();
      Nation nation = game.getNation(player.identifier()).orElse(null);
      if (nation != null) {
        RkpColor rkpColor = game.palette().get(nation.colorId()).orElse(game.palette().last());
        String color = String.format("%06x", 0xFFFFFF & rkpColor.toAwtColor().getRGB());
        vertices.put(name, graph.insertVertex(graph.getDefaultParent(), name, null, 0, 0, 15, 15,
            "ellipse;whiteSpace=wrap;html=1;aspect=fixed;strokeWidth=1;noLabel=1;fillColor=#" + color + ";strokeColor=#" + borderColor));
      }
    }

    // Add the edges
    for (Player player : game.players()) {
      Nation nation = game.getNation(player.identifier()).orElse(null);

      if (nation != null) {
        RkpColor rkpColor = game.palette().get(nation.colorId()).orElse(game.palette().last());
        String color = String.format("%06x", 0xFFFFFF & rkpColor.toAwtColor().getRGB());

        Set<Nation> allies = game.getAllies(nation.identifier());

        for (Nation ally : allies) {
          var optAlly = game.getPlayer(ally.leaderIdentifier());
          if (optAlly.isPresent()) {
            String name = player.name();
            String nameAlly = optAlly.get().name();
            String style = "endArrow=classic;startArrow=none;strokeWidth=1;curved=1;snapToPoint=1;dashed=1;dashPattern=1;noLabel=1;strokeColor=#" + color;
            if (game.allianceStatus(nation.identifier(), ally.identifier()).equals(AllianceStatus.COMPLETE)) {
              style = "endArrow=none;startArrow=none;strokeWidth=1;curved=1;snapToPoint=1;noLabel=1;strokeColor=#" + borderColor;
            }
            graph.insertEdge(graph.getDefaultParent(), "", null, vertices.get(name), vertices.get(nameAlly), style);
          }
        }
      }
    }

    var vertexStyle = graph.getStylesheet().getDefaultVertexStyle();
    vertexStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
    mxConstants.STYLE_ALIGN = mxConstants.ALIGN_CENTER;
    // If labels in vertices are to be set back on, uncomment this and set noLabel to 0 in style vertices and also increase the width and height
//    vertexStyle.put(mxConstants.STYLE_LABEL_BACKGROUNDCOLOR, "#e0dad9");
//    mxConstants.STYLE_ALIGN = mxConstants.ALIGN_CENTER;
//    mxConstants.LABEL_INSET=1;

    var edgeStyle = graph.getStylesheet().getDefaultEdgeStyle();
    edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.SHAPE_LINE);
//    edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_TOPTOBOTTOM);
//    edgeStyle.put(mxConstants.STYLE_ENDARROW, "none");

    graph.setAllowDanglingEdges(false);

    return graph;
  }

}
