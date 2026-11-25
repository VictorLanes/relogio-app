"""Gera os layouts dos widgets (um por fonte e por tipo) e os mostradores analógicos.
Rodar a partir da raiz do projeto: python3 tools/gerar_layouts.py"""
import os, math
RES = 'app/src/main/res'
FONTES = ['lcd', 'orbitron', 'bebas', 'oswald', 'quicksand', 'playfair', 'pacifico', 'monoton', 'sharetech',
          'righteous', 'comfortaa', 'majormono', 'thin', 'condensed']
SISTEMA = {'thin': 'sans-serif-thin', 'condensed': 'sans-serif-condensed'}
# fontes sem letras (ou difíceis de ler) usam outra fonte na data
DATA_ALT = {'lcd': 'sans-serif-light', 'monoton': 'sans-serif-light'}

def fam(f):
    return SISTEMA.get(f, '@font/' + f)

def famData(f):
    return DATA_ALT.get(f, fam(f))

A = 'xmlns:android="http://schemas.android.com/apk/res/android"'

def tc(id_, f, size, fmt12, fmt24, extra=''):
    return f'''<TextClock android:id="@+id/{id_}" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:fontFamily="{f}" android:textSize="{size}dp" android:textColor="#FFFFFFFF" android:maxLines="1"
        android:includeFontPadding="false" android:format12Hour="{fmt12}" android:format24Hour="{fmt24}" {extra}/>'''

def fundo():
    return '''<ImageView android:id="@+id/fundo" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitXY" android:src="@drawable/fundo_widget" android:importantForAccessibility="no"/>'''

def extra(f):
    return f'''<TextView android:id="@+id/extra" android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:fontFamily="{famData(f)}" android:textSize="13dp" android:textColor="#CCFFFFFF" android:maxLines="1"
        android:layout_marginTop="2dp" android:visibility="gone"/>'''

def raiz(corpo):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<FrameLayout {A} android:id="@+id/raiz" android:layout_width="match_parent" android:layout_height="match_parent" android:padding="4dp">
    {fundo()}
    {corpo}
