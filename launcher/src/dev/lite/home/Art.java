package dev.lite.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/** Artwork helpers: colour sampling and flat tiles for apps that only ship a square icon. */
final class Art {
    private Art() {}

    /** A dark, saturated version of a drawable's dominant colour. */
    static int accentOf(Drawable d) {
        float[] hsv = dominantHsv(d);
        if (hsv == null) return 0xFF1B2433;
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = 0.28f;
        return Color.HSVToColor(hsv);
    }

    /** Gradient colours behind a square icon, from the icon's own colour (slate for greyscale icons). */
    static int[] washColors(Drawable icon) {
        float[] hsv = dominantHsv(icon);
        if (hsv == null || hsv[1] < 0.2f) hsv = new float[]{222, 0.35f, 0};
        hsv[1] = Math.min(0.85f, hsv[1]);
        hsv[2] = 0.62f;
        int light = Color.HSVToColor(hsv);
        hsv[2] = 0.22f;
        return new int[]{light, Color.HSVToColor(hsv)};
    }

    /** Square icon centred on its colour wash, sized for a w x h tile (px). New instance each call. */
    static Drawable iconTile(Drawable icon, int[] wash, int w, int h, int iconSize, float radius) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, wash);
        bg.setCornerRadius(radius);
        Drawable glyph = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        LayerDrawable ld = new LayerDrawable(new Drawable[]{bg, glyph});
        int ix = (w - iconSize) / 2, iy = (h - iconSize) / 2;
        ld.setLayerInset(1, ix, iy, ix, iy);
        return ld;
    }

    /** A second, independent copy of a drawable (a Drawable can only live in one view). */
    static Drawable copy(Drawable d) {
        return d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
    }

    // Dominant colour (HSV), weighted towards vivid pixels; null if the drawable is empty.
    private static float[] dominantHsv(Drawable d) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        Drawable c = copy(d);
        c.setBounds(0, 0, 16, 9);
        c.draw(new Canvas(b));
        float[] hsv = new float[3];
        double r = 0, g = 0, bl = 0, wsum = 0;
        for (int y = 0; y < 9; y++) for (int x = 0; x < 16; x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            double w = hsv[1] * hsv[2] + 0.02;
            r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w; wsum += w;
        }
        b.recycle();
        if (wsum == 0) return null;
        Color.colorToHSV(Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum)), hsv);
        return hsv;
    }
}
