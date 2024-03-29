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

package com.riskrieg.bot.listener;

import com.riskrieg.bot.command.handler.InteractionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class InteractionListener extends ListenerAdapter {

  private final InteractionHandler handler;

  public InteractionListener(InteractionHandler handler) {
    this.handler = handler;
  }

  @Override
  public void onSlashCommandInteraction(@NonNull SlashCommandInteractionEvent event) {
    handler.process(event);
  }

  @Override
  public void onModalInteraction(@NonNull ModalInteractionEvent event) {
    // TODO: Implement modal handler
  }
}
