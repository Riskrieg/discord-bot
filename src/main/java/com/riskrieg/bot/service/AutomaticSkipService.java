package com.riskrieg.bot.service;

public class AutomaticSkipService implements Service {

    @Override
    public String name() {
        return "AutomaticSkip";
    }

    @Override
    public void run() {
        System.out.println("\r[Services] " + name() + " service running.");
    }

}
