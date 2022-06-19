package com.riskrieg.bot.util;

public class StringUtil {

  private StringUtil() {
  }

  public static String toTitleCase(String str) {
    StringBuilder titleCase = new StringBuilder(str.length());
    boolean nextTitleCase = true;

    for (char c : str.toLowerCase().toCharArray()) {
      if (Character.isSpaceChar(c)) {
        nextTitleCase = true;
      } else if (nextTitleCase) {
        c = Character.toTitleCase(c);
        nextTitleCase = false;
      }
      titleCase.append(c);
    }

    return titleCase.toString();
  }

}