</FrameLayout>
'''

def digital(f, completo=False):
    return raiz(f'''<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical"
        android:gravity="center" android:padding="10dp">
        <LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="bottom">
            {tc('hora', fam(f), 56, 'h:mm', 'HH:mm')}
            {tc('segundos', fam(f), 22, 'ss', 'ss', 'android:layout_marginStart="4dp" android:layout_marginBottom="4dp" android:visibility="gone"')}
            {tc('ampm', famData(f), 16, 'a', '', 'android:layout_marginStart="6dp" android:layout_marginBottom="6dp"')}
        </LinearLayout>
        {tc('data', famData(f), 15, "EEE, d 'de' MMMM", "EEE, d 'de' MMMM", 'android:layout_marginTop="4dp"')}
        {extra(f)}
    </LinearLayout>''')

def empilhado(f):
    return raiz(f'''<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical"
        android:gravity="center" android:padding="8dp">
        {tc('hora', fam(f), 60, 'hh', 'HH')}
        {tc('minuto', fam(f), 60, 'mm', 'mm', 'android:layout_marginTop="-4dp"')}
        {tc('data', famData(f), 13, "EEE d MMM", "EEE d MMM", 'android:layout_marginTop="4dp"')}
        {tc('ampm', famData(f), 12, 'a', '', 'android:visibility="gone"')}
        {extra(f)}
    </LinearLayout>''')

def card(id_, f, fmt12, fmt24, cid):
    return f'''<FrameLayout android:layout_width="0dp" android:layout_height="match_parent" android:layout_weight="1">
            <ImageView android:id="@+id/{cid}" android:layout_width="match_parent" android:layout_height="match_parent"
                android:scaleType="fitXY" android:src="@drawable/fundo_card" android:importantForAccessibility="no"/>
            <LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:gravity="center">
                {tc(id_, fam(f), 54, fmt12, fmt24)}
            </LinearLayout>
            <ImageView android:id="@+id/{cid}_linha" android:layout_width="match_parent" android:layout_height="2dp" android:layout_gravity="center_vertical"
                android:src="@drawable/linha_flip" android:scaleType="fitXY" android:importantForAccessibility="no"/>
        </FrameLayout>'''

def flip(f):
    return raiz(f'''<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="10dp">
        <LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:orientation="horizontal">
            {card('hora', f, 'hh', 'HH', 'card1')}
            <View android:layout_width="8dp" android:layout_height="match_parent"/>
            {card('minuto', f, 'mm', 'mm', 'card2')}
        </LinearLayout>
        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center" android:layout_marginTop="6dp">
            {tc('data', famData(f), 13, "EEEE, d 'de' MMMM", "EEEE, d 'de' MMMM")}
            {tc('ampm', famData(f), 13, ' · a', '', 'android:visibility="gone"')}
        </LinearLayout>
        {extra(f)}
    </LinearLayout>''')

def data(f):
    return raiz(f'''<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical"
        android:gravity="center" android:padding="8dp">
        {tc('hora', famData(f) if f in DATA_ALT else fam(f), 30, 'EEEE', 'EEEE')}
        {tc('data', famData(f), 15, "d 'de' MMMM 'de' yyyy", "d 'de' MMMM 'de' yyyy", 'android:layout_marginTop="2dp"')}
        {extra(f)}
    </LinearLayout>''')

DIAIS = ['classico', 'minimal', 'moderno', 'esportivo', 'transparente', 'elegante']

def analogico(d, seg):
    s = f'android:hand_second="@drawable/ponteiro_{d}_s"' if seg else ''
    return raiz(f'''<AnalogClock android:id="@+id/analogico" android:layout_width="match_parent" android:layout_height="match_parent"
        android:layout_gravity="center" android:padding="6dp"
        android:dial="@drawable/mostrador_{d}" android:hand_hour="@drawable/ponteiro_{d}_h" android:hand_minute="@drawable/ponteiro_{d}_m" {s}/>''')

os.makedirs(RES + '/layout', exist_ok=True)
for f in FONTES:
    for tipo, fn in [('digital', digital), ('empilhado', empilhado), ('flip', flip), ('data', data)]:
        open(f'{RES}/layout/wd_{tipo}_{f}.xml', 'w').write(fn(f))
    open(f'{RES}/layout/wd_completo_{f}.xml', 'w').write(digital(f, True))
for d in DIAIS:
    for seg in (False, True):
        open(f'{RES}/layout/wa_{d}_{"s" if seg else "n"}.xml', 'w').write(analogico(d, seg))

# ---------------------------------------------------------------- mostradores e ponteiros
from PIL import Image, ImageDraw, ImageFont, ImageFilter
S = 600; C = S / 2
def novo(): return Image.new('RGBA', (S, S), (0, 0, 0, 0))
def pol(r, ang):
    a = math.radians(ang - 90)
    return (C + r * math.cos(a), C + r * math.sin(a))
def fonte(n, sz):
    caminhos = {'serif': '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf', 'sans': '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'}
    return ImageFont.truetype(caminhos.get(n, n), sz)
def texto_centro(d, xy, t, f, cor):
    b = d.textbbox((0, 0), t, font=f); w, h = b[2] - b[0], b[3] - b[1]
    d.text((xy[0] - w / 2 - b[0], xy[1] - h / 2 - b[1]), t, font=f, fill=cor)
def sombra(im, raio=10, alfa=110):
    s = Image.new('RGBA', im.size, (0, 0, 0, 0)); a = im.split()[3].point(lambda v: v * alfa // 255)
    s.putalpha(a); s = s.filter(ImageFilter.GaussianBlur(raio)); base = Image.new('RGBA', im.size, (0, 0, 0, 0))
    base.alpha_composite(s, (0, 6)); base.alpha_composite(im); return base
def ponteiro(comp, larg, cor, cauda=40, ponta='reta', centro=None, contorno=None):
    im = novo(); d = ImageDraw.Draw(im)
    if ponta == 'losango':
        pts = [(C, C - comp), (C + larg, C - comp * 0.55), (C + larg * 0.5, C + cauda), (C - larg * 0.5, C + cauda), (C - larg, C - comp * 0.55)]
        d.polygon(pts, fill=cor)
    elif ponta == 'agulha':
        d.polygon([(C, C - comp), (C + larg / 2, C + cauda), (C - larg / 2, C + cauda)], fill=cor)
    else:
        d.rounded_rectangle([C - larg / 2, C - comp, C + larg / 2, C + cauda], radius=larg / 2, fill=cor, outline=contorno, width=3 if contorno else 0)
    if centro: d.ellipse([C - centro[0], C - centro[0], C + centro[0], C + centro[0]], fill=centro[1])
    return sombra(im, 6, 120)
def salvar(im, n): im.save(f'{RES}/drawable-nodpi/{n}.png', optimize=True)

ROMANOS = ['XII', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI']
def dial_classico():
    im = novo(); d = ImageDraw.Draw(im)
    d.ellipse([10, 10, S - 10, S - 10], fill=(160, 120, 50))
    d.ellipse([26, 26, S - 26, S - 26], fill=(246, 239, 222))
    d.ellipse([40, 40, S - 40, S - 40], outline=(60, 45, 30), width=3)
    for m in range(60):
        r1 = 250 if m % 5 else 236; p1, p2 = pol(r1, m * 6), pol(258, m * 6)
        d.line([p1, p2], fill=(40, 30, 20), width=6 if m % 5 == 0 else 2)
    f = fonte('serif', 44)
    for h in range(12):
        texto_centro(d, pol(198, h * 30), ROMANOS[h], f, (40, 30, 20))
    return sombra(im, 12, 120)
def dial_minimal():
    im = novo(); d = ImageDraw.Draw(im)
    d.ellipse([12, 12, S - 12, S - 12], fill=(250, 250, 250))
    for h in range(12):
        x, y = pol(240, h * 30); r = 11 if h % 3 == 0 else 6
        d.ellipse([x - r, y - r, x + r, y + r], fill=(30, 30, 30))
    return sombra(im, 14, 100)
def dial_moderno():
    im = novo(); d = ImageDraw.Draw(im)
    d.ellipse([12, 12, S - 12, S - 12], fill=(16, 20, 28))
    d.ellipse([12, 12, S - 12, S - 12], outline=(45, 212, 191), width=5)
    for m in range(60):
        if m % 5 == 0:
            d.line([pol(208, m * 6), pol(262, m * 6)], fill=(45, 212, 191), width=10 if m % 15 == 0 else 6)
        else:
            d.line([pol(250, m * 6), pol(262, m * 6)], fill=(90, 100, 115), width=2)
    return sombra(im, 14, 140)
def dial_esportivo():
    im = novo(); d = ImageDraw.Draw(im)
    d.ellipse([10, 10, S - 10, S - 10], fill=(70, 74, 82)); d.ellipse([30, 30, S - 30, S - 30], fill=(34, 36, 42))
    for m in range(60):
        d.line([pol(242 if m % 5 else 224, m * 6), pol(262, m * 6)], fill=(220, 220, 220), width=6 if m % 5 == 0 else 2)
    f = fonte('sans', 64)
    for h, t in [(0, '12'), (3, '3'), (6, '6'), (9, '9')]:
        texto_centro(d, pol(172, h * 30), t, f, (235, 235, 235))
    return sombra(im, 14, 140)
def dial_transparente():
    im = novo(); d = ImageDraw.Draw(im)
    for m in range(60):
        if m % 5 == 0:
            d.line([pol(222, m * 6), pol(270, m * 6)], fill=(255, 255, 255), width=12 if m % 15 == 0 else 7)
    return sombra(im, 5, 200)
def dial_elegante():
    im = novo(); d = ImageDraw.Draw(im)
    d.ellipse([10, 10, S - 10, S - 10], fill=(212, 175, 55)); d.ellipse([22, 22, S - 22, S - 22], fill=(16, 30, 64))
    for h in range(12):
        a = h * 30
        if h % 3 == 0:
            p = [pol(262, a - 2.2), pol(262, a + 2.2), pol(205, a + 1.2), pol(205, a - 1.2)]
            d.polygon(p, fill=(222, 190, 90))
        else:
            d.line([pol(225, a), pol(262, a)], fill=(222, 190, 90), width=8)
    f = fonte('serif', 26)
    texto_centro(d, (C, C + 120), 'AUTOMATIC', f, (222, 190, 90))
    return sombra(im, 14, 140)
estilos = {
    'classico': (dial_classico, dict(h=(150, 16, (30, 22, 15), 'losango'), m=(220, 12, (30, 22, 15), 'losango'), s=(240, 4, (170, 25, 25), 'reta'), c=(14, (30, 22, 15)))),
    'minimal': (dial_minimal, dict(h=(140, 14, (25, 25, 25), 'reta'), m=(215, 10, (25, 25, 25), 'reta'), s=(240, 4, (230, 60, 50), 'reta'), c=(10, (230, 60, 50)))),
    'moderno': (dial_moderno, dict(h=(150, 16, (240, 240, 240), 'reta'), m=(225, 12, (240, 240, 240), 'reta'), s=(250, 4, (249, 115, 22), 'reta'), c=(12, (249, 115, 22)))),
    'esportivo': (dial_esportivo, dict(h=(150, 22, (120, 255, 170), 'agulha'), m=(225, 18, (120, 255, 170), 'agulha'), s=(245, 4, (255, 200, 40), 'reta'), c=(14, (255, 200, 40)))),
    'transparente': (dial_transparente, dict(h=(150, 16, (255, 255, 255), 'reta'), m=(225, 12, (255, 255, 255), 'reta'), s=(245, 4, (255, 80, 80), 'reta'), c=(12, (255, 255, 255)))),
    'elegante': (dial_elegante, dict(h=(150, 16, (226, 196, 100), 'losango'), m=(225, 12, (226, 196, 100), 'losango'), s=(245, 3, (226, 196, 100), 'reta'), c=(12, (226, 196, 100)))),
}
for nome, (fd, hs) in estilos.items():
    salvar(fd(), f'mostrador_{nome}')
    for k in ('h', 'm', 's'):
        comp, larg, cor, ponta = hs[k]
        salvar(ponteiro(comp, larg, cor, 50 if k == 's' else 30, ponta, hs['c'] if k == 's' else (hs['c'] if k == 'm' else None)), f'ponteiro_{nome}_{k}')
print('ok')
