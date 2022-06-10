package com.riskrieg.bot.game;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public record Dice(int sides, int amount) {

  public int[] roll() {
    return IntStream.generate(this::rollOne).limit(amount).toArray();
  }

  private int rollOne() {
    return ThreadLocalRandom.current().nextInt(sides) + 1;
  }

}
