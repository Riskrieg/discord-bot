package com.riskrieg.bot.service;

public class AutomaticPingService implements Service {

    @Override
    public String name() {
        return "AutomaticPing";
    }

    @Override
    public void run() {
        System.out.println("\r[Services] " + name() + " service running.");
    }

}
