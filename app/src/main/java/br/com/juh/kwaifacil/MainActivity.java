package br.com.juh.kwaifacil;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int CREAM = 0xFFFFF7ED;
    private static final int PAPER = 0xFFFFFFFF;
    private static final int INK = 0xFF42271D;
    private static final int MUTED = 0xFF82695C;
    private static final int ORANGE = 0xFFFF6B35;
    private static final int ORANGE_DARK = 0xFFE94F22;
    private static final int MINT = 0xFF168C76;
    private static final int LINE = 0xFFF0DFD0;

    LinearLayout box, list;
    TextView status;
    CheckBox automatic;
    Handler h = new Handler(Looper.getMainLooper());
    String rendered = "";
    Runnable refresh;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        QueueStore.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(CREAM);
        getWindow().setNavigationBarColor(CREAM);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CREAM);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(box);
        setContentView(scroll);

        addHeader();
        addIntro();
        addControlCard();
        addQueueCard();

        refresh = () -> {
            String s = QueueStore.notice + "\n" + Run.status;
            status.setText(s.trim().isEmpty() ? "Pronto para organizar sua fila." : s.trim());
            String key = "";
            for (QueueStore.Item x : QueueStore.items) key += x.name + x.state + x.caption;
            if (!key.equals(rendered)) {
                rendered = key;
                list.removeAllViews();
                if (QueueStore.items.isEmpty()) {
                    TextView empty = label("Os vídeos importados aparecerão aqui.", 14, MUTED, false);
                    empty.setPadding(dp(12), dp(12), dp(12), dp(8));
                    list.addView(empty);
                }
                for (int i = 0; i < QueueStore.items.size(); i++) {
                    final int n = i;
                    QueueStore.Item x = QueueStore.items.get(i);
                    Button row = styledButton(x.name + "\n" + x.state, PAPER, INK, false);
                    row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    row.setTextSize(14);
                    row.setPadding(dp(16), dp(12), dp(16), dp(12));
                    row.setBackground(background(PAPER, LINE, 16));
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
                    p.topMargin = dp(8);
                    list.addView(row, p);
                    row.setOnClickListener(v -> review(n));
                }
            }
            h.postDelayed(refresh, 750);
        };
        h.post(refresh);
    }

    private void addHeader() {
        LinearLayout card = card(ORANGE, 22);
        card.setPadding(dp(14), dp(14), dp(14), dp(16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(14);
        box.addView(card, cp);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        ImageView icon = new ImageView(this);
        int id = getResources().getIdentifier("app_mascot", "drawable", getPackageName());
        icon.setImageResource(id);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brand.addView(icon, new LinearLayout.LayoutParams(dp(104), dp(104)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        bp.leftMargin = dp(8);
        brand.addView(titleBlock, bp);
        TextView title = label("Kwai Fácil", 27, Color.WHITE, true);
        titleBlock.addView(title);
        TextView sub = label("Sua fila de vídeos", 14, 0xFFFFF0E7, false);
        sub.setPadding(0, dp(3), 0, 0);
        titleBlock.addView(sub);

        TextView tagline = label("Publicação acompanhada, com carinho e controle", 13, 0xFFFFF0E7, false);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(6), 0, 0);
        card.addView(tagline);
    }

    private void addIntro() {
        LinearLayout info = card(PAPER, 18);
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView title = label("Antes de começar", 17, INK, true);
        info.addView(title);
        TextView details = label("Use arquivos com o mesmo nome: 001.mp4 + 001.txt. Legendas em UTF-8; .TXT também funciona. Opcional: CAPA_SEGUNDOS=3 no TXT. Mantenha o celular desbloqueado e confira o primeiro envio.", 14, MUTED, false);
        details.setPadding(0, dp(5), 0, 0);
        info.addView(details);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(16);
        box.addView(info, p);
    }

    private void addControlCard() {
        LinearLayout card = card(PAPER, 18);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView heading = label("Montar sua fila", 18, INK, true);
        card.addView(heading);

        addButton(card, "Escolher pasta de vídeos", ORANGE, Color.WHITE, true, () -> {
            if (QueueStore.running || QueueStore.importing) { toast("Pause a fila ou aguarde a leitura."); return; }
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 11);
        });
        addButton(card, "Ativar assistente de publicação", 0xFFFFF1E7, ORANGE_DARK, false, () -> new AlertDialog.Builder(this)
            .setTitle("Assistente de acessibilidade")
            .setMessage("Durante a fila iniciada por você, o assistente lê as telas do Kwai e executa os passos de publicação. Vídeos e legendas só são enviados ao Kwai. Você pode pausar ou desativar o serviço.")
            .setPositiveButton("Abrir configurações", (d,w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("Cancelar", null).show());

        automatic = new CheckBox(this);
        automatic.setButtonTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        automatic.setTextColor(INK);
        automatic.setTextSize(14);
        automatic.setText("Publicar a fila automaticamente");
        automatic.setPadding(0, dp(4), 0, dp(4));
        card.addView(automatic);

        addButton(card, "Iniciar / continuar fila", MINT, Color.WHITE, true, () -> begin());
        addButton(card, "Pausar / parar", 0xFFFFF1E7, ORANGE_DARK, false, () -> QueueStore.pause("Fila pausada. Revise o item em andamento antes de continuar."));

        TextView simulate = label("Simular leitura de legenda", 12, MUTED, false);
        simulate.setGravity(Gravity.END);
        simulate.setPadding(dp(8), dp(12), dp(8), dp(12));
        simulate.setOnClickListener(v -> testLastVideo());
        simulate.setContentDescription("Simular leitura do vídeo mais recente, sem publicar");
        card.addView(simulate, new LinearLayout.LayoutParams(-1, -2));

        TextView diagnostic = label("Diagnóstico da tela", 12, MUTED, false);
        diagnostic.setGravity(Gravity.END);
        diagnostic.setPadding(dp(8), dp(12), dp(8), dp(12));
        diagnostic.setOnClickListener(v -> diagnosticMenu());
        card.addView(diagnostic, new LinearLayout.LayoutParams(-1, -2));

        status = label("Pronto para organizar sua fila.", 14, INK, false);
        status.setPadding(dp(13), dp(11), dp(13), dp(11));
        status.setBackground(background(0xFFFFF8EF, LINE, 12));
        card.addView(status);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(16);
        box.addView(card, p);
    }

    private void addQueueCard() {
        LinearLayout card = card(PAPER, 18);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView heading = label("Fila de publicação", 18, INK, true);
        card.addView(heading);
        TextView hint = label("Toque em um vídeo para revisar os detalhes.", 13, MUTED, false);
        hint.setPadding(0, dp(3), 0, dp(4));
        card.addView(hint);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        card.addView(list);
        box.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout card(int color, int radius) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(background(color, color == PAPER ? LINE : color, radius));
        return v;
    }

    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != fill) d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView label(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setLineSpacing(dp(2), 1f);
        return v;
    }

    private Button styledButton(String text, int fill, int color, boolean bold) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(color);
        b.setTextSize(15);
        if (bold) b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(50));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackground(background(fill, fill, 15));
        return b;
    }

    private void addButton(LinearLayout parent, String text, int fill, int color, boolean bold, Runnable action) {
        Button b = styledButton(text, fill, color, bold);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(9);
        parent.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void review(int i) {
        QueueStore.Item x = QueueStore.items.get(i);
        AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(x.name).setMessage(x.state + "\n\n" + x.caption).setNegativeButton("Fechar", null);
        if (!QueueStore.running && x.state.equals("Precisa de atenção")) {
            d.setPositiveButton("Conferi no perfil: publicou", (a,w) -> { x.state = "Publicado"; QueueStore.save(); })
             .setNeutralButton("Conferi: não publicou", (a,w) -> new AlertDialog.Builder(this)
                .setMessage("Só tente novamente depois de conferir o perfil do Kwai. Isso evita publicar o mesmo vídeo duas vezes.")
                .setPositiveButton("Tentar novamente", (a2,w2) -> { x.state = "Pendente"; x.confirmationRetries = 0; QueueStore.save(); })
                .setNegativeButton("Cancelar", null).show());
        }
        d.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if(getSharedPreferences("diagnostic",0).getBoolean("show",false)) {
            getSharedPreferences("diagnostic",0).edit().putBoolean("show",false).apply();
            h.post(() -> showDiagnostic());
        }
    }

    void diagnosticMenu() {
        new AlertDialog.Builder(this).setTitle("Diagnóstico da tela")
            .setItems(new String[]{"Capturar ANTES de tocar em Mais", "Capturar DEPOIS de tocar em Mais", "Ver / copiar relatórios"}, (d,which) -> {
                if(which==2){showDiagnostic();return;}
                KwaiService service=KwaiService.instance;
                if(service==null){toast("Ative a acessibilidade primeiro.");return;}
                if(Run.active||QueueStore.running){toast("Pause a fila e a simulação antes do diagnóstico.");return;}
                if(service.diagnosticPending){toast("Já existe uma captura aguardando.");return;}
                final String phase=which==0?"ANTES":"DEPOIS";
                new AlertDialog.Builder(this).setTitle("Capturar "+phase)
                    .setMessage("Ao iniciar, você terá 10 segundos para abrir o vídeo no Kwai. "+
                        (which==0?"Deixe a legenda cortada, com Mais aparecendo.":"Toque manualmente em Mais e deixe a legenda inteira aberta.")+
                        " Não haverá cliques automáticos. O relatório inclui os textos dessa tela e só é compartilhado quando você escolher.")
                    .setPositiveButton("Iniciar 10 segundos", (a,w) -> {
                        service.scheduleDiagnostic(phase);
                        moveTaskToBack(true);
                    }).setNegativeButton("Cancelar",null).show();
            }).setNegativeButton("Fechar",null).show();
    }

    void showDiagnostic() {
        android.content.SharedPreferences prefs=getSharedPreferences("diagnostic",0);
        String before=prefs.getString("ANTES","");
        String after=prefs.getString("DEPOIS","");
        String error=prefs.getString("error","");
        final String report=(before+"\n\n"+after).trim();
        if(report.isEmpty()){new AlertDialog.Builder(this).setTitle("Diagnóstico").setMessage(error.isEmpty()?"Nenhum relatório capturado. Use as opções Antes e Depois.":error).setPositiveButton("Fechar",null).show();return;}
        TextView text=label((error.isEmpty()?"":error+"\n\n")+report,12,INK,false);
        text.setTextIsSelectable(true);
        text.setPadding(dp(14),dp(12),dp(14),dp(12));
        ScrollView scroll=new ScrollView(this);scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("Relatório: Antes / Depois").setView(scroll)
            .setPositiveButton("Copiar", (d,w) -> {
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Diagnóstico Kwai",report));toast("Relatório copiado.");
            }).setNeutralButton("Compartilhar", (d,w) -> {
                Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report);
                startActivity(Intent.createChooser(send,"Compartilhar diagnóstico"));
            }).setNegativeButton("Fechar",null).show();
    }

    void testLastVideo() {
        if (QueueStore.running || Run.active || (KwaiService.instance != null && KwaiService.instance.diagnosticPending)) { toast("Pause a fila ou aguarde o diagnóstico antes do teste."); return; }
        if (KwaiService.instance == null) { toast("Ative a acessibilidade primeiro."); return; }
        ArrayList<String> packages = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        Intent probe = new Intent(Intent.ACTION_SEND).setType("video/*");
        for (ResolveInfo r : getPackageManager().queryIntentActivities(probe, PackageManager.MATCH_ALL)) {
            String pkg = r.activityInfo.packageName;
            if ((pkg.equals("com.kwai.video") || pkg.equals("com.smile.gifmaker")) && !packages.contains(pkg)) {
                packages.add(pkg); names.add(r.loadLabel(getPackageManager()).toString());
            }
        }
        if (packages.isEmpty()) { toast("Kwai não encontrado."); return; }
        new AlertDialog.Builder(this).setTitle("Testar leitura no Kwai")
            .setItems(names.toArray(new String[0]), (d,i) -> {
                String pkg = packages.get(i);
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch == null) return;
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                KwaiService.instance.startReadingTest(pkg);
                startActivity(launch);
            }).setNegativeButton("Cancelar", null).show();
    }

    void begin() {
        if (KwaiService.instance != null && KwaiService.instance.diagnosticPending) { toast("Aguarde o diagnóstico terminar."); return; }
        if (QueueStore.importing) { toast("Aguarde a leitura da pasta."); return; }
        if (QueueStore.running) { toast("Fila já está em andamento."); return; }
        if (KwaiService.instance == null) { toast("Ative a acessibilidade primeiro."); return; }
        if (QueueStore.uncertain()) { toast("Revise o item que precisa de atenção antes de continuar."); return; }
        if (QueueStore.pending() < 0) { toast("Não há vídeos pendentes com legenda."); return; }

        Intent probe = new Intent(Intent.ACTION_SEND).setType("video/*");
        ArrayList<ResolveInfo> choices = new ArrayList<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(probe, PackageManager.MATCH_ALL)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals("com.kwai.video") || pkg.equals("com.smile.gifmaker") || r.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).contains("kwai")) choices.add(r);
        }
        if (choices.isEmpty()) { toast("Kwai não encontrado para receber vídeo."); return; }
        String[] names = new String[choices.size()];
        for (int i = 0; i < names.length; i++) names[i] = choices.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("Iniciar fila no Kwai").setItems(names, (d,i) -> {
            ResolveInfo r = choices.get(i);
            QueueStore.destination = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
            Run.auto = automatic.isChecked();
            QueueStore.running = true;
            QueueStore.launch(this);
        }).setNegativeButton("Cancelar", null).show();
    }

    @Override protected void onActivityResult(int req, int result, Intent d) {
        super.onActivityResult(req, result, d);
        if (req == 11 && result == RESULT_OK && d != null) {
            Uri tree = d.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                QueueStore.importing = true;
                QueueStore.notice = "Lendo pasta…";
                new Thread(() -> {
                    try { QueueStore.scan(tree); }
                    catch (Exception e) { QueueStore.notice = "Não foi possível ler: " + e.getMessage(); }
                    finally { QueueStore.importing = false; }
                }).start();
            } catch (Exception e) { toast("Não foi possível autorizar a pasta."); }
        }
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
