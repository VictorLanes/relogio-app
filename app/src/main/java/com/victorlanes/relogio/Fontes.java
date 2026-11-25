package com.victorlanes.relogio;

import android.content.Context;
import android.graphics.Typeface;

/** Typeface de cada fonte dos widgets (para usar dentro do app). */
public final class Fontes {
    private Fontes() {
    }

    public static Typeface typeface(Context c, String nome) {
        if ("thin".equals(nome)) return Typeface.create("sans-serif-thin", Typeface.NORMAL);
        if ("condensed".equals(nome)) return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        int id = c.getResources().getIdentifier(nome, "font", c.getPackageName());
        try {
            if (id != 0) return c.getResources().getFont(id);
        } catch (Exception ignored) {
        }
        return Typeface.DEFAULT;
    }
}
