package com.victorlanes.relogio;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Widgets da tela inicial. As horas usam TextClock/AnalogClock, que o próprio Android atualiza
 * sozinho a cada minuto (ou segundo) — não gastam bateria com atualizações do app.
 */
public final class Widgets {

    private Widgets() {
    }

    public static final String[] FONTES = {"lcd", "orbitron", "bebas", "oswald", "quicksand", "playfair", "pacifico", "monoton",
            "sharetech", "righteous", "comfortaa", "majormono", "thin", "condensed"};
    public static final String[] NOMES_FONTES = {"LCD 7 segmentos", "Futurista", "Condensada alta", "Fina elegante", "Arredondada",
            "Clássica serifa", "Cursiva", "Neon retrô", "Terminal", "Moderna", "Suave", "Geométrica", "Fina do sistema", "Sistema condensada"};
    /** largura média de um dígito em relação ao tamanho da fonte (para calcular o tamanho que cabe) */
    private static final float[] LARGURA = {0.66f, 0.82f, 0.42f, 0.47f, 0.58f, 0.6f, 0.62f, 0.95f, 0.55f, 0.6f, 0.64f, 0.9f, 0.56f, 0.5f};

    public static final String[] DIAIS = {"classico", "minimal", "moderno", "esportivo", "transparente", "elegante"};
    public static final String[] NOMES_DIAIS = {"Clássico romano", "Minimalista", "Moderno neon", "Esportivo", "Transparente", "Elegante dourado"};

    // ------------------------------------------------------------------ provedores (um por tipo)

    public abstract static class Base extends AppWidgetProvider {
        abstract String tipo();

        @Override
        public void onUpdate(Context c, AppWidgetManager am, int[] ids) {
            for (int id : ids) atualizar(c, am, id, tipo());
        }

        @Override
        public void onAppWidgetOptionsChanged(Context c, AppWidgetManager am, int id, Bundle o) {
            atualizar(c, am, id, tipo());
        }

        @Override
        public void onDeleted(Context c, int[] ids) {
            for (int id : ids) Dados.removerWidget(c, id);
        }
    }

    public static class Digital extends Base { String tipo() { return "digital"; } }
    public static class Empilhado extends Base { String tipo() { return "empilhado"; } }
    public static class Flip extends Base { String tipo() { return "flip"; } }
    public static class Analogico extends Base { String tipo() { return "analogico"; } }
    public static class Data extends Base { String tipo() { return "data"; } }
    public static class Completo extends Base { String tipo() { return "completo"; } }

    public static final Class<?>[] CLASSES = {Digital.class, Empilhado.class, Flip.class, Analogico.class, Data.class, Completo.class};
    public static final String[] TIPOS = {"digital", "empilhado", "flip", "analogico", "data", "completo"};
    public static final String[] NOMES_TIPOS = {"Digital", "Digital empilhado", "Flip", "Analógico", "Data", "Relógio, data e alarme"};
    /** tamanho inicial em células {colunas, linhas} */
    public static final int[][] CELULAS = {{4, 2}, {2, 2}, {4, 2}, {2, 2}, {4, 1}, {4, 2}};

    public static String tipoDe(Context c, int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
        if (info == null) return "digital";
        for (int i = 0; i < CLASSES.length; i++) if (CLASSES[i].getName().equals(info.provider.getClassName())) return TIPOS[i];
        return "digital";
    }

    public static int indiceTipo(String tipo) {
        for (int i = 0; i < TIPOS.length; i++) if (TIPOS[i].equals(tipo)) return i;
        return 0;
    }

    /** configuração inicial de cada tipo (antes do usuário escolher) */
    public static Dados.WCfg padrao(String tipo) {
        Dados.WCfg w = new Dados.WCfg();
        switch (tipo) {
            case "flip":
                w.fonte = "bebas";
                w.corFundo = 0xFF1F2430;
                w.alfaFundo = 240;
                break;
            case "empilhado":
                w.fonte = "oswald";
                break;
            case "analogico":
                w.dial = "classico";
                break;
            case "data":
                w.fonte = "playfair";
                break;
            case "completo":
                w.fonte = "quicksand";
                w.corFundo = 0xFF000000;
                w.alfaFundo = 90;
                break;
            default:
                w.fonte = "lcd";
                w.corTexto = 0xFF7CF5C8;
                w.corFundo = 0xFF000000;
                w.alfaFundo = 110;
        }
        return w;
    }

