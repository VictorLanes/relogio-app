package com.victorlanes.relogio;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Tela cheia do alarme tocando (aparece por cima da tela de bloqueio). */
public class TocandoActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable vigia = new Runnable() {
        @Override
        public void run() {
            if (!TocarService.tocando) {
                finish();
                return;
            }
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);

        boolean alarme = "alarme".equals(TocarService.tipoAtual);
        LinearLayout col = Ui.coluna(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(28), dp(60), dp(28), dp(48));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2B2A6B, 0xFF131433, 0xFF07080F}));
        setContentView(col);
        Ui.aplicarInsets(col);

        TextView ic = Ui.texto(this, alarme ? "⏰" : "⌛", 54, 0xFFFFFFFF, false);
        col.addView(ic);
        TextClock relogio = new TextClock(this);
        relogio.setFormat24Hour("HH:mm");
        relogio.setFormat12Hour("h:mm");
        relogio.setTextSize(92);
        relogio.setTextColor(0xFFFFFFFF);
        relogio.setTypeface(getResources().getFont(R.font.quicksand));
        relogio.setPadding(0, dp(18), 0, 0);
        col.addView(relogio);
        TextView t = Ui.texto(this, alarme ? TocarService.subtitulo : TocarService.titulo, 26, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, 0);
        col.addView(t);
        TextView s = Ui.texto(this, alarme ? "" : TocarService.subtitulo, 17, 0xBBFFFFFF, false);
        s.setGravity(Gravity.CENTER);
        col.addView(s);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        int soneca = 10;
        Dados.Alarme al = alarme ? Dados.alarme(this, TocarService.idAtual) : null;
        if (al != null) soneca = al.soneca;
        Button b1 = Ui.botao(this, alarme ? "😴  Soneca de " + soneca + " min" : "+1 minuto", false);
        b1.setTextSize(19);
        b1.setOnClickListener(v -> {
            TocarService.comando(this, alarme ? TocarService.ACAO_SONECA : TocarService.ACAO_MAIS1);
            finish();
        });
        col.addView(b1, new LinearLayout.LayoutParams(-1, dp(64)));
        Button b2 = Ui.botaoGradiente(this, "Parar");
        b2.setTextSize(21);
        b2.setTypeface(Typeface.DEFAULT_BOLD);
        b2.setOnClickListener(v -> {
            TocarService.comando(this, TocarService.ACAO_PARAR);
            finish();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(72));
        lp.topMargin = dp(14);
        col.addView(b2, lp);
        h.postDelayed(vigia, 800);
    }

    @Override
    public void onBackPressed() {
        // o botão voltar não para o alarme por engano
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(vigia);
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
