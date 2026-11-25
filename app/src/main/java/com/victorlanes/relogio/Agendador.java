package com.victorlanes.relogio;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Agenda alarmes (setAlarmClock: exato, aparece o ícone de alarme) e temporizadores. */
public final class Agendador {

    public static final String ACAO_ALARME = "com.victorlanes.relogio.ALARME";
    public static final String ACAO_TIMER = "com.victorlanes.relogio.TIMER";
    static final String CANAL_TIMERS = "timers";

    private Agendador() {
    }

    private static PendingIntent pi(Context c, String acao, int id) {
        Intent i = new Intent(c, AlarmeReceiver.class).setAction(acao).setData(Uri.parse("relogio://" + acao + "/" + id)).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent abrirApp(Context c, String aba) {
        Intent i = new Intent(c, MainActivity.class).putExtra("aba", aba).setData(Uri.parse("relogio://abrir/" + aba))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, aba.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void agendarAlarmes(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long agora = System.currentTimeMillis();
        for (Dados.Alarme a : Dados.alarmes(c)) {
            PendingIntent op = pi(c, ACAO_ALARME, a.id);
            am.cancel(op);
            if (!a.ativo) continue;
            long t = a.proximo(agora);
            try {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, abrirApp(c, "alarme")), op);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
            }
        }
        Widgets.atualizarTodos(c);
    }

    public static void agendarTimers(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long agora = System.currentTimeMillis();
        for (Dados.Timer t : Dados.timers(c)) {
            PendingIntent op = pi(c, ACAO_TIMER, t.id);
            am.cancel(op);
            if (!t.rodando() || t.tocando) continue;
            exato(am, Math.max(agora + 500, t.fimEm), op);
        }
        notificarTimers(c);
    }

    public static void cancelarTimer(Context c, int id) {
        c.getSystemService(AlarmManager.class).cancel(pi(c, ACAO_TIMER, id));
        c.getSystemService(NotificationManager.class).cancel(5000 + id);
    }

    static void exato(AlarmManager am, long t, PendingIntent op) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
        }
    }

    /** notificação com contagem regressiva para cada temporizador rodando */
    static void notificarTimers(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CANAL_TIMERS) == null) {
            NotificationChannel ch = new NotificationChannel(CANAL_TIMERS, "Temporizadores em andamento", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        for (Dados.Timer t : Dados.timers(c)) {
            int nid = 5000 + t.id;
            if (!t.rodando() || t.tocando) {
                nm.cancel(nid);
                continue;
            }
            Notification n = new Notification.Builder(c, CANAL_TIMERS)
                    .setSmallIcon(R.drawable.ic_temporizador)
                    .setContentTitle(t.rotulo.isEmpty() ? "Temporizador · " + duracaoTexto(t.duracao) : t.rotulo)
                    .setContentText("Termina às " + horaTexto(c, t.fimEm))
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(t.fimEm)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(abrirApp(c, "temporizador"))
                    .build();
            nm.notify(nid, n);
        }
    }

    public static String duracaoTexto(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600, m = (s / 60) % 60, ss = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, ss);
        return String.format(Locale.US, "%02d:%02d", m, ss);
    }

    public static String horaTexto(Context c, long t) {
        java.text.DateFormat f = android.text.format.DateFormat.getTimeFormat(c);
        return f.format(new java.util.Date(t));
    }

    /** "em 7 h 32 min" */
    public static String faltaTexto(long ms) {
        long min = Math.max(1, (ms + 59_999) / 60_000);
        long d = min / 1440, h = (min / 60) % 24, m = min % 60;
        StringBuilder sb = new StringBuilder("em ");
        if (d > 0) sb.append(d).append(d == 1 ? " dia " : " dias ");
        if (h > 0) sb.append(h).append(" h ");
        if (m > 0 && d == 0) sb.append(m).append(" min");
        return sb.toString().trim();
    }

    /** próximo alarme do app (null se nenhum) */
    public static Dados.Alarme proximoAlarme(Context c) {
        long agora = System.currentTimeMillis();
        Dados.Alarme melhor = null;
        long mt = Long.MAX_VALUE;
        List<Dados.Alarme> l = Dados.alarmes(c);
        for (Dados.Alarme a : l) {
            if (!a.ativo) continue;
            long t = a.proximo(agora);
            if (t < mt) {
                mt = t;
                melhor = a;
            }
        }
        return melhor;
    }

    static String diaSemanaCurto(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        return new String[]{"dom", "seg", "ter", "qua", "qui", "sex", "sáb"}[k.get(Calendar.DAY_OF_WEEK) - 1];
    }
}
