package com.riskrieg.bot.util;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

public record Interval(long period, TimeUnit unit) implements Comparable<Interval> {

    public long asMinutes() {
        return unit.toMinutes(period);
    }

    @Override
    public int compareTo(@NotNull Interval o) {
        BigInteger thisInterval = convert(this);
        BigInteger thatInterval = convert(o);

        return thisInterval.compareTo(thatInterval);
    }

    private BigInteger convert(Interval interval) {
        return BigInteger.valueOf(interval.period()).multiply(BigInteger.valueOf(interval.unit().toNanos(1)));
    }

}