    // ------------------------------------------------------------------ atualização

    public static void atualizarTodos(Context c) {
        AppWidgetManager am = AppWidgetManager.getInstance(c);
        for (int i = 0; i < CLASSES.length; i++) {
            for (int id : am.getAppWidgetIds(new ComponentName(c, CLASSES[i]))) atualizar(c, am, id, TIPOS[i]);
        }
    }

    public static void atualizar(Context c, AppWidgetManager am, int id, String tipo) {
        Dados.WCfg w = Dados.widget(c, id);
        if (w == null) w = padrao(tipo);
        int[] t = tamanhoDp(c, am, id, tipo);
        RemoteViews rv = montar(c, tipo, w, t[0], t[1]);
        Intent abrir = new Intent(c, MainActivity.class).putExtra("aba", "completo".equals(tipo) ? "alarme" : "relogio")
                .setData(Uri.parse("relogio://widget/" + id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        rv.setOnClickPendingIntent(R.id.raiz, PendingIntent.getActivity(c, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        try {
            am.updateAppWidget(id, rv);
        } catch (Exception ignored) {
        }
    }

    static int[] tamanhoDp(Context c, AppWidgetManager am, int id, String tipo) {
        Bundle o = am.getAppWidgetOptions(id);
        int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (w <= 0 || h <= 0) return tamanhoCelulas(c, tipo);
        return new int[]{w, h};
    }

    public static int[] tamanhoCelulas(Context c, String tipo) {
        int[] cel = CELULAS[indiceTipo(tipo)];
        float larg = (c.getResources().getConfiguration().screenWidthDp - 24) / 4f;
        return new int[]{Math.round(larg * cel[0] - 8), Math.round(larg * 1.1f * cel[1] - 8)};
    }

    private static int layout(Context c, String nome) {
        int id = c.getResources().getIdentifier(nome, "layout", c.getPackageName());
        return id != 0 ? id : R.layout.wd_digital_quicksand;
    }

    private static int indiceFonte(String f) {
        for (int i = 0; i < FONTES.length; i++) if (FONTES[i].equals(f)) return i;
        return 4;
    }

    private static int comAlfa(int cor, int alfa) {
        return (cor & 0x00FFFFFF) | (alfa << 24);
    }

    /** monta o widget (também usado nas prévias dentro do app) */
    public static RemoteViews montar(Context c, String tipo, Dados.WCfg w, int wDp, int hDp) {
        RemoteViews rv;
        if ("analogico".equals(tipo)) {
            rv = new RemoteViews(c.getPackageName(), layout(c, "wa_" + w.dial + "_" + (w.segundos ? "s" : "n")));
            fundo(rv, R.id.fundo, w.corFundo, w.alfaFundo);
            return rv;
        }
        rv = new RemoteViews(c.getPackageName(), layout(c, "wd_" + tipo + "_" + w.fonte));
        float lf = LARGURA[indiceFonte(w.fonte)];
        float esc = w.tamanho / 100f;
        int sec = comAlfa(w.corTexto, 0xCC);
        int[] textos = {R.id.hora, R.id.minuto, R.id.segundos, R.id.ampm};
        for (int id : textos) rv.setTextColor(id, w.corTexto);
        rv.setTextColor(R.id.data, sec);
        rv.setTextColor(R.id.extra, sec);
        rv.setViewVisibility(R.id.data, w.data ? View.VISIBLE : View.GONE);

        String extra = null;
        if (!w.fuso.isEmpty()) {
            for (int id : new int[]{R.id.hora, R.id.minuto, R.id.segundos, R.id.ampm, R.id.data}) rv.setString(id, "setTimeZone", w.fuso);
            extra = "🌍 " + Cidades.nome(w.fuso);
        }
        if ("completo".equals(tipo)) {
            AlarmManager.AlarmClockInfo prox = c.getSystemService(AlarmManager.class).getNextAlarmClock();
            extra = prox == null ? "⏰ Nenhum alarme" : "⏰ " + Agendador.diaSemanaCurto(prox.getTriggerTime()) + " " + Agendador.horaTexto(c, prox.getTriggerTime());
        }
        rv.setViewVisibility(R.id.extra, extra != null ? View.VISIBLE : View.GONE);
        if (extra != null) rv.setTextViewText(R.id.extra, extra);
        int linhasExtras = (w.data ? 1 : 0) + (extra != null ? 1 : 0);

        switch (tipo) {
            case "empilhado": {
                String fh = w.h24 ? "HH" : "hh";
                rv.setCharSequence(R.id.hora, "setFormat12Hour", fh);
                rv.setCharSequence(R.id.hora, "setFormat24Hour", fh);
                float tam = Math.min((wDp - 20) / (2 * lf), (hDp - 16 - linhasExtras * 18) / 2f * 0.92f) * esc;
                tam(rv, R.id.hora, tam);
                tam(rv, R.id.minuto, tam);
                tam(rv, R.id.data, clamp(tam * 0.2f, 11, 18));
                tam(rv, R.id.extra, clamp(tam * 0.18f, 10, 15));
                rv.setViewVisibility(R.id.ampm, View.GONE);
                fundo(rv, R.id.fundo, w.corFundo, w.alfaFundo);
                break;
            }
            case "flip": {
                String fh = w.h24 ? "HH" : "hh";
                rv.setCharSequence(R.id.hora, "setFormat12Hour", fh);
                rv.setCharSequence(R.id.hora, "setFormat24Hour", fh);
                float card = (wDp - 36) / 2f;
                float tam = Math.min(card / (2 * lf) * 0.82f, (hDp - 24 - linhasExtras * 20) * 0.72f) * esc;
                tam(rv, R.id.hora, tam);
                tam(rv, R.id.minuto, tam);
                tam(rv, R.id.data, clamp(tam * 0.2f, 11, 16));
                tam(rv, R.id.ampm, clamp(tam * 0.2f, 11, 16));
                tam(rv, R.id.extra, clamp(tam * 0.18f, 10, 14));
                rv.setViewVisibility(R.id.ampm, w.h24 ? View.GONE : View.VISIBLE);
                rv.setViewVisibility(R.id.fundo, View.GONE);
                int alfaCard = w.alfaFundo == 0 ? 235 : w.alfaFundo;
                fundo(rv, R.id.card1, w.corFundo, alfaCard);
                fundo(rv, R.id.card2, w.corFundo, alfaCard);
                break;
            }
            case "data": {
                float tam = Math.min((wDp - 20) / (8.5f * lf), (hDp - 12) * 0.42f) * esc;
                tam(rv, R.id.hora, tam);
                tam(rv, R.id.data, clamp(tam * 0.5f, 11, 22));
                tam(rv, R.id.extra, clamp(tam * 0.42f, 10, 16));
                rv.setViewVisibility(R.id.data, View.VISIBLE);
                fundo(rv, R.id.fundo, w.corFundo, w.alfaFundo);
                break;
            }
            default: { // digital e completo
                String fh = w.h24 ? "HH:mm" : "h:mm";
                rv.setCharSequence(R.id.hora, "setFormat12Hour", fh);
                rv.setCharSequence(R.id.hora, "setFormat24Hour", fh);
                rv.setCharSequence(R.id.ampm, "setFormat12Hour", w.h24 ? "" : "a");
                rv.setCharSequence(R.id.ampm, "setFormat24Hour", w.h24 ? "" : "a");
                rv.setViewVisibility(R.id.ampm, w.h24 ? View.GONE : View.VISIBLE);
                rv.setViewVisibility(R.id.segundos, w.segundos ? View.VISIBLE : View.GONE);
                float chars = 4.4f + (w.segundos ? 1.0f : 0) + (w.h24 ? 0 : 0.9f);
                float alturaUtil = (hDp - 20) * (linhasExtras == 0 ? 0.85f : linhasExtras == 1 ? 0.62f : 0.5f);
                float tam = Math.min((wDp - 28) / (chars * lf), alturaUtil) * esc;
                tam(rv, R.id.hora, tam);
                tam(rv, R.id.segundos, tam * 0.4f);
                tam(rv, R.id.ampm, clamp(tam * 0.28f, 10, 22));
                tam(rv, R.id.data, clamp(tam * 0.26f, 11, 22));
                tam(rv, R.id.extra, clamp(tam * 0.22f, 10, 18));
                fundo(rv, R.id.fundo, w.corFundo, w.alfaFundo);
            }
        }
        return rv;
    }

    private static void tam(RemoteViews rv, int id, float dp) {
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, Math.max(8, dp));
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    private static void fundo(RemoteViews rv, int id, int cor, int alfa) {
        if (alfa <= 0) {
            rv.setViewVisibility(id, View.GONE);
            return;
        }
        rv.setViewVisibility(id, View.VISIBLE);
        rv.setInt(id, "setColorFilter", cor | 0xFF000000);
        rv.setInt(id, "setImageAlpha", alfa);
    }

    // ------------------------------------------------------------------ fusos (cidades)

    public static final class Cidades {
        public static final String[][] LISTA = {
                {"America/Sao_Paulo", "São Paulo / Rio"}, {"America/Manaus", "Manaus"}, {"America/Noronha", "Fernando de Noronha"},
                {"America/Rio_Branco", "Rio Branco"}, {"America/Fortaleza", "Fortaleza"}, {"America/Cuiaba", "Cuiabá"},
                {"America/Buenos_Aires", "Buenos Aires"}, {"America/Santiago", "Santiago"}, {"America/Bogota", "Bogotá"},
                {"America/Lima", "Lima"}, {"America/Mexico_City", "Cidade do México"}, {"America/New_York", "Nova York"},
                {"America/Chicago", "Chicago"}, {"America/Los_Angeles", "Los Angeles"}, {"America/Toronto", "Toronto"},
                {"Europe/Lisbon", "Lisboa"}, {"Europe/London", "Londres"}, {"Europe/Madrid", "Madri"}, {"Europe/Paris", "Paris"},
                {"Europe/Rome", "Roma"}, {"Europe/Berlin", "Berlim"}, {"Europe/Amsterdam", "Amsterdã"}, {"Europe/Moscow", "Moscou"},
                {"Africa/Luanda", "Luanda"}, {"Africa/Maputo", "Maputo"}, {"Africa/Johannesburg", "Joanesburgo"}, {"Africa/Cairo", "Cairo"},
                {"Asia/Dubai", "Dubai"}, {"Asia/Kolkata", "Nova Délhi"}, {"Asia/Shanghai", "Pequim / Xangai"}, {"Asia/Hong_Kong", "Hong Kong"},
                {"Asia/Tokyo", "Tóquio"}, {"Asia/Seoul", "Seul"}, {"Asia/Singapore", "Singapura"}, {"Australia/Sydney", "Sydney"},
                {"Pacific/Auckland", "Auckland"}, {"Pacific/Honolulu", "Honolulu"}, {"Atlantic/Azores", "Açores"}, {"UTC", "UTC"}};

        public static String nome(String tz) {
            for (String[] c : LISTA) if (c[0].equals(tz)) return c[1];
            String n = tz.contains("/") ? tz.substring(tz.lastIndexOf('/') + 1) : tz;
            return n.replace('_', ' ');
        }

        public static List<String[]> todas() {
            List<String[]> l = new ArrayList<>();
            for (String[] c : LISTA) if (TimeZone.getTimeZone(c[0]) != null) l.add(c);
            return l;
        }
    }
}
