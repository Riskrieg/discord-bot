package com.riskrieg.bot.util.lang;

import com.riskrieg.bot.command.Command;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction;
import net.dv8tion.jda.internal.utils.Checks;

public class RkLocalizationFunction implements LocalizationFunction {

  private final Set<Bundle> bundles;

  private RkLocalizationFunction(Set<Bundle> bundles) {
    this.bundles = bundles;
  }

  @Nonnull
  @Override
  public Map<DiscordLocale, String> apply(@Nonnull String localizationKey) {
    final Map<DiscordLocale, String> map = new HashMap<>();
    for (Bundle bundle : bundles) {
      final ResourceBundle resourceBundle = bundle.resourceBundle;
      if (resourceBundle.containsKey(localizationKey)) {
        map.put(bundle.targetLocale, resourceBundle.getString(localizationKey));
      }
    }

    return map;
  }

  @Nonnull
  public static Builder fromBundle(@Nonnull ResourceBundle resourceBundle, @Nonnull DiscordLocale locale) {
    return new Builder().addBundle(resourceBundle, locale);
  }

  @Nonnull
  public static Builder fromBundles(@Nonnull String baseName, @Nonnull DiscordLocale... locales) {
    return new Builder().addBundles(baseName, locales);
  }

  @Nonnull
  public static Builder fromExternalBundles(@Nonnull Command command, @Nonnull DiscordLocale... locales) {
    return new Builder().addExternalBundles(command, locales);
  }

  @Nonnull
  public static Builder empty() {
    return new Builder();
  }

  public static class Builder {

    private final Set<Bundle> bundles = new HashSet<>();

    protected Builder() {
    }

    @Nonnull
    public Builder addBundle(@Nonnull ResourceBundle resourceBundle, @Nonnull DiscordLocale locale) {
      Checks.notNull(resourceBundle, "Resource bundle");
      Checks.notNull(locale, "Locale");
      Checks.check(locale != DiscordLocale.UNKNOWN, "Cannot use UNKNOWN DiscordLocale");

      bundles.add(new Bundle(locale, resourceBundle));
      return this;
    }

    @Nonnull
    public Builder addBundles(@Nonnull String baseName, @Nonnull DiscordLocale... locales) {
      Checks.notNull(baseName, "Base name");
      Checks.noneNull(locales, "Locale");

      for (DiscordLocale locale : locales) {
        Checks.check(locale != DiscordLocale.UNKNOWN, "Cannot use UNKNOWN DiscordLocale");

        final ResourceBundle resourceBundle = ResourceBundle.getBundle(baseName,
            Locale.forLanguageTag(locale.getLocale()));
        bundles.add(new Bundle(locale, resourceBundle));
      }
      return this;
    }

    @Nonnull
    public Builder addExternalBundles(@Nonnull Command command, @Nonnull DiscordLocale... locales) {
      Checks.notNull(command, "Command");
      Checks.noneNull(locales, "Locale");

      for (DiscordLocale locale : locales) {
        Checks.check(locale != DiscordLocale.UNKNOWN, "Cannot use UNKNOWN DiscordLocale");

        final ResourceBundle resourceBundle = BundleUtil.getExternalBundle(command, locale);
        bundles.add(new Bundle(locale, resourceBundle));
      }
      return this;
    }

    @Nonnull
    public RkLocalizationFunction build() {
      return new RkLocalizationFunction(bundles);
    }
  }

  private static final class Bundle {

    private final DiscordLocale targetLocale;
    private final ResourceBundle resourceBundle;

    public Bundle(DiscordLocale targetLocale, ResourceBundle resourceBundle) {
      this.targetLocale = targetLocale;
      this.resourceBundle = resourceBundle;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Bundle bundle)) {
        return false;
      }

      if (!targetLocale.equals(bundle.targetLocale)) {
        return false;
      }
      return resourceBundle.equals(bundle.resourceBundle);
    }

    @Override
    public int hashCode() {
      return Objects.hash(targetLocale, resourceBundle);
    }
  }
}
