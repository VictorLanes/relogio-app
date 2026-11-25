package com.victorlanes.relogio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Toca o alarme/temporizador (som em loop, vibração, tela cheia) até o usuário parar ou adiar. */
public class TocarService extends Service {

    public static final String ACAO_TOCAR = "tocar", ACAO_PARAR = "parar", ACAO_SONECA = "soneca", ACAO_MAIS1 = "mais1";
    private static final String CANAL = "tocando";

    public static volatile boolean tocando;
    public static volatile String tipoAtual = "";
    public static volatile int idAtual;
    public static volatile String titulo = "", subtitulo = "";

    private MediaPlayer mp;
    private Vibrator vib;
    private PowerManager.WakeLock wake;
    private final Handler h = new Handler(Looper.getMainLooper());
    private float volume;
    private final Runnable aumentar = new Runnable() {
        @Override
        public void run() {
            if (mp == null) return;
            volume = Math.min(1f, volume + 0.05f);
            try {
                mp.setVolume(volume, volume);
            } catch (Exception ignored) {
            }
            if (volume < 1f) h.postDelayed(this, 1500);
        }
    };
    private final Runnable limite = () -> {
        // ninguém parou em 10 minutos: alarme entra em soneca, temporizador para
        if ("alarme".equals(tipoAtual)) soneca();
        else parar();
    };

    public static void tocar(Context c, String tipo, int id) {
        Intent i = new Intent(c, TocarService.class).setAction(ACAO_TOCAR).putExtra("tipo", tipo).putExtra("id", id);
        c.startForegroundService(i);
    }

    public static void comando(Context c, String acao) {
        c.startService(new Intent(c, TocarService.class).setAction(acao));
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String a = i != null && i.getAction() != null ? i.getAction() : ACAO_PARAR;
        switch (a) {
            case ACAO_TOCAR:
                iniciar(i.getStringExtra("tipo"), i.getIntExtra("id", 0));
                break;
            case ACAO_SONECA:
                soneca();
                break;
            case ACAO_MAIS1:
                maisUmMinuto();
                break;
            default:
                parar();
        }
        return START_NOT_STICKY;
    }

    private void iniciar(String tipo, int id) {
        pararSom();
        tipoAtual = tipo;
        idAtual = id;
        String som = "";
        boolean vibrar = true, gradual = false;
        if ("alarme".equals(tipo)) {
            Dados.Alarme al = Dados.alarme(this, id);
            titulo = Agendador.horaTexto(this, System.currentTimeMillis());
            subtitulo = al != null && !al.rotulo.isEmpty() ? al.rotulo : "Alarme";
            if (al != null) {
                som = al.som;
                vibrar = al.vibrar;
                gradual = al.gradual;
            }
        } else {
            Dados.Timer t = Dados.timer(this, id);
            titulo = "Tempo esgotado!";
            subtitulo = t != null ? (t.rotulo.isEmpty() ? "Temporizador de " + Agendador.duracaoTexto(t.duracao) : t.rotulo) : "Temporizador";
        }
        entrarPrimeiroPlano();
        tocando = true;
        PowerManager pm = getSystemService(PowerManager.class);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Relogio:tocando");
        wake.acquire(11 * 60_000L);
        tocarSom(som, gradual);
        if (vibrar) vibrar();
        h.postDelayed(limite, 10 * 60_000L);
        // tenta abrir a tela de alarme por cima de tudo (funciona com a tela bloqueada)
        try {
            startActivity(new Intent(this, TocandoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
        } catch (Exception ignored) {
        }
    }

    private void entrarPrimeiroPlano() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CANAL) == null) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Alarme tocando", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        PendingIntent tela = PendingIntent.getActivity(this, 1, new Intent(this, TocandoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean alarme = "alarme".equals(tipoAtual);
        PendingIntent p1 = PendingIntent.getService(this, 2, new Intent(this, TocarService.class).setAction(alarme ? ACAO_SONECA : ACAO_MAIS1), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent p2 = PendingIntent.getService(this, 3, new Intent(this, TocarService.class).setAction(ACAO_PARAR), PendingIntent.FLAG_IMMUTABLE);
        int soneca = 10;
        if (alarme) {
            Dados.Alarme al = Dados.alarme(this, idAtual);
            if (al != null) soneca = al.soneca;
        }
        Notification n = new Notification.Builder(this, CANAL)
                .setSmallIcon(alarme ? R.drawable.ic_alarme : R.drawable.ic_temporizador)
                .setContentTitle(alarme ? subtitulo + " · " + titulo : titulo)
                .setContentText(alarme ? "Toque para abrir" : subtitulo)
                .setCategory(alarme ? Notification.CATEGORY_ALARM : Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(tela, true)
                .setContentIntent(tela)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, alarme ? "Soneca " + soneca + " min" : "+1 min", p1).build())
                .addAction(new Notification.Action.Builder(null, "Parar", p2).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(9, n);
    }

    private void tocarSom(String som, boolean gradual) {
        Uri u = null;
        if (som != null && !som.isEmpty() && !"silencio".equals(som)) u = Uri.parse(som);
        if ("silencio".equals(som)) return;
        if (u == null) u = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(this, u);
            mp.setLooping(true);
            mp.prepare();
            volume = gradual ? 0.08f : 1f;
            mp.setVolume(volume, volume);
            mp.start();
            if (gradual) h.postDelayed(aumentar, 1500);
            // garante que o volume de alarme não está zerado
            AudioManager am = getSystemService(AudioManager.class);
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2), 0);
            }
        } catch (Exception e) {
            try {
                if (mp != null) mp.release();
                mp = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                if (mp != null) {
                    mp.setLooping(true);
                    mp.start();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void vibrar() {
        vib = Build.VERSION.SDK_INT >= 31 ? getSystemService(VibratorManager.class).getDefaultVibrator() : getSystemService(Vibrator.class);
        if (vib == null || !vib.hasVibrator()) return;
        long[] padrao = {0, 700, 600, 700, 1200};
        vib.vibrate(VibrationEffect.createWaveform(padrao, 0),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
    }

    private void pararSom() {
        h.removeCallbacks(aumentar);
        h.removeCallbacks(limite);
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception ignored) {
            }
            mp.release();
            mp = null;
        }
        if (vib != null) vib.cancel();
        if (wake != null && wake.isHeld()) wake.release();
    }

    private void soneca() {
        if ("alarme".equals(tipoAtual)) {
            Dados.Alarme al = Dados.alarme(this, idAtual);
            if (al != null) {
                al.sonecaAte = System.currentTimeMillis() + al.soneca * 60_000L;
                al.ativo = true;
                Dados.salvarAlarme(this, al);
                Agendador.agendarAlarmes(this);
            }
        }
        finalizar();
    }

    private void maisUmMinuto() {
        Dados.Timer t = Dados.timer(this, idAtual);
        if (t != null) {
            t.tocando = false;
            t.fimEm = System.currentTimeMillis() + 60_000L;
            Dados.salvarTimer(this, t);
            Agendador.agendarTimers(this);
        }
        finalizar();
    }

    private void parar() {
        if ("timer".equals(tipoAtual)) {
            Dados.Timer t = Dados.timer(this, idAtual);
            if (t != null) { // volta ao tempo inicial, pronto para usar de novo
                t.tocando = false;
                t.fimEm = 0;
                t.restante = t.duracao;
                Dados.salvarTimer(this, t);
                Agendador.agendarTimers(this);
            }
        }
        finalizar();
    }

    private void finalizar() {
        pararSom();
        tocando = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        pararSom();
        tocando = false;
        super.onDestroy();
    }
}
