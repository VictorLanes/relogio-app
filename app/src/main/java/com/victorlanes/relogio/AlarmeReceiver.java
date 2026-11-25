package com.victorlanes.relogio;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Disparo de alarme e de temporizador, reinício do celular, mudança de hora e widget fixado pelo app. */
public class AlarmeReceiver extends BroadcastReceiver {

    public static final String ACAO_WIDGET_FIXADO = "com.victorlanes.relogio.WIDGET_FIXADO";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        int id = i.getIntExtra("id", 0);
        switch (a) {
            case Agendador.ACAO_ALARME: {
                Dados.Alarme al = Dados.alarme(c, id);
                if (al == null || !al.ativo) return;
                al.sonecaAte = 0;
                if (al.dias == 0) al.ativo = false; // alarme de uma vez só
                Dados.salvarAlarme(c, al);
                Agendador.agendarAlarmes(c);
                TocarService.tocar(c, "alarme", id);
                break;
            }
            case Agendador.ACAO_TIMER: {
                Dados.Timer t = Dados.timer(c, id);
                if (t == null || !t.rodando()) return;
                t.tocando = true;
                t.fimEm = 0;
                t.restante = 0;
                Dados.salvarTimer(c, t);
                Agendador.agendarTimers(c);
                TocarService.tocar(c, "timer", id);
                break;
            }
            case ACAO_WIDGET_FIXADO: {
                int wid = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
                Dados.WCfg w = Dados.pegarPendente(c);
                if (wid != AppWidgetManager.INVALID_APPWIDGET_ID && w != null) Dados.salvarWidget(c, wid, w);
                Widgets.atualizarTodos(c);
                break;
            }
            case AlarmManagerProximo.ACAO:
                Widgets.atualizarTodos(c);
                break;
            default: // boot, troca de hora/fuso, app atualizado
                long agora = System.currentTimeMillis();
                for (Dados.Timer t : Dados.timers(c)) {
                    if (t.rodando() && t.fimEm <= agora && !t.tocando) {
                        t.tocando = true;
                        t.fimEm = 0;
                        Dados.salvarTimer(c, t);
                        TocarService.tocar(c, "timer", t.id);
                    }
                }
                Agendador.agendarAlarmes(c);
                Agendador.agendarTimers(c);
        }
    }

    /** nome da ação de "próximo alarme mudou" (de qualquer app de alarme) */
    static final class AlarmManagerProximo {
        static final String ACAO = android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED;
    }
}
