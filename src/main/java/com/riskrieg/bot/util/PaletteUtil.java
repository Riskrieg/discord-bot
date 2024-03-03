package com.riskrieg.bot.util;

import com.riskrieg.palette.RkpColor;
import com.riskrieg.palette.RkpPalette;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.aaronjyoder.fill.Filler;
import io.github.aaronjyoder.fill.recursive.BlockFiller;

import java.awt.*;
import java.awt.image.BufferedImage;

public class PaletteUtil {

    @NonNull
    public static byte[] generatePaletteDisplay(RkpPalette palette) {
        // General UI (Pixels)
        final int borderThickness = 3;

        // Color List UI (Pixels)
        final int colorItemWidth = 92;

        // Other
        final int itemHeight = 28; // Used for both colorItem and nameItem height since they should always match

        // Parameters
        final int colorListWidth = colorItemWidth + 2 * borderThickness;
        final int listHeight = borderThickness + (itemHeight + borderThickness) * palette.size(); // Same height for both lists since they should always match

        /* Draw color list */
        BufferedImage colorListImage = new BufferedImage(colorListWidth, listHeight, BufferedImage.TYPE_INT_ARGB);
        ImageUtil.fillTransparent(colorListImage);

        // Draw border
        drawBorder(colorListImage, palette.borderColor().toAwtColor(), borderThickness + 2); // Need a thickness of 5 to get 3px thickness for some reason
        drawRoundedCornerManually(colorListImage);

        // Draw dividers, add colors, add color names
        Graphics2D g = colorListImage.createGraphics();

        g.setColor(palette.borderColor().toAwtColor());
        g.setStroke(new BasicStroke(borderThickness));
        int x = borderThickness;
        int y;
        for (RkpColor color : palette.sortedColorSet()) {
            int i = color.order(); // Should always start at 0 and go sequentially by 1 from there
            y = (i + 1) * (itemHeight + borderThickness) + 1; // Set y right away so that every fill operation fills in the same number of pixels

            // Draw divider
            g.drawLine(x, y, x + colorItemWidth - 1, y);

            // Fill color
            Filler filler = new BlockFiller(colorListImage);
            filler.fill(borderThickness, borderThickness + (i * (itemHeight + borderThickness)), color.toAwtColor());

            // Draw color name
            int x1 = borderThickness;
            int y1 = borderThickness + i * (itemHeight + borderThickness);
            int x2 = x1 + colorItemWidth - 1;
            int y2 = y1 + itemHeight - 1;
            ImageUtil.drawTextWithBounds(colorListImage, color.name().toUpperCase(), RkpPalette.DEFAULT_TEXT_COLOR.toAwtColor(),
                    x1, y1, x2, y2, false, true,
                    new Font("Raleway", Font.BOLD, 15), new Font("Noto Mono", Font.BOLD, 15), 12.0F, 15.0F);
        }

        g.dispose();

        return ImageUtil.convertToByteArray(colorListImage);
    }

    private static void drawBorder(BufferedImage image, Color color, int thickness) {
        int borderAdjustment = 1;
        if (thickness % 2 == 0) {
            borderAdjustment = 0;
        }
        int width = image.getWidth();
        int height = image.getHeight();

        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(thickness));
        g2d.drawLine(0, 0, 0, height);
        g2d.drawLine(0, 0, width, 0);
        g2d.drawLine(0, height - borderAdjustment, width, height - borderAdjustment);
        g2d.drawLine(width - borderAdjustment, height - borderAdjustment, width - borderAdjustment, 0);
        g2d.dispose();
    }

    private static void drawRoundedCornerManually(BufferedImage image) { // Only works for border thickness of 3px, just doing manually for now
        int width = image.getWidth();
        int height = image.getHeight();
        int transparentRGB = new Color(0, 0, 0, 0).getRGB();
        // Top left corner
        image.setRGB(0, 0, transparentRGB);
        image.setRGB(1, 0, transparentRGB);
        image.setRGB(0, 1, transparentRGB);

        // Bottom left corner
        image.setRGB(0, height - 1, transparentRGB);
        image.setRGB(1, height - 1, transparentRGB);
        image.setRGB(0, height - 2, transparentRGB);

        // Top right corner
        image.setRGB(width - 1, 0, transparentRGB);
        image.setRGB(width - 2, 0, transparentRGB);
        image.setRGB(width - 1, 1, transparentRGB);

        // Bottom right corner
        image.setRGB(width - 1, height - 1, transparentRGB);
        image.setRGB(width - 2, height - 1, transparentRGB);
        image.setRGB(width - 1, height - 2, transparentRGB);
    }

}
