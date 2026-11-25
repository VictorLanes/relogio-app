package com.victorlanes.relogio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Personalização de um widget com prévia ao vivo (a prévia é o próprio widget renderizado). */
public class WidgetConfigActivity extends Activity {

    public static final String EXTRA_TIPO = "tipo";
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String tipo;
    private Dados.WCfg cfg;
    private FrameLayout previa;
    private Ui.Cores k;

    private static final int[] CORES_TEXTO = {0xFFFFFFFF, 0xFF7CF5C8, 0xFF67E8F9, 0xFF93C5FD, 0xFFC4B5FD, 0xFFF9A8D4, 0xFFFDBA74,
            0xFFFDE047, 0xFFF87171, 0xFFE2C275, 0xFF111111};
    private static final int[] CORES_FUNDO = {0xFF000000, 0xFFFFFFFF, 0xFF1F2430, 0xFF0F1E3D, 0xFF3B1F5C, 0xFF0F3D2E, 0xFF4A2E1A,
            0xFF7F1D1D, 0xFF334155};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Ui.tema = Dados.tema(this);
        k = Ui.cores(this);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        boolean naTela = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID;
        tipo = naTela ? Widgets.tipoDe(this, widgetId) : getIntent().getStringExtra(EXTRA_TIPO);
        if (tipo == null) tipo = "digital";
        Dados.WCfg salvo = naTela ? Dados.widget(this, widgetId) : null;
        cfg = salvo != null ? salvo : Widgets.padrao(tipo);
        String dial = getIntent().getStringExtra("dial");
        if (salvo == null && dial != null) cfg.dial = dial;
        montar(naTela);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void montar(boolean naTela) {
        LinearLayout raiz = Ui.coluna(this);
        raiz.setBackgroundColor(k.fundo);
        setContentView(raiz);
        Ui.aplicarInsets(raiz);

        LinearLayout topo = Ui.coluna(this);
        topo.setPadding(dp(20), dp(14), dp(20), dp(8));
        topo.addView(Ui.texto(this, "Widget " + Widgets.NOMES_TIPOS[Widgets.indiceTipo(tipo)].toLowerCase(), 24, k.texto, true));
        topo.addView(Ui.texto(this, "Personalize e veja como fica", 14, k.sub, false));
        raiz.addView(topo);

        // prévia sobre um "papel de parede"
        FrameLayout palco = new FrameLayout(this);
        palco.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4F46E5, 0xFF0EA5E9, 0xFF22C55E}));
        palco.setClipToOutline(true);
        GradientDrawable forma = new GradientDrawable();
        forma.setCornerRadius(dp(24));
        palco.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        ((GradientDrawable) palco.getBackground()).setCornerRadius(dp(24));
        LinearLayout.LayoutParams lp = Ui.lp(-1, dp(230));
        lp.setMargins(dp(16), dp(4), dp(16), dp(4));
        raiz.addView(palco, lp);
        previa = new FrameLayout(this);
        int[] t = Widgets.tamanhoCelulas(this, tipo);
        float esc = Math.min(1f, Math.min((getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density - 56) / t[0], 210f / t[1]));
        palco.addView(previa, new FrameLayout.LayoutParams(dp(t[0] * esc), dp(t[1] * esc), Gravity.CENTER));

        ScrollView sv = new ScrollView(this);
        LinearLayout op = Ui.coluna(this);
        op.setPadding(dp(16), 0, dp(16), dp(16));
        sv.addView(op);
        raiz.addView(sv, Ui.lp(-1, 0, 1f));

        boolean analog = "analogico".equals(tipo);
        if (analog) secaoDial(op);
        else secaoFonte(op);
        if (!analog) secaoCores(op, "Cor do texto", CORES_TEXTO, cfg.corTexto, c -> cfg.corTexto = c, false);
        secaoCores(op, "Fundo", CORES_FUNDO, cfg.corFundo, c -> {
            cfg.corFundo = c;
            if (cfg.alfaFundo == 0) cfg.alfaFundo = 160;
        }, true);

        LinearLayout opc = Ui.secao(this, op, "Opções", null);
        if (!analog && !"data".equals(tipo)) {
            opc.addView(new Ui.Chips(this, new String[]{"24", "12"}, new String[]{"24 horas", "12 horas (AM/PM)"}, cfg.h24 ? "24" : "12", id -> {
                cfg.h24 = "24".equals(id);
                render();
            }).view);
        }
        if (!analog && !"data".equals(tipo)) interruptor(opc, "Mostrar a data", cfg.data, v -> cfg.data = v);
        if ("digital".equals(tipo) || "completo".equals(tipo) || analog) interruptor(opc, analog ? "Ponteiro dos segundos" : "Mostrar os segundos", cfg.segundos, v -> cfg.segundos = v);
        if (!analog) {
            TextView rt = Ui.texto(this, "Tamanho do texto: " + cfg.tamanho + "%", 14, k.texto, false);
            rt.setPadding(0, dp(14), 0, 0);
            opc.addView(rt);
            SeekBar sb = new SeekBar(this);
            sb.setMax(90);
            sb.setProgress(cfg.tamanho - 60);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    cfg.tamanho = 60 + p;
                    rt.setText("Tamanho do texto: " + cfg.tamanho + "%");
                    render();
                }

                public void onStartTrackingTouch(SeekBar s) {
                }

                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            opc.addView(sb);
            LinearLayout fz = Ui.linha(this);
            fz.setPadding(0, dp(14), 0, dp(4));
            LinearLayout ft = Ui.coluna(this);
            ft.addView(Ui.texto(this, "Fuso horário", 15, k.texto, false));
            TextView fv = Ui.texto(this, cfg.fuso.isEmpty() ? "Horário do celular" : Widgets.Cidades.nome(cfg.fuso), 13, k.sub, false);
            ft.addView(fv);
            fz.addView(ft, Ui.lp(0, -2, 1f));
            TextView alt = Ui.texto(this, "Alterar", 15, k.destaque, true);
            alt.setPadding(dp(12), dp(8), dp(4), dp(8));
            alt.setOnClickListener(x -> escolherFuso(fv));
            fz.addView(alt);
            opc.addView(fz);
        }

        LinearLayout barra = Ui.linha(this);
        barra.setPadding(dp(16), dp(10), dp(16), dp(14));
        Button cancelar = Ui.botao(this, "Cancelar", false);
        cancelar.setOnClickListener(v -> finish());
        barra.addView(cancelar, Ui.lp(0, -2, 1f));
        Button salvar = Ui.botaoGradiente(this, naTela ? "Salvar" : "Adicionar à tela");
        salvar.setTextSize(16);
        salvar.setOnClickListener(v -> salvar(naTela));
        LinearLayout.LayoutParams ls = Ui.lp(0, dp(54), 1.5f);
        ls.leftMargin = dp(10);
        barra.addView(salvar, ls);
        raiz.addView(barra);
        render();
    }

    interface AoBool {
        void v(boolean b);
    }

    interface AoCor {
        void c(int cor);
    }

    private void interruptor(LinearLayout pai, String t, boolean on, AoBool cb) {
        Switch s = new Switch(this);
        s.setText(t);
        s.setTextSize(15);
        s.setTextColor(k.texto);
        s.setChecked(on);
        s.setPadding(0, dp(12), 0, dp(4));
        s.setOnCheckedChangeListener((x, v) -> {
            cb.v(v);
            render();
        });
        pai.addView(s);
    }

    private void secaoFonte(LinearLayout op) {
        LinearLayout s = Ui.secao(this, op, "Fonte", null);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(10), 0, dp(4));
        hs.addView(l);
        s.addView(hs);
        TextView nome = Ui.texto(this, "", 13, k.sub, false);
        s.addView(nome);
        TextView[] cs = new TextView[Widgets.FONTES.length];
        Runnable pintar = () -> {
            for (int i = 0; i < cs.length; i++) {
                boolean on = Widgets.FONTES[i].equals(cfg.fonte);
                cs[i].setBackground(Ui.clicavel(on ? k.chipSel : k.chip, dp(16), 0x22000000));
                if (on) nome.setText(Widgets.NOMES_FONTES[i]);
            }
        };
        for (int i = 0; i < Widgets.FONTES.length; i++) {
            final String f = Widgets.FONTES[i];
            TextView c = Ui.texto(this, "12:45", 24, k.texto, false);
            c.setTypeface(Fontes.typeface(this, f));
            c.setPadding(dp(16), dp(10), dp(16), dp(10));
            c.setOnClickListener(v -> {
                cfg.fonte = f;
                pintar.run();
                render();
            });
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.rightMargin = dp(8);
            l.addView(c, lp);
            cs[i] = c;
        }
        pintar.run();
    }

    private void secaoDial(LinearLayout op) {
        LinearLayout s = Ui.secao(this, op, "Mostrador", null);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(10), 0, dp(4));
        hs.addView(l);
        s.addView(hs);
        TextView nome = Ui.texto(this, "", 13, k.sub, false);
        s.addView(nome);
        ImageView[] cs = new ImageView[Widgets.DIAIS.length];
        Runnable pintar = () -> {
            for (int i = 0; i < cs.length; i++) {
                boolean on = Widgets.DIAIS[i].equals(cfg.dial);
                cs[i].setBackground(Ui.fundo(on ? k.chipSel : k.chip, dp(18)));
                if (on) nome.setText(Widgets.NOMES_DIAIS[i]);
            }
        };
        for (int i = 0; i < Widgets.DIAIS.length; i++) {
            final String d = Widgets.DIAIS[i];
            ImageView iv = new ImageView(this);
            iv.setImageResource(getResources().getIdentifier("mostrador_" + d, "drawable", getPackageName()));
            iv.setPadding(dp(8), dp(8), dp(8), dp(8));
            iv.setOnClickListener(v -> {
                cfg.dial = d;
                pintar.run();
                render();
            });
            LinearLayout.LayoutParams lp = Ui.lp(dp(78), dp(78));
            lp.rightMargin = dp(8);
            l.addView(iv, lp);
            cs[i] = iv;
        }
        pintar.run();
    }

    private void secaoCores(LinearLayout op, String titulo, int[] cores, int atual, AoCor cb, boolean fundo) {
        LinearLayout s = Ui.secao(this, op, titulo, null);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(10), 0, dp(4));
        hs.addView(l);
        s.addView(hs);
        List<View> vs = new java.util.ArrayList<>();
        int total = cores.length + (fundo ? 1 : 0);
        final int[] sel = {-1};
        for (int i = 0; i < cores.length; i++) if (cores[i] == atual) sel[0] = i + (fundo ? 1 : 0);
        if (fundo && cfg.alfaFundo == 0) sel[0] = 0;
        Runnable pintar = () -> {
            for (int i = 0; i < vs.size(); i++) {
                GradientDrawable g = (GradientDrawable) vs.get(i).getBackground();
                g.setStroke(dp(i == sel[0] ? 3 : 1), i == sel[0] ? k.destaque : k.linha);
            }
        };
        for (int i = 0; i < total; i++) {
            final int idx = i;
            View v;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (fundo && i == 0) {
                TextView x = Ui.texto(this, "∅", 18, k.texto, true);
                x.setGravity(Gravity.CENTER);
                g.setColor(k.chip);
                v = x;
            } else {
                v = new View(this);
                g.setColor(cores[i - (fundo ? 1 : 0)]);
            }
            v.setBackground(g);
            v.setOnClickListener(x -> {
                sel[0] = idx;
                if (fundo && idx == 0) cfg.alfaFundo = 0;
                else cb.c(cores[idx - (fundo ? 1 : 0)]);
                pintar.run();
                render();
            });
            LinearLayout.LayoutParams lp = Ui.lp(dp(40), dp(40));
            lp.rightMargin = dp(10);
            l.addView(v, lp);
            vs.add(v);
        }
        pintar.run();
        if (fundo) {
            TextView rt = Ui.texto(this, "Transparência do fundo", 14, k.texto, false);
            rt.setPadding(0, dp(12), 0, 0);
            s.addView(rt);
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(cfg.alfaFundo);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar x, int p, boolean u) {
                    if (!u) return;
                    cfg.alfaFundo = p;
                    render();
                }

                public void onStartTrackingTouch(SeekBar x) {
                }

                public void onStopTrackingTouch(SeekBar x) {
                }
            });
            s.addView(sb);
        }
    }

    private void escolherFuso(TextView alvo) {
        List<String[]> l = Widgets.Cidades.todas();
        String[] nomes = new String[l.size() + 1];
        nomes[0] = "Horário do celular";
        for (int i = 0; i < l.size(); i++) nomes[i + 1] = l.get(i)[1];
        new AlertDialog.Builder(this).setTitle("Fuso horário").setItems(nomes, (d, w) -> {
            cfg.fuso = w == 0 ? "" : l.get(w - 1)[0];
            alvo.setText(nomes[w]);
            render();
        }).show();
    }

    private void render() {
        int[] t = Widgets.tamanhoCelulas(this, tipo);
        RemoteViews rv = Widgets.montar(this, tipo, cfg, t[0], t[1]);
        previa.removeAllViews();
        try {
            View v = rv.apply(this, previa);
            // a prévia é menor que o widget real: escala para caber
            float esc = (float) previa.getLayoutParams().width / dp(t[0]);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(t[0]), dp(t[1]));
            v.setPivotX(0);
            v.setPivotY(0);
            v.setScaleX(esc);
            v.setScaleY(esc);
            previa.addView(v, lp);
        } catch (Exception e) {
            TextView x = Ui.texto(this, "Prévia indisponível", 14, 0xFFFFFFFF, false);
            previa.addView(x);
        }
    }

    private void salvar(boolean naTela) {
        if (naTela) {
            Dados.salvarWidget(this, widgetId, cfg);
            Widgets.atualizar(this, AppWidgetManager.getInstance(this), widgetId, tipo);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
            finish();
            return;
        }
        AppWidgetManager am = AppWidgetManager.getInstance(this);
        if (!am.isRequestPinAppWidgetSupported()) {
            Toast.makeText(this, "Segure a tela inicial → Widgets → Relógio para adicionar", Toast.LENGTH_LONG).show();
            return;
        }
        Dados.salvarPendente(this, cfg);
        Intent cb = new Intent(this, AlarmeReceiver.class).setAction(AlarmeReceiver.ACAO_WIDGET_FIXADO);
        PendingIntent pi = PendingIntent.getBroadcast(this, 77, cb, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        am.requestPinAppWidget(new ComponentName(this, Widgets.CLASSES[Widgets.indiceTipo(tipo)]), null, pi);
        Toast.makeText(this, "Confirme para colocar o widget na tela inicial", Toast.LENGTH_LONG).show();
        finish();
    }
}
