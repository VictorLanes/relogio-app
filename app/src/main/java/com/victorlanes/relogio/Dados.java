package com.victorlanes.relogio;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Alarmes, temporizadores, cronômetro, cidades e configuração dos widgets (SharedPreferences + JSON). */
public final class Dados {

    private Dados() {
    }

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("relogio", Context.MODE_PRIVATE);
    }

    // ================================================================== alarmes

    public static final class Alarme {
        public int id;
        public int hora = 7, minuto = 0;
        public int dias = 0; // bit 0 = domingo ... bit 6 = sábado (0 = só uma vez)
        public boolean ativo = true;
        public String rotulo = "";
        public String som = ""; // "" = som padrão de alarme
        public boolean vibrar = true;
        public int soneca = 10;
        public boolean gradual = true;
        public long sonecaAte = 0; // horário da soneca pendente (0 = nenhuma)

        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("hora", hora).put("minuto", minuto).put("dias", dias).put("ativo", ativo)
                    .put("rotulo", rotulo).put("som", som).put("vibrar", vibrar).put("soneca", soneca).put("gradual", gradual)
                    .put("sonecaAte", sonecaAte);
        }

        static Alarme de(JSONObject o) {
            Alarme a = new Alarme();
            a.id = o.optInt("id");
            a.hora = o.optInt("hora", 7);
            a.minuto = o.optInt("minuto");
            a.dias = o.optInt("dias");
            a.ativo = o.optBoolean("ativo", true);
            a.rotulo = o.optString("rotulo", "");
            a.som = o.optString("som", "");
            a.vibrar = o.optBoolean("vibrar", true);
            a.soneca = o.optInt("soneca", 10);
            a.gradual = o.optBoolean("gradual", true);
            a.sonecaAte = o.optLong("sonecaAte", 0);
            return a;
        }

        /** próximo horário em que este alarme toca (ms) */
        public long proximo(long agora) {
            if (sonecaAte > agora) return sonecaAte;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(agora);
            c.set(Calendar.HOUR_OF_DAY, hora);
            c.set(Calendar.MINUTE, minuto);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < 8; i++) {
                if (c.getTimeInMillis() > agora) {
                    int dow = c.get(Calendar.DAY_OF_WEEK) - 1; // 0 = domingo
                    if (dias == 0 || (dias & (1 << dow)) != 0) return c.getTimeInMillis();
                }
                c.add(Calendar.DAY_OF_YEAR, 1);
            }
            return c.getTimeInMillis();
        }

        public String textoDias() {
            if (dias == 0) return "Uma vez";
            if (dias == 0x7F) return "Todos os dias";
            if (dias == 0x3E) return "Segunda a sexta";
            if (dias == 0x41) return "Fins de semana";
            String[] n = {"Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"};
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 7; i++) {
                int d = i % 7; // começa na segunda
                if ((dias & (1 << d)) != 0) sb.append(sb.length() > 0 ? ", " : "").append(n[d]);
            }
            return sb.toString();
        }
    }

    public static synchronized List<Alarme> alarmes(Context c) {
        List<Alarme> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("alarmes", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Alarme.de(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        l.sort((x, y) -> (x.hora * 60 + x.minuto) - (y.hora * 60 + y.minuto));
        return l;
    }

    public static synchronized void salvarAlarmes(Context c, List<Alarme> l) {
        JSONArray a = new JSONArray();
        try {
            for (Alarme x : l) a.put(x.json());
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("alarmes", a.toString()).apply();
    }

    public static Alarme alarme(Context c, int id) {
        for (Alarme a : alarmes(c)) if (a.id == id) return a;
        return null;
    }

    public static synchronized void salvarAlarme(Context c, Alarme novo) {
        List<Alarme> l = alarmes(c);
        if (novo.id == 0) novo.id = novoId(c);
        boolean achou = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == novo.id) {
            l.set(i, novo);
            achou = true;
        }
        if (!achou) l.add(novo);
        salvarAlarmes(c, l);
    }

    public static synchronized void removerAlarme(Context c, int id) {
        List<Alarme> l = alarmes(c);
        l.removeIf(a -> a.id == id);
        salvarAlarmes(c, l);
    }

    public static synchronized int novoId(Context c) {
        int id = sp(c).getInt("proxId", 100) + 1;
        sp(c).edit().putInt("proxId", id).apply();
        return id;
    }

    // ================================================================== temporizadores

    public static final class Timer {
        public int id;
        public long duracao;   // ms
        public long restante;  // ms (quando pausado)
        public long fimEm;     // relógio de parede (ms) quando rodando; 0 = pausado
        public String rotulo = "";
        public boolean tocando;

        public boolean rodando() {
            return fimEm > 0;
        }

        public long faltam(long agora) {
            return rodando() ? Math.max(0, fimEm - agora) : restante;
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("duracao", duracao).put("restante", restante).put("fimEm", fimEm)
                    .put("rotulo", rotulo).put("tocando", tocando);
        }

        static Timer de(JSONObject o) {
            Timer t = new Timer();
            t.id = o.optInt("id");
            t.duracao = o.optLong("duracao");
            t.restante = o.optLong("restante");
            t.fimEm = o.optLong("fimEm");
            t.rotulo = o.optString("rotulo", "");
            t.tocando = o.optBoolean("tocando");
            return t;
        }
    }

    public static synchronized List<Timer> timers(Context c) {
        List<Timer> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("timers", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Timer.de(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        return l;
    }

    public static synchronized void salvarTimers(Context c, List<Timer> l) {
        JSONArray a = new JSONArray();
        try {
            for (Timer x : l) a.put(x.json());
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("timers", a.toString()).apply();
    }

    public static Timer timer(Context c, int id) {
        for (Timer t : timers(c)) if (t.id == id) return t;
        return null;
    }

    public static synchronized void salvarTimer(Context c, Timer t) {
        List<Timer> l = timers(c);
        if (t.id == 0) t.id = novoId(c);
        boolean achou = false;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == t.id) {
            l.set(i, t);
            achou = true;
        }
        if (!achou) l.add(t);
        salvarTimers(c, l);
    }

    public static synchronized void removerTimer(Context c, int id) {
        List<Timer> l = timers(c);
        l.removeIf(t -> t.id == id);
        salvarTimers(c, l);
    }

    // ================================================================== cronômetro

    public static final class Crono {
        public long inicio;      // SystemClock.elapsedRealtime() quando começou a rodar (0 = parado)
        public long acumulado;   // ms acumulados antes do último início
        public List<Long> voltas = new ArrayList<>();

        public long total(long agoraElapsed) {
            return acumulado + (inicio > 0 ? agoraElapsed - inicio : 0);
        }
    }

    public static Crono crono(Context c) {
        Crono k = new Crono();
        SharedPreferences p = sp(c);
        k.inicio = p.getLong("cronoInicio", 0);
        k.acumulado = p.getLong("cronoAcum", 0);
        try {
            JSONArray a = new JSONArray(p.getString("cronoVoltas", "[]"));
            for (int i = 0; i < a.length(); i++) k.voltas.add(a.getLong(i));
        } catch (JSONException ignored) {
        }
        return k;
    }

    public static void salvarCrono(Context c, Crono k) {
        sp(c).edit().putLong("cronoInicio", k.inicio).putLong("cronoAcum", k.acumulado)
                .putString("cronoVoltas", new JSONArray(k.voltas).toString()).apply();
    }

    // ================================================================== cidades

    public static List<String> cidades(Context c) {
        List<String> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("cidades", "[\"Europe/Lisbon\",\"America/New_York\"]"));
            for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        } catch (JSONException ignored) {
        }
        return l;
    }

    public static void salvarCidades(Context c, List<String> l) {
        sp(c).edit().putString("cidades", new JSONArray(l).toString()).apply();
    }

    // ================================================================== widgets

    public static final class WCfg {
        public String fonte = "quicksand";
        public String dial = "classico";
        public int corTexto = 0xFFFFFFFF;
        public int corFundo = 0xFF000000;
        public int alfaFundo = 0;     // 0 = sem fundo
        public boolean h24 = true;
        public boolean data = true;
        public boolean segundos = false;
        public String fuso = "";      // "" = horário do celular
        public int tamanho = 100;     // % do tamanho automático do texto

        public JSONObject json() throws JSONException {
            return new JSONObject().put("fonte", fonte).put("dial", dial).put("corTexto", corTexto).put("corFundo", corFundo)
                    .put("alfaFundo", alfaFundo).put("h24", h24).put("data", data).put("segundos", segundos).put("fuso", fuso)
                    .put("tamanho", tamanho);
        }

        public static WCfg de(JSONObject o) {
            WCfg w = new WCfg();
            w.fonte = o.optString("fonte", w.fonte);
            w.dial = o.optString("dial", w.dial);
            w.corTexto = o.optInt("corTexto", w.corTexto);
            w.corFundo = o.optInt("corFundo", w.corFundo);
            w.alfaFundo = o.optInt("alfaFundo", w.alfaFundo);
            w.h24 = o.optBoolean("h24", true);
            w.data = o.optBoolean("data", true);
            w.segundos = o.optBoolean("segundos", false);
            w.fuso = o.optString("fuso", "");
            w.tamanho = o.optInt("tamanho", 100);
            return w;
        }

        public WCfg copia() {
            try {
                return de(json());
            } catch (JSONException e) {
                return new WCfg();
            }
        }
    }

    public static WCfg widget(Context c, int id) {
        String s = sp(c).getString("w_" + id, null);
        if (s == null) return null;
        try {
            return WCfg.de(new JSONObject(s));
        } catch (JSONException e) {
            return null;
        }
    }

    public static void salvarWidget(Context c, int id, WCfg w) {
        try {
            sp(c).edit().putString("w_" + id, w.json().toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static void removerWidget(Context c, int id) {
        sp(c).edit().remove("w_" + id).apply();
    }

    public static void salvarPendente(Context c, WCfg w) {
        try {
            sp(c).edit().putString("w_pendente", w.json().toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public static WCfg pegarPendente(Context c) {
        String s = sp(c).getString("w_pendente", null);
        sp(c).edit().remove("w_pendente").apply();
        if (s == null) return null;
        try {
            return WCfg.de(new JSONObject(s));
        } catch (JSONException e) {
            return null;
        }
    }

    public static String tema(Context c) {
        return sp(c).getString("tema", "escuro");
    }
}
