package com.victorlanes.relogio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {

    private static final String[] ABAS = {"alarme", "relogio", "cronometro", "temporizador", "widgets"};
    private static final String[] NOMES = {"Alarme", "Relógio", "Cronômetro", "Timer", "Widgets"};
    private static final int[] ICONES = {R.drawable.ic_alarme, R.drawable.ic_relogio, R.drawable.ic_cronometro, R.drawable.ic_temporizador, R.drawable.ic_widgets};
    private static final int REQ_SOM = 11;

    private Ui.Cores k;
    private FrameLayout conteudo;
    private LinearLayout nav;
    private String aba = "alarme";
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable tick;

    // estado da edição de alarme (para o seletor de toque)
    private Dados.Alarme editando;
    private TextView somEditando;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.tema = Dados.tema(this);
        k = Ui.cores(this);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        if (b != null) aba = b.getString("aba", aba);
        lerAba(getIntent());

        LinearLayout raiz = Ui.coluna(this);
        raiz.setBackgroundColor(k.fundo);
        setContentView(raiz);
        Ui.aplicarInsets(raiz);
        conteudo = new FrameLayout(this);
        raiz.addView(conteudo, Ui.lp(-1, 0, 1f));
        nav = Ui.linha(this);
        nav.setPadding(dp(8), dp(6), dp(8), dp(10));
        nav.setBackgroundColor(k.superficie);
        raiz.addView(nav, Ui.lp(-1, -2));
        montarNav();

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Agendador.agendarAlarmes(this);
        Agendador.agendarTimers(this);
    }

    private void lerAba(Intent i) {
        String a = i != null ? i.getStringExtra("aba") : null;
        if (a != null) for (String x : ABAS) if (x.equals(a)) aba = a;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        lerAba(i);
        montarNav();
        mostrar();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putString("aba", aba);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mostrar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pararTick();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // ================================================================== navegação

    private void montarNav() {
        nav.removeAllViews();
        for (int i = 0; i < ABAS.length; i++) {
            final String id = ABAS[i];
            boolean on = id.equals(aba);
            LinearLayout it = Ui.coluna(this);
            it.setGravity(Gravity.CENTER);
            it.setPadding(0, dp(4), 0, dp(2));
            FrameLayout pilula = new FrameLayout(this);
            if (on) pilula.setBackground(Ui.fundo(k.chipSel, dp(16)));
            ImageView ic = new ImageView(this);
            ic.setImageResource(ICONES[i]);
            ic.setColorFilter(on ? k.destaque : k.sub);
            pilula.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            it.addView(pilula, Ui.lp(dp(60), dp(32)));
            TextView t = Ui.texto(this, NOMES[i], 12, on ? k.texto : k.sub, on);
            t.setPadding(0, dp(4), 0, 0);
            it.addView(t);
            it.setOnClickListener(v -> {
                aba = id;
                montarNav();
                mostrar();
            });
            nav.addView(it, Ui.lp(0, -2, 1f));
        }
    }

    private void mostrar() {
        pararTick();
        conteudo.removeAllViews();
        switch (aba) {
            case "relogio":
                abaRelogio();
                break;
            case "cronometro":
                abaCronometro();
                break;
            case "temporizador":
                abaTemporizador();
                break;
            case "widgets":
                abaWidgets();
                break;
            default:
                abaAlarme();
        }
    }

    private void iniciarTick(Runnable r, long ms) {
        pararTick();
        tick = new Runnable() {
            @Override
            public void run() {
                r.run();
                h.postDelayed(this, ms);
            }
        };
        h.post(tick);
    }

    private void pararTick() {
        if (tick != null) h.removeCallbacks(tick);
        tick = null;
    }

    /** página rolável com título grande */
    private LinearLayout pagina(String titulo, String sub) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = Ui.coluna(this);
        col.setPadding(dp(16), dp(18), dp(16), dp(96));
        sv.addView(col);
        conteudo.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        TextView t = Ui.texto(this, titulo, 30, k.texto, true);
        t.setPadding(dp(4), 0, 0, 0);
        col.addView(t);
        if (sub != null) {
            TextView s = Ui.texto(this, sub, 14, k.sub, false);
            s.setPadding(dp(4), dp(2), 0, dp(4));
            col.addView(s);
        }
        return col;
    }

    private void botaoFlutuante(View.OnClickListener cb) {
        ImageView fab = new ImageView(this);
        fab.setImageResource(R.drawable.ic_mais);
        fab.setColorFilter(0xFFFFFFFF);
        fab.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Ui.GRADIENTE);
        g.setCornerRadius(dp(20));
        fab.setBackground(g);
        fab.setElevation(dp(6));
        fab.setOnClickListener(cb);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, dp(20), dp(20));
        conteudo.addView(fab, lp);
    }

    private TextView vazio(String s) {
        TextView t = Ui.texto(this, s, 15, k.sub, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(60), dp(20), dp(20));
        return t;
    }

    // ================================================================== ALARME

    private void abaAlarme() {
        Dados.Alarme prox = Agendador.proximoAlarme(this);
        String sub = prox == null ? "Nenhum alarme ativo" : "Próximo alarme " + Agendador.faltaTexto(prox.proximo(System.currentTimeMillis()) - System.currentTimeMillis());
        LinearLayout col = pagina("Alarmes", sub);
        avisoPermissoes(col);
        List<Dados.Alarme> l = Dados.alarmes(this);
        l.sort((a, b) -> (a.hora * 60 + a.minuto) - (b.hora * 60 + b.minuto));
        if (l.isEmpty()) col.addView(vazio("Toque em + para criar seu primeiro alarme"));
        for (Dados.Alarme a : l) col.addView(cartaoAlarme(a));
        botaoFlutuante(v -> editarAlarme(null));
    }

    private void avisoPermissoes(LinearLayout col) {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (!nm.canUseFullScreenIntent()) {
                aviso(col, "Permita que o alarme apareça em tela cheia (inclusive com a tela bloqueada).", () -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        abrirAjustesApp();
                    }
                });
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            aviso(col, "Ative as notificações para ver o alarme tocando e os temporizadores.", this::abrirAjustesApp);
        }
    }

    private void abrirAjustesApp() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private void aviso(LinearLayout col, String msg, Runnable acao) {
        LinearLayout c = Ui.linha(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Ui.clicavel(0xFF3B2A12, dp(18), 0x33FFFFFF));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView t = Ui.texto(this, "⚠️  " + msg, 14, 0xFFFDE68A, false);
        c.addView(t, Ui.lp(0, -2, 1f));
        TextView b = Ui.texto(this, "Permitir", 14, 0xFFFDE68A, true);
        b.setPadding(dp(10), 0, 0, 0);
        c.addView(b);
        c.setOnClickListener(v -> acao.run());
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        col.addView(c, lp);
    }

    private String hhmm(int hh, int mm) {
        if (DateFormat.is24HourFormat(this)) return String.format(Locale.US, "%02d:%02d", hh, mm);
        int h12 = hh % 12 == 0 ? 12 : hh % 12;
        return String.format(Locale.US, "%d:%02d", h12, mm);
    }

    private View cartaoAlarme(Dados.Alarme a) {
        LinearLayout c = Ui.linha(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Ui.clicavel(k.superficie, dp(24), 0x22FFFFFF));
        c.setPadding(dp(20), dp(16), dp(14), dp(16));
        LinearLayout esq = Ui.coluna(this);
        LinearLayout lh = Ui.linha(this);
        lh.setGravity(Gravity.BOTTOM);
        TextView hora = Ui.texto(this, hhmm(a.hora, a.minuto), 44, a.ativo ? k.texto : k.sub, false);
        hora.setTypeface(Fontes.typeface(this, "quicksand"));
        hora.setIncludeFontPadding(false);
        lh.addView(hora);
        if (!DateFormat.is24HourFormat(this)) {
            TextView ap = Ui.texto(this, a.hora < 12 ? " AM" : " PM", 16, k.sub, false);
            ap.setPadding(0, 0, 0, dp(6));
            lh.addView(ap);
        }
        esq.addView(lh);
        String linha2 = (a.rotulo.isEmpty() ? "" : a.rotulo + " · ") + a.textoDias();
        if (a.sonecaAte > System.currentTimeMillis()) linha2 = "😴 Soneca até " + Agendador.horaTexto(this, a.sonecaAte);
        esq.addView(Ui.texto(this, linha2, 14, a.ativo ? k.sub : 0x88A3A3AD, false));
        c.addView(esq, Ui.lp(0, -2, 1f));
        Switch s = new Switch(this);
        s.setChecked(a.ativo);
        s.setOnCheckedChangeListener((v, on) -> {
            a.ativo = on;
            a.sonecaAte = 0;
            Dados.salvarAlarme(this, a);
            Agendador.agendarAlarmes(this);
            if (on) avisarQuando(a);
            mostrar();
        });
        c.addView(s);
        c.setOnClickListener(v -> editarAlarme(a));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private void avisarQuando(Dados.Alarme a) {
        long ms = a.proximo(System.currentTimeMillis()) - System.currentTimeMillis();
        Toast.makeText(this, "Alarme vai tocar " + Agendador.faltaTexto(ms), Toast.LENGTH_SHORT).show();
    }

    private String nomeSom(String som) {
        if ("silencio".equals(som)) return "Silencioso (só vibra)";
        try {
            Uri u = som.isEmpty() ? RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) : Uri.parse(som);
            if (u == null) return "Padrão";
            Ringtone r = RingtoneManager.getRingtone(this, u);
            String t = r != null ? r.getTitle(this) : null;
            return (som.isEmpty() ? "Padrão · " : "") + (t != null ? t : "Toque");
        } catch (Exception e) {
            return "Padrão";
        }
    }

    private void editarAlarme(Dados.Alarme orig) {
        boolean novo = orig == null;
        Dados.Alarme a = new Dados.Alarme();
        if (!novo) {
            a = Dados.Alarme.de(jsonSeguro(orig));
        } else {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.HOUR_OF_DAY, 1);
            a.hora = cal.get(java.util.Calendar.HOUR_OF_DAY);
            a.minuto = 0;
        }
        final Dados.Alarme al = a;
        editando = al;

        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.coluna(this);
        col.setPadding(dp(18), dp(8), dp(18), dp(8));
        sv.addView(col);

        TimePicker tp = new TimePicker(this);
        tp.setIs24HourView(DateFormat.is24HourFormat(this));
        tp.setHour(al.hora);
        tp.setMinute(al.minuto);
        col.addView(tp, Ui.lp(-1, -2));

        // dias da semana (começa na segunda)
        col.addView(rotulo("Repetir"));
        LinearLayout dias = Ui.linha(this);
        dias.setPadding(0, dp(6), 0, dp(4));
        String[] letras = {"D", "S", "T", "Q", "Q", "S", "S"};
        TextView[] bt = new TextView[7];
        TextView resumo = Ui.texto(this, al.textoDias(), 13, k.sub, false);
        Runnable pintar = () -> {
            for (int d = 0; d < 7; d++) {
                boolean on = (al.dias & (1 << d)) != 0;
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(on ? k.destaque : k.chip);
                bt[d].setBackground(g);
                bt[d].setTextColor(on ? k.sobreDestaque : k.texto);
            }
            resumo.setText(al.textoDias());
        };
        for (int i = 1; i <= 7; i++) {
            final int d = i % 7;
            TextView t = Ui.texto(this, letras[d], 15, k.texto, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                al.dias ^= (1 << d);
                pintar.run();
            });
            LinearLayout.LayoutParams lp = Ui.lp(dp(38), dp(38));
            lp.rightMargin = dp(5);
            dias.addView(t, lp);
            bt[d] = t;
        }
        col.addView(dias);
        col.addView(resumo);
        LinearLayout atalhos = Ui.linha(this);
        atalhos.setPadding(0, dp(6), 0, 0);
        String[][] at = {{"Dias úteis", "62"}, {"Fim de semana", "65"}, {"Todo dia", "127"}, {"Uma vez", "0"}};
        for (String[] x : at) {
            TextView t = Ui.texto(this, x[0], 13, k.destaque, false);
            t.setPadding(dp(10), dp(6), dp(10), dp(6));
            t.setBackground(Ui.clicavel(k.chip, dp(14), 0x22000000));
            t.setOnClickListener(v -> {
                al.dias = Integer.parseInt(x[1]);
                pintar.run();
            });
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.rightMargin = dp(6);
            atalhos.addView(t, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(atalhos);
        col.addView(hs);
        pintar.run();

        col.addView(rotulo("Nome do alarme"));
        EditText nome = new EditText(this);
        nome.setHint("Ex.: Trabalho, remédio…");
        nome.setText(al.rotulo);
        nome.setSingleLine(true);
        nome.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nome.setTextColor(k.texto);
        nome.setHintTextColor(k.sub);
        col.addView(nome);

        col.addView(rotulo("Som do alarme"));
        LinearLayout ls = Ui.linha(this);
        ls.setGravity(Gravity.CENTER_VERTICAL);
        ls.setBackground(Ui.clicavel(k.chip, dp(14), 0x22000000));
        ls.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView som = Ui.texto(this, nomeSom(al.som), 15, k.texto, false);
        somEditando = som;
        ls.addView(som, Ui.lp(0, -2, 1f));
        ls.addView(Ui.texto(this, "Trocar", 14, k.destaque, true));
        ls.setOnClickListener(v -> escolherSom(al));
        LinearLayout.LayoutParams lsl = Ui.lp(-1, -2);
        lsl.topMargin = dp(6);
        col.addView(ls, lsl);

        Switch vib = interruptor(col, "Vibrar", al.vibrar);
        Switch grad = interruptor(col, "Aumentar o volume aos poucos", al.gradual);

        col.addView(rotulo("Soneca"));
        String[] sids = {"5", "10", "15", "20", "30"};
        String[] snomes = {"5 min", "10 min", "15 min", "20 min", "30 min"};
        Ui.Chips sn = new Ui.Chips(this, sids, snomes, String.valueOf(al.soneca), null);
        col.addView(sn.view);

        AlertDialog.Builder bd = new AlertDialog.Builder(this).setTitle(novo ? "Novo alarme" : "Editar alarme").setView(sv)
                .setPositiveButton("Salvar", (d, w) -> {
                    al.hora = tp.getHour();
                    al.minuto = tp.getMinute();
                    al.rotulo = nome.getText().toString().trim();
                    al.vibrar = vib.isChecked();
                    al.gradual = grad.isChecked();
                    try {
                        al.soneca = Integer.parseInt(sn.valor());
                    } catch (Exception ignored) {
                    }
                    al.ativo = true;
                    al.sonecaAte = 0;
                    Dados.salvarAlarme(this, al);
                    Agendador.agendarAlarmes(this);
                    avisarQuando(al);
                    editando = null;
                    mostrar();
                })
                .setNegativeButton("Cancelar", (d, w) -> editando = null);
        if (!novo) bd.setNeutralButton("Excluir", (d, w) -> {
            Dados.removerAlarme(this, al.id);
            Agendador.agendarAlarmes(this);
            editando = null;
            mostrar();
        });
        bd.show();
    }

    private org.json.JSONObject jsonSeguro(Dados.Alarme a) {
        try {
            return a.json();
        } catch (org.json.JSONException e) {
            return new org.json.JSONObject();
        }
    }

    private void escolherSom(Dados.Alarme al) {
        String[] op = {"Escolher um toque do celular…", "Som padrão de alarme", "Silencioso (só vibra)"};
        new AlertDialog.Builder(this).setItems(op, (d, w) -> {
            if (w == 0) {
                Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_RINGTONE)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                if (!al.som.isEmpty() && !"silencio".equals(al.som))
                    i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(al.som));
                try {
                    startActivityForResult(i, REQ_SOM);
                } catch (Exception e) {
                    Toast.makeText(this, "Seletor de toques indisponível", Toast.LENGTH_SHORT).show();
                }
            } else {
                al.som = w == 1 ? "" : "silencio";
                if (somEditando != null) somEditando.setText(nomeSom(al.som));
            }
        }).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SOM && res == RESULT_OK && data != null && editando != null) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            editando.som = u == null || RingtoneManager.isDefault(u) ? "" : u.toString();
            if (somEditando != null) somEditando.setText(nomeSom(editando.som));
        }
    }

    private TextView rotulo(String s) {
        TextView t = Ui.texto(this, s, 14, k.sub, true);
        t.setPadding(0, dp(16), 0, 0);
        return t;
    }

    private Switch interruptor(LinearLayout col, String t, boolean on) {
        Switch s = new Switch(this);
        s.setText(t);
        s.setTextSize(15);
        s.setTextColor(k.texto);
        s.setChecked(on);
        s.setPadding(0, dp(14), 0, dp(4));
        col.addView(s);
        return s;
    }

    // ================================================================== RELÓGIO

    private void abaRelogio() {
        LinearLayout col = pagina("Relógio", null);
        LinearLayout grande = Ui.coluna(this);
        grande.setGravity(Gravity.CENTER_HORIZONTAL);
        grande.setPadding(dp(10), dp(26), dp(10), dp(26));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1E1B4B, 0xFF172554});
        g.setCornerRadius(dp(28));
        grande.setBackground(g);
        LinearLayout.LayoutParams gl = Ui.lp(-1, -2);
        gl.topMargin = dp(12);
        col.addView(grande, gl);

        LinearLayout lh = Ui.linha(this);
        lh.setGravity(Gravity.BOTTOM);
        TextClock tc = new TextClock(this);
        tc.setFormat24Hour("HH:mm");
        tc.setFormat12Hour("h:mm");
        tc.setTextSize(78);
        tc.setTextColor(0xFFFFFFFF);
        tc.setTypeface(Fontes.typeface(this, "quicksand"));
        tc.setIncludeFontPadding(false);
        lh.addView(tc);
        TextClock seg = new TextClock(this);
        seg.setFormat24Hour("ss");
        seg.setFormat12Hour("ss a");
        seg.setTextSize(24);
        seg.setTextColor(0xFF9AA6FF);
        seg.setTypeface(Fontes.typeface(this, "quicksand"));
        seg.setPadding(dp(6), 0, 0, dp(12));
        lh.addView(seg);
        grande.addView(lh);
        TextClock data = new TextClock(this);
        data.setFormat24Hour("EEEE, d 'de' MMMM 'de' yyyy");
        data.setFormat12Hour("EEEE, d 'de' MMMM 'de' yyyy");
        data.setTextSize(16);
        data.setTextColor(0xCCFFFFFF);
        grande.addView(data);
        Dados.Alarme prox = Agendador.proximoAlarme(this);
        if (prox != null) {
            long t = prox.proximo(System.currentTimeMillis());
            TextView pa = Ui.texto(this, "⏰  " + Agendador.diaSemanaCurto(t) + " " + Agendador.horaTexto(this, t), 14, 0xCCFFFFFF, false);
            pa.setPadding(dp(14), dp(6), dp(14), dp(6));
            pa.setBackground(Ui.fundo(0x22FFFFFF, dp(14)));
            LinearLayout.LayoutParams pl = Ui.lp(-2, -2);
            pl.topMargin = dp(14);
            grande.addView(pa, pl);
        }

        LinearLayout titulo = Ui.linha(this);
        titulo.setGravity(Gravity.CENTER_VERTICAL);
        titulo.setPadding(dp(4), dp(22), 0, dp(2));
        titulo.addView(Ui.texto(this, "Relógio mundial", 18, k.texto, true), Ui.lp(0, -2, 1f));
        col.addView(titulo);

        List<String> cs = Dados.cidades(this);
        if (cs.isEmpty()) col.addView(vazio("Adicione cidades com o botão +"));
        long agora = System.currentTimeMillis();
        int meu = TimeZone.getDefault().getOffset(agora);
        for (String tz : cs) {
            LinearLayout c = Ui.linha(this);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setBackground(Ui.clicavel(k.superficie, dp(22), 0x22FFFFFF));
            c.setPadding(dp(18), dp(14), dp(18), dp(14));
            LinearLayout e = Ui.coluna(this);
            e.addView(Ui.texto(this, Widgets.Cidades.nome(tz), 17, k.texto, true));
            int dif = TimeZone.getTimeZone(tz).getOffset(agora) - meu;
            float hrs = dif / 3_600_000f;
            String ds = dif == 0 ? "Mesmo horário" : (hrs > 0 ? "+" : "") + (hrs == (int) hrs ? String.valueOf((int) hrs) : String.valueOf(hrs)) + " h";
            TextClock dia = new TextClock(this);
            dia.setTimeZone(tz);
            dia.setFormat24Hour("EEE, d MMM");
            dia.setFormat12Hour("EEE, d MMM");
            dia.setTextSize(13);
            dia.setTextColor(k.sub);
            LinearLayout l2 = Ui.linha(this);
            l2.addView(dia);
            l2.addView(Ui.texto(this, "  ·  " + ds, 13, k.sub, false));
            e.addView(l2);
            c.addView(e, Ui.lp(0, -2, 1f));
            TextClock t = new TextClock(this);
            t.setTimeZone(tz);
            t.setFormat24Hour("HH:mm");
            t.setFormat12Hour("h:mm a");
            t.setTextSize(32);
            t.setTextColor(k.texto);
            t.setTypeface(Fontes.typeface(this, "quicksand"));
            c.addView(t);
            c.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage("Remover " + Widgets.Cidades.nome(tz) + "?")
                        .setPositiveButton("Remover", (d, w) -> {
                            List<String> l = Dados.cidades(this);
                            l.remove(tz);
                            Dados.salvarCidades(this, l);
                            mostrar();
                        }).setNegativeButton("Cancelar", null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = dp(10);
            col.addView(c, lp);
        }
        if (!cs.isEmpty()) {
            TextView dica = Ui.texto(this, "Segure uma cidade para remover", 12, k.sub, false);
            dica.setGravity(Gravity.CENTER);
            dica.setPadding(0, dp(10), 0, 0);
            col.addView(dica);
        }
        botaoFlutuante(v -> {
            List<String[]> todas = Widgets.Cidades.todas();
            String[] n = new String[todas.size()];
            for (int i = 0; i < n.length; i++) n[i] = todas.get(i)[1];
            new AlertDialog.Builder(this).setTitle("Adicionar cidade").setItems(n, (d, w) -> {
                List<String> l = Dados.cidades(this);
                if (!l.contains(todas.get(w)[0])) l.add(todas.get(w)[0]);
                Dados.salvarCidades(this, l);
                mostrar();
            }).show();
        });
    }

    // ================================================================== CRONÔMETRO

    private static String cronoTexto(long ms, boolean centesimos) {
        long cs = (ms / 10) % 100, s = (ms / 1000) % 60, m = (ms / 60000) % 60, hh = ms / 3_600_000;
        String base = hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, m, s) : String.format(Locale.US, "%02d:%02d", m, s);
        return centesimos ? base + String.format(Locale.US, ".%02d", cs) : base;
    }

    private void abaCronometro() {
        LinearLayout col = pagina("Cronômetro", null);
        Dados.Crono cr = Dados.crono(this);

        FrameLayout anel = new FrameLayout(this);
        int tam = Math.min(getResources().getDisplayMetrics().widthPixels - dp(80), dp(300));
        AnelView av = new AnelView(this, k.superficie);
        anel.addView(av, new FrameLayout.LayoutParams(tam, tam, Gravity.CENTER));
        LinearLayout centro = Ui.coluna(this);
        centro.setGravity(Gravity.CENTER);
        TextView tempo = Ui.texto(this, "00:00.00", 50, k.texto, false);
        tempo.setTypeface(Fontes.typeface(this, "quicksand"));
        centro.addView(tempo);
        TextView volta = Ui.texto(this, "", 15, k.sub, false);
        centro.addView(volta);
        anel.addView(centro, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams al = Ui.lp(-1, tam + dp(20));
        al.topMargin = dp(10);
        col.addView(anel, al);

        LinearLayout bts = Ui.linha(this);
        Button esq = Ui.botao(this, "", false);
        Button dir = Ui.botaoGradiente(this, "");
        esq.setTextSize(17);
        dir.setTextSize(17);
        bts.addView(esq, Ui.lp(0, dp(58), 1f));
        LinearLayout.LayoutParams dl = Ui.lp(0, dp(58), 1f);
        dl.leftMargin = dp(12);
        bts.addView(dir, dl);
        col.addView(bts);

        LinearLayout voltas = Ui.coluna(this);
        voltas.setPadding(0, dp(10), 0, 0);
        col.addView(voltas);

        Runnable pintarVoltas = () -> {
            voltas.removeAllViews();
            List<Long> v = cr.voltas;
            if (v.isEmpty()) return;
            long melhor = Long.MAX_VALUE, pior = 0;
            List<Long> parc = new ArrayList<>();
            for (int i = 0; i < v.size(); i++) {
                long p = v.get(i) - (i == 0 ? 0 : v.get(i - 1));
                parc.add(p);
                melhor = Math.min(melhor, p);
                pior = Math.max(pior, p);
            }
            for (int i = v.size() - 1; i >= 0; i--) {
                LinearLayout r = Ui.linha(this);
                r.setPadding(dp(16), dp(12), dp(16), dp(12));
                long p = parc.get(i);
                int cor = v.size() > 2 && p == melhor ? 0xFF4ADE80 : v.size() > 2 && p == pior ? 0xFFF87171 : k.texto;
                r.addView(Ui.texto(this, String.format(Locale.US, "Volta %02d", i + 1), 15, k.sub, false), Ui.lp(0, -2, 1f));
                r.addView(Ui.texto(this, "+" + cronoTexto(p, true), 15, cor, false), Ui.lp(0, -2, 1f));
                TextView tt = Ui.texto(this, cronoTexto(v.get(i), true), 15, k.texto, true);
                tt.setGravity(Gravity.END);
                r.addView(tt, Ui.lp(0, -2, 1f));
                voltas.addView(r);
                View sep = new View(this);
                sep.setBackgroundColor(k.linha);
                voltas.addView(sep, Ui.lp(-1, 1));
            }
        };
        Runnable estado = () -> {
            boolean rodando = cr.inicio > 0;
            boolean zerado = !rodando && cr.acumulado == 0;
            dir.setText(rodando ? "Pausar" : zerado ? "Iniciar" : "Continuar");
            esq.setText(rodando ? "Volta" : "Zerar");
            esq.setEnabled(!zerado);
            esq.setAlpha(zerado ? 0.5f : 1f);
        };
        dir.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (cr.inicio > 0) {
                cr.acumulado += now - cr.inicio;
                cr.inicio = 0;
            } else cr.inicio = now;
            Dados.salvarCrono(this, cr);
            estado.run();
        });
        esq.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (cr.inicio > 0) {
                cr.voltas.add(cr.total(now));
            } else {
                cr.acumulado = 0;
                cr.voltas.clear();
            }
            Dados.salvarCrono(this, cr);
            pintarVoltas.run();
            estado.run();
        });
        estado.run();
        pintarVoltas.run();
        iniciarTick(() -> {
            long tot = cr.total(SystemClock.elapsedRealtime());
            tempo.setText(cronoTexto(tot, true));
            long ultima = cr.voltas.isEmpty() ? 0 : cr.voltas.get(cr.voltas.size() - 1);
            volta.setText(cr.voltas.isEmpty() ? "" : String.format(Locale.US, "Volta %d · %s", cr.voltas.size() + 1, cronoTexto(tot - ultima, true)));
            av.setFracao((tot % 60000) / 60000f);
        }, 33);
    }

    /** anel de progresso desenhado à mão (leve e bonito) */
    static final class AnelView extends View {
        private final android.graphics.Paint trilho = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint arco = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private float fracao;

        AnelView(android.content.Context c, int corTrilho) {
            super(c);
            float w = Ui.dp(c, 9);
            trilho.setStyle(android.graphics.Paint.Style.STROKE);
            trilho.setStrokeWidth(w);
            trilho.setColor(corTrilho);
            arco.setStyle(android.graphics.Paint.Style.STROKE);
            arco.setStrokeWidth(w);
            arco.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        void setFracao(float f) {
            fracao = f;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            arco.setShader(new android.graphics.SweepGradient(w / 2f, h / 2f, new int[]{0xFF3B82F6, 0xFF6D5BFF, 0xFFC084FC, 0xFF3B82F6}, null));
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float m = arco.getStrokeWidth();
            r.set(m, m, getWidth() - m, getHeight() - m);
            c.drawOval(r, trilho);
            c.save();
            c.rotate(-90, getWidth() / 2f, getHeight() / 2f);
            if (fracao > 0) c.drawArc(r, 0, 360 * fracao, false, arco);
            c.restore();
        }
    }

    // ================================================================== TEMPORIZADOR

    private void abaTemporizador() {
        LinearLayout col = pagina("Temporizador", null);
        List<Dados.Timer> l = Dados.timers(this);
        List<Runnable> atualizar = new ArrayList<>();
        for (Dados.Timer t : l) col.addView(cartaoTimer(t, atualizar));
        novoTimer(col, l.isEmpty());
        if (!atualizar.isEmpty()) iniciarTick(() -> {
            for (Runnable r : atualizar) r.run();
        }, 200);
    }

    private void novoTimer(LinearLayout col, boolean aberto) {
        LinearLayout card = Ui.coluna(this);
        card.setBackground(Ui.fundo(k.superficie, dp(24)));
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams cl = Ui.lp(-1, -2);
        cl.topMargin = dp(12);
        col.addView(card, cl);
        card.addView(Ui.texto(this, "Novo temporizador", 17, k.texto, true));

        LinearLayout pick = Ui.linha(this);
        pick.setGravity(Gravity.CENTER);
        NumberPicker ph = picker(23, 0), pm = picker(59, 5), ps = picker(59, 0);
        pick.addView(coluna(ph, "horas"));
        pick.addView(Ui.texto(this, ":", 28, k.sub, true));
        pick.addView(coluna(pm, "min"));
        pick.addView(Ui.texto(this, ":", 28, k.sub, true));
        pick.addView(coluna(ps, "seg"));
        card.addView(pick);

        LinearLayout pre = Ui.linha(this);
        pre.setPadding(0, dp(6), 0, dp(4));
        int[] mins = {1, 3, 5, 10, 15, 30, 60};
        for (int m : mins) {
            TextView t = Ui.texto(this, m < 60 ? m + " min" : "1 h", 14, k.texto, false);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            t.setBackground(Ui.clicavel(k.chip, dp(16), 0x22000000));
            t.setOnClickListener(v -> {
                ph.setValue(m / 60);
                pm.setValue(m % 60);
                ps.setValue(0);
            });
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.rightMargin = dp(8);
            pre.addView(t, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pre);
        card.addView(hs);

        EditText nome = new EditText(this);
        nome.setHint("Nome (opcional): ovo, pizza, estudo…");
        nome.setSingleLine(true);
        nome.setTextColor(k.texto);
        nome.setHintTextColor(k.sub);
        card.addView(nome);

        Button ir = Ui.botaoGradiente(this, "▶  Iniciar");
        ir.setTextSize(17);
        ir.setOnClickListener(v -> {
            long ms = ((ph.getValue() * 60L + pm.getValue()) * 60L + ps.getValue()) * 1000L;
            if (ms <= 0) {
                Toast.makeText(this, "Escolha um tempo", Toast.LENGTH_SHORT).show();
                return;
            }
            Dados.Timer t = new Dados.Timer();
            t.duracao = ms;
            t.restante = ms;
            t.fimEm = System.currentTimeMillis() + ms;
            t.rotulo = nome.getText().toString().trim();
            Dados.salvarTimer(this, t);
            Agendador.agendarTimers(this);
            mostrar();
        });
        LinearLayout.LayoutParams il = Ui.lp(-1, dp(56));
        il.topMargin = dp(10);
        card.addView(ir, il);
    }

    private NumberPicker picker(int max, int v) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(0);
        p.setMaxValue(max);
        p.setValue(v);
        p.setFormatter(i -> String.format(Locale.US, "%02d", i));
        if (Build.VERSION.SDK_INT >= 29) {
            p.setTextColor(k.texto);
            p.setTextSize(dp(30));
        }
        return p;
    }

    private LinearLayout coluna(NumberPicker p, String n) {
        LinearLayout c = Ui.coluna(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(p);
        c.addView(Ui.texto(this, n, 12, k.sub, false));
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.setMargins(dp(8), 0, dp(8), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private View cartaoTimer(Dados.Timer t0, List<Runnable> atualizar) {
        LinearLayout c = Ui.coluna(this);
        c.setBackground(Ui.fundo(k.superficie, dp(24)));
        c.setPadding(dp(18), dp(14), dp(14), dp(14));
        LinearLayout topo = Ui.linha(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        topo.addView(Ui.texto(this, t0.rotulo.isEmpty() ? "Temporizador · " + Agendador.duracaoTexto(t0.duracao) : t0.rotulo + " · " + Agendador.duracaoTexto(t0.duracao),
                14, k.sub, false), Ui.lp(0, -2, 1f));
        ImageView lixo = new ImageView(this);
        lixo.setImageResource(R.drawable.ic_lixo);
        lixo.setColorFilter(k.sub);
        lixo.setPadding(dp(6), dp(6), dp(6), dp(6));
        lixo.setOnClickListener(v -> {
            if (TocarService.tocando && "timer".equals(TocarService.tipoAtual) && TocarService.idAtual == t0.id)
                TocarService.comando(this, TocarService.ACAO_PARAR);
            Agendador.cancelarTimer(this, t0.id);
            Dados.removerTimer(this, t0.id);
            Agendador.agendarTimers(this);
            mostrar();
        });
        topo.addView(lixo, Ui.lp(dp(36), dp(36)));
        c.addView(topo);

        TextView tempo = Ui.texto(this, "", 52, k.texto, false);
        tempo.setTypeface(Fontes.typeface(this, "quicksand"));
        c.addView(tempo);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(k.destaque));
        pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(k.linha));
        c.addView(pb, Ui.lp(-1, dp(8)));

        LinearLayout bts = Ui.linha(this);
        bts.setPadding(0, dp(12), 0, 0);
        Button reset = Ui.botao(this, "Zerar", false);
        Button mais = Ui.botao(this, "+1 min", false);
        Button play = Ui.botaoGradiente(this, "");
        bts.addView(reset, Ui.lp(0, dp(50), 1f));
        LinearLayout.LayoutParams ml = Ui.lp(0, dp(50), 1f);
        ml.leftMargin = dp(8);
        bts.addView(mais, ml);
        LinearLayout.LayoutParams pl = Ui.lp(0, dp(50), 1.2f);
        pl.leftMargin = dp(8);
        bts.addView(play, pl);
        c.addView(bts);

        final int id = t0.id;
        Runnable pintar = () -> {
            Dados.Timer t = Dados.timer(this, id);
            if (t == null) return;
            long f = t.faltam(System.currentTimeMillis());
            boolean tocandoEste = t.tocando || (t.rodando() && f == 0);
            tempo.setText(tocandoEste ? "00:00" : Agendador.duracaoTexto(f));
            tempo.setTextColor(tocandoEste ? 0xFFF87171 : t.rodando() ? k.texto : k.sub);
            pb.setProgress(t.duracao == 0 ? 0 : (int) (1000 * f / t.duracao));
            play.setText(tocandoEste ? "■  Parar" : t.rodando() ? "❚❚  Pausar" : "▶  Continuar");
        };
        play.setOnClickListener(v -> {
            Dados.Timer t = Dados.timer(this, id);
            if (t == null) return;
            long agora = System.currentTimeMillis();
            if (t.tocando || (t.rodando() && t.faltam(agora) == 0)) {
                if (TocarService.tocando) TocarService.comando(this, TocarService.ACAO_PARAR);
                t.tocando = false;
                t.fimEm = 0;
                t.restante = t.duracao;
            } else if (t.rodando()) {
                t.restante = t.faltam(agora);
                t.fimEm = 0;
            } else {
                if (t.restante <= 0) t.restante = t.duracao;
                t.fimEm = agora + t.restante;
            }
            Dados.salvarTimer(this, t);
            Agendador.agendarTimers(this);
            pintar.run();
        });
        reset.setOnClickListener(v -> {
            Dados.Timer t = Dados.timer(this, id);
            if (t == null) return;
            if (t.tocando && TocarService.tocando) TocarService.comando(this, TocarService.ACAO_PARAR);
            t.tocando = false;
            t.fimEm = 0;
            t.restante = t.duracao;
            Dados.salvarTimer(this, t);
            Agendador.agendarTimers(this);
            pintar.run();
        });
        mais.setOnClickListener(v -> {
            Dados.Timer t = Dados.timer(this, id);
            if (t == null) return;
            long agora = System.currentTimeMillis();
            if (t.tocando) {
                TocarService.comando(this, TocarService.ACAO_MAIS1);
                return;
            }
            if (t.rodando()) t.fimEm = Math.max(t.fimEm, agora) + 60_000L;
            else t.restante += 60_000L;
            t.duracao = Math.max(t.duracao, t.faltam(agora));
            Dados.salvarTimer(this, t);
            Agendador.agendarTimers(this);
            pintar.run();
        });
        pintar.run();
        atualizar.add(pintar);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    // ================================================================== WIDGETS

    private void abaWidgets() {
        LinearLayout col = pagina("Widgets", "Escolha um estilo, personalize e coloque na tela inicial");
        for (int i = 0; i < Widgets.TIPOS.length; i++) {
            String tipo = Widgets.TIPOS[i];
            if ("analogico".equals(tipo)) {
                col.addView(galeriaAnalogicos());
                continue;
            }
            Dados.WCfg cfg = Widgets.padrao(tipo);
            col.addView(cartaoWidget(tipo, Widgets.NOMES_TIPOS[i], cfg, i));
        }
        // amostras de fontes
        LinearLayout s = Ui.secao(this, col, "Fontes disponíveis", "Todas podem ser usadas nos widgets digitais");
        for (int i = 0; i < Widgets.FONTES.length; i++) {
            LinearLayout r = Ui.linha(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(8), 0, dp(8));
            TextView a = Ui.texto(this, "12:45", 30, k.texto, false);
            a.setTypeface(Fontes.typeface(this, Widgets.FONTES[i]));
            r.addView(a, Ui.lp(0, -2, 1f));
            r.addView(Ui.texto(this, Widgets.NOMES_FONTES[i], 13, k.sub, false));
            s.addView(r);
        }
    }

    private View cartaoWidget(String tipo, String nome, Dados.WCfg cfg, int idx) {
        LinearLayout c = Ui.coluna(this);
        c.setBackground(Ui.fundo(k.superficie, dp(24)));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout topo = Ui.linha(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tt = Ui.coluna(this);
        tt.addView(Ui.texto(this, nome, 17, k.texto, true));
        int[] cel = Widgets.CELULAS[idx];
        tt.addView(Ui.texto(this, cel[0] + " × " + cel[1] + " · redimensionável", 13, k.sub, false));
        topo.addView(tt, Ui.lp(0, -2, 1f));
        Button add = Ui.botaoGradiente(this, "Adicionar");
        add.setTextSize(14);
        add.setOnClickListener(v -> startActivity(new Intent(this, WidgetConfigActivity.class).putExtra(WidgetConfigActivity.EXTRA_TIPO, tipo)));
        topo.addView(add, Ui.lp(-2, dp(44)));
        c.addView(topo);
        c.addView(previa(tipo, cfg));
        c.setOnClickListener(v -> startActivity(new Intent(this, WidgetConfigActivity.class).putExtra(WidgetConfigActivity.EXTRA_TIPO, tipo)));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View galeriaAnalogicos() {
        LinearLayout c = Ui.coluna(this);
        c.setBackground(Ui.fundo(k.superficie, dp(24)));
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout topo = Ui.linha(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tt = Ui.coluna(this);
        tt.addView(Ui.texto(this, "Analógico", 17, k.texto, true));
        tt.addView(Ui.texto(this, Widgets.DIAIS.length + " mostradores · 2 × 2", 13, k.sub, false));
        topo.addView(tt, Ui.lp(0, -2, 1f));
        Button add = Ui.botaoGradiente(this, "Adicionar");
        add.setTextSize(14);
        add.setOnClickListener(v -> startActivity(new Intent(this, WidgetConfigActivity.class).putExtra(WidgetConfigActivity.EXTRA_TIPO, "analogico")));
        topo.addView(add, Ui.lp(-2, dp(44)));
        c.addView(topo);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout l = Ui.linha(this);
        l.setPadding(0, dp(12), 0, 0);
        hs.addView(l);
        for (String d : Widgets.DIAIS) {
            FrameLayout f = new FrameLayout(this);
            f.setBackground(fundoPapel());
            f.setPadding(dp(10), dp(10), dp(10), dp(10));
            ImageView iv = new ImageView(this);
            iv.setImageResource(getResources().getIdentifier("mostrador_" + d, "drawable", getPackageName()));
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            f.setOnClickListener(v -> {
                startActivity(new Intent(this, WidgetConfigActivity.class).putExtra(WidgetConfigActivity.EXTRA_TIPO, "analogico").putExtra("dial", d));
            });
            LinearLayout.LayoutParams lp = Ui.lp(dp(120), dp(120));
            lp.rightMargin = dp(10);
            l.addView(f, lp);
        }
        c.addView(hs);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private GradientDrawable fundoPapel() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4F46E5, 0xFF0EA5E9, 0xFF22C55E});
        g.setCornerRadius(dp(18));
        return g;
    }

    private View previa(String tipo, Dados.WCfg cfg) {
        int[] t = Widgets.tamanhoCelulas(this, tipo);
        float larguraDisp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density - 60;
        float esc = Math.min(1f, larguraDisp / t[0]);
        FrameLayout palco = new FrameLayout(this);
        palco.setBackground(fundoPapel());
        palco.setPadding(dp(8), dp(10), dp(8), dp(10));
        FrameLayout caixa = new FrameLayout(this);
        palco.addView(caixa, new FrameLayout.LayoutParams(dp(t[0] * esc), dp(t[1] * esc), Gravity.CENTER));
        try {
            RemoteViews rv = Widgets.montar(this, tipo, cfg, t[0], t[1]);
            View v = rv.apply(this, caixa);
            v.setPivotX(0);
            v.setPivotY(0);
            v.setScaleX(esc);
            v.setScaleY(esc);
            caixa.addView(v, new FrameLayout.LayoutParams(dp(t[0]), dp(t[1])));
        } catch (Exception e) {
            caixa.addView(Ui.texto(this, "Prévia indisponível", 13, 0xFFFFFFFF, false));
        }
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = dp(12);
        palco.setLayoutParams(lp);
        return palco;
    }
}
