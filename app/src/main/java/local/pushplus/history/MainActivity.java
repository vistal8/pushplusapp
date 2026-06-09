package local.pushplus.history;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.content.res.Configuration;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String PREFS = "pushplus_native";
    static final String KEY_USER_TOKEN = "user_token";
    static final String KEY_SECRET_KEY = "secret_key";
    static final String KEY_AUTO_SYNC = "auto_sync";
    static final String KEY_THEME = "theme_color";
    static final String KEY_LANGUAGE = "language";
    static final String LANG_SYSTEM = "system";
    static final String LANG_ZH_CN = "zh_CN";
    static final String BASE_URL = "https://www.pushplus.plus";
    static final String NOTIFICATION_CHANNEL_ID = "pushplus_messages";
    private static final int REQ_EXPORT = 11;
    private static final int REQ_IMPORT = 12;
    private static final int REQ_NOTIFICATIONS = 13;
    private static final int NOTIFICATION_ID_NEW_MESSAGES = 9001;
    private static final int DEFAULT_THEME = Color.rgb(70, 21, 153);

    private SharedPreferences prefs;
    private MessageDb db;
    private EditText keywordInput;
    private TextView statusText;
    private ArrayAdapter<Message> adapter;
    private final List<Message> messages = new ArrayList<Message>();
    private float pullStartY = -1f;
    private boolean pullArmed = false;
    private boolean showingDetail = false;
    private boolean showingMine = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(applyLanguage(newBase));
    }

    private static Context applyLanguage(Context context) {
        Locale locale = Locale.SIMPLIFIED_CHINESE;
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        db = new MessageDb(this);
        requestNotificationPermissionIfNeeded();
        if (hasKeys()) {
            showHome();
        } else {
            showSetup();
        }
    }

    private boolean hasKeys() {
        return prefs.getString(KEY_USER_TOKEN, "").length() > 0 && prefs.getString(KEY_SECRET_KEY, "").length() > 0;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATIONS);
        }
    }

    private void showSetup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView logo = new TextView(this);
        logo.setText("pushplus");
        logo.setTextSize(40);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextColor(themeColor());
        root.addView(logo);

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.setup_subtitle));
        sub.setTextSize(18);
        sub.setTextColor(Color.rgb(90, 90, 90));
        root.addView(sub);

        final EditText token = input(getString(R.string.hint_user_token));
        final EditText secret = input(getString(R.string.hint_secret_key));
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(token);
        root.addView(secret);

        Button save = button(getString(R.string.save_config));
        root.addView(save, fullButtonParams());
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String t = token.getText().toString().trim();
                String s = secret.getText().toString().trim();
                if (t.length() == 0 || s.length() == 0) {
                    toast(getString(R.string.need_token_secret));
                    return;
                }
                prefs.edit().putString(KEY_USER_TOKEN, t).putString(KEY_SECRET_KEY, s).apply();
                toast(getString(R.string.config_saved));
                showHome();
            }
        });
        setContentView(root);
    }

    private void showHome() {
        showingDetail = false;
        showingMine = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(249, 249, 251));
        styleStatusBar(Color.rgb(249, 249, 251));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bar.setBackgroundColor(Color.WHITE);
        TextView menu = new TextView(this);
        menu.setText("☰");
        menu.setTextSize(24);
        menu.setGravity(Gravity.CENTER);
        menu.setTextColor(themeColor());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = new TextView(this);
        title.setText("消息");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(themeColor());
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(8));

        keywordInput = input(getString(R.string.hint_search));
        keywordInput.setBackground(rounded(Color.rgb(243, 243, 245), dp(12), Color.rgb(203, 195, 213), 1));
        keywordInput.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0);
        keywordInput.setCompoundDrawablePadding(dp(8));
        panel.addView(keywordInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        keywordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshList(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(115, 115, 115));
        statusText.setPadding(dp(2), dp(4), dp(2), dp(4));
        panel.addView(statusText);
        root.addView(panel);

        adapter = new ArrayAdapter<Message>(this, android.R.layout.simple_list_item_2, android.R.id.text1, messages) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                Message m = getItem(position);
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.TOP);
                card.setPadding(dp(16), dp(16), dp(16), dp(16));
                card.setBackground(rounded(Color.WHITE, dp(12), Color.rgb(226, 226, 228), 1));
                AbsListView.LayoutParams rowParams = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(92));
                card.setLayoutParams(rowParams);
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.LEFT);
                LinearLayout titleRow = new LinearLayout(MainActivity.this);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);
                TextView t1 = new TextView(MainActivity.this);
                TextView time = new TextView(MainActivity.this);
                TextView t2 = new TextView(MainActivity.this);
                TextView t3 = new TextView(MainActivity.this);
                if (m != null) {
                    String titleText = m.title.length() == 0 ? getString(R.string.no_title) : m.title;
                    String preview = listPreviewFor(m);
                    String code = extractVerificationCode(preview);
                    t1.setText(clip(titleText, 24));
                    t1.setTextSize(17);
                    t1.setTypeface(Typeface.DEFAULT_BOLD);
                    t1.setSingleLine(true);
                    t1.setTextColor(Color.rgb(28, 30, 36));
                    time.setText(shortTime(m.updateTime));
                    time.setTextSize(12);
                    time.setSingleLine(true);
                    time.setGravity(Gravity.RIGHT);
                    time.setTextColor(Color.rgb(73, 68, 83));
                    t2.setText(code.length() > 0 ? "验证码 " + code : clip(preview, 42));
                    t2.setTextSize(13);
                    t2.setSingleLine(true);
                    t2.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                    t2.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    t2.setPadding(dp(6), dp(3), dp(6), dp(3));
                    t2.setBackground(rounded(Color.rgb(238, 238, 240), dp(4), Color.rgb(238, 238, 240), 0));
                    t2.setTextColor(code.length() > 0 ? themeColor() : Color.rgb(96, 93, 105));
                    if (code.length() > 0) t2.setTypeface(Typeface.DEFAULT_BOLD);
                    t3.setText(m.updateTime);
                    t3.setTextSize(12);
                    t3.setSingleLine(true);
                    t3.setTextColor(Color.rgb(154, 158, 168));
                }
                titleRow.addView(t1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                titleRow.addView(time, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
                texts.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                texts.addView(t2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                t3.setVisibility(View.GONE);
                card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setPadding(dp(12), dp(6), dp(12), dp(6));
                wrap.addView(card);
                return wrap;
            }
        };
        ListView listView = new ListView(this);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setSelector(android.R.color.transparent);
        listView.setAdapter(adapter);
        listView.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                return handlePullRefresh((ListView) v, event);
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) { showMessage(messages.get(position)); }
        });
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView tabMessages = tab(getString(R.string.tab_messages), true);
        TextView tabMine = tab(getString(R.string.tab_mine), false);
        tabs.addView(tabMessages, weightParams());
        tabs.addView(tabMine, weightParams());
        root.addView(tabs);
        setContentView(root);

        tabMine.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showMinePage(); } });
        refreshList();
    }

    private TextView tab(String text, boolean active) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(16);
        t.setTextColor(active ? themeColor() : Color.rgb(85, 85, 85));
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (active) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setBackground(rounded(Color.rgb(232, 218, 246), dp(28), Color.rgb(232, 218, 246), 0));
        }
        return t;
    }

    private TextView chip(String text, boolean active) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setTextColor(active ? Color.WHITE : Color.rgb(73, 68, 83));
        int bg = active ? themeColor() : Color.rgb(232, 232, 234);
        chip.setBackground(rounded(bg, dp(20), bg, 0));
        return chip;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(8), 0);
        return p;
    }

    private String shortTime(String time) {
        if (time == null || time.length() == 0) return "";
        if (time.length() >= 16) return time.substring(11, 16);
        return time;
    }

    private void showKeyDialog() {
        showKeyDialog(false);
    }

    private void showKeyDialog(final boolean returnMine) {
        final EditText token = input(getString(R.string.hint_user_token));
        final EditText secret = input(getString(R.string.hint_secret_key));
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(prefs.getString(KEY_USER_TOKEN, ""));
        secret.setText(prefs.getString(KEY_SECRET_KEY, ""));
        final CheckBox autoSync = new CheckBox(this);
        autoSync.setText(prefs.getBoolean(KEY_AUTO_SYNC, false) ? getString(R.string.auto_sync_on) : getString(R.string.auto_sync_off));
        autoSync.setChecked(prefs.getBoolean(KEY_AUTO_SYNC, false));
        autoSync.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                autoSync.setText(autoSync.isChecked() ? getString(R.string.auto_sync_on) : getString(R.string.auto_sync_off));
            }
        });

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), 0, dp(12), 0);
        box.addView(token);
        box.addView(secret);
        box.addView(autoSync);
        new AlertDialog.Builder(this).setTitle(getString(R.string.key_settings)).setView(box)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save_config), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        prefs.edit()
                                .putString(KEY_USER_TOKEN, token.getText().toString().trim())
                                .putString(KEY_SECRET_KEY, secret.getText().toString().trim())
                                .putBoolean(KEY_AUTO_SYNC, autoSync.isChecked())
                                .apply();
                        if (autoSync.isChecked()) AutoSync.schedule(MainActivity.this); else AutoSync.cancel(MainActivity.this);
                        toast(getString(R.string.config_saved));
                        if (returnMine) showMinePage();
                    }
                }).show();
    }

    private void showMinePage() {
        showingMine = true;
        showingDetail = false;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 248, 248));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(themeColor());
        TextView title = new TextView(this);
        title.setText(getString(R.string.tab_settings));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(16), dp(12), dp(12));
        LinearLayout statusCard = statusCard(getString(R.string.local_count_prefix) + db.count());
        body.addView(statusCard, fullWrapParams(0, 0, 0, dp(14)));
        statusText = (TextView) statusCard.findViewWithTag("status_text");

        LinearLayout keySettings = settingsRow("▣", getString(R.string.key_settings), Color.rgb(220, 210, 235));
        LinearLayout syncAll = settingsRow("↻", getString(R.string.sync_all), Color.rgb(190, 232, 218));
        body.addView(keySettings, rowParams(0, 0, 0, dp(12)));
        body.addView(syncAll, rowParams(0, 0, 0, dp(14)));

        LinearLayout twoCol = new LinearLayout(this);
        twoCol.setOrientation(LinearLayout.HORIZONTAL);
        TextView skin = squareSetting("🎨", getString(R.string.skin_switch));
        TextView language = squareSetting("文", getString(R.string.language_settings));
        twoCol.addView(skin, squareParams());
        twoCol.addView(language, squareParams());
        body.addView(twoCol, fullWrapParams(0, 0, 0, dp(14)));

        TextView dataTitle = sectionTitle("数据管理");
        body.addView(dataTitle, fullWrapParams(0, 0, 0, dp(8)));
        LinearLayout exportDb = settingsRow("▤", getString(R.string.export_db), Color.rgb(248, 218, 218));
        LinearLayout importDb = settingsRow("▣", getString(R.string.import_db), Color.rgb(222, 210, 250));
        body.addView(exportDb, rowParams(0, 0, 0, dp(8)));
        body.addView(importDb, rowParams(0, 0, 0, dp(8)));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, dp(8), 0, dp(8));
        TextView tabMessages = tab(getString(R.string.tab_messages), false);
        TextView tabMine = tab(getString(R.string.tab_settings), true);
        tabs.addView(tabMessages, weightParams());
        tabs.addView(tabMine, weightParams());
        root.addView(tabs);
        setContentView(root);
        tabMessages.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showHome(); } });
        keySettings.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showKeyDialog(true); } });
        syncAll.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startSync(true); } });
        skin.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSkinDialog(); } });
        language.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showLanguageDialog(); } });
        exportDb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { exportDatabase(); } });
        importDb.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { importDatabase(); } });
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setPadding(dp(10), 0, dp(10), 0);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(55, 55, 55));
        b.setBackground(rounded(Color.WHITE, dp(12), Color.rgb(226, 226, 232), 1));
        return b;
    }

    private LinearLayout statusCard(String text) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(16));
        card.setBackground(rounded(Color.WHITE, dp(12), Color.rgb(232, 232, 238), 1));
        TextView label = new TextView(this);
        label.setText("DATABASE STATUS");
        label.setTextSize(11);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(Color.rgb(120, 118, 130));
        TextView value = new TextView(this);
        value.setTag("status_text");
        value.setText(text);
        value.setTextSize(20);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(Color.rgb(36, 34, 44));
        View line = new View(this);
        line.setBackgroundColor(themeColor());
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        lineParams.setMargins(0, dp(14), dp(96), 0);
        card.addView(label);
        card.addView(value);
        card.addView(line, lineParams);
        return card;
    }

    private LinearLayout settingsRow(String icon, String text, int iconColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setBackground(rounded(Color.WHITE, dp(12), Color.rgb(232, 232, 238), 1));
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(18);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextColor(themeColor());
        iconView.setBackground(rounded(iconColor, dp(8), iconColor, 0));
        row.addView(iconView, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(Color.rgb(52, 50, 60));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        labelParams.setMargins(dp(14), 0, dp(8), 0);
        row.addView(label, labelParams);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(Color.rgb(120, 118, 130));
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView squareSetting(String icon, String text) {
        TextView card = new TextView(this);
        card.setText(icon + "\n\n" + text);
        card.setTextSize(14);
        card.setGravity(Gravity.CENTER);
        card.setTextColor(Color.rgb(56, 54, 64));
        card.setBackground(rounded(Color.WHITE, dp(10), Color.rgb(232, 232, 238), 1));
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(86, 84, 96));
        title.setPadding(dp(2), 0, 0, 0);
        return title;
    }

    private LinearLayout.LayoutParams fullWrapParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private LinearLayout.LayoutParams rowParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void styleStatusBar(int color) {
        if (Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(color);
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", text));
        toast("已复制");
    }

    private int themeColor() {
        return prefs == null ? DEFAULT_THEME : prefs.getInt(KEY_THEME, DEFAULT_THEME);
    }

    private void showSkinDialog() {
        final int[] colors = new int[] { Color.rgb(196, 0, 190), Color.rgb(0, 145, 234), Color.rgb(0, 150, 136), Color.rgb(244, 81, 30), Color.rgb(63, 81, 181) };
        final String[] names = new String[] { getString(R.string.theme_purple), getString(R.string.theme_blue), getString(R.string.theme_teal), getString(R.string.theme_orange), getString(R.string.theme_indigo) };
        new AlertDialog.Builder(this).setTitle(getString(R.string.theme_settings)).setItems(names, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                prefs.edit().putInt(KEY_THEME, colors[which]).apply();
                showMinePage();
            }
        }).show();
    }

    private void showLanguageDialog() {
        final String[] values = new String[] { LANG_SYSTEM, LANG_ZH_CN };
        final String[] labels = new String[] { getString(R.string.language_system), getString(R.string.language_zh_cn) };
        String current = prefs.getString(KEY_LANGUAGE, LANG_SYSTEM);
        int checked = LANG_ZH_CN.equals(current) ? 1 : 0;
        new AlertDialog.Builder(this).setTitle(getString(R.string.language_settings)).setSingleChoiceItems(labels, checked, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                prefs.edit().putString(KEY_LANGUAGE, values[which]).apply();
                d.dismiss();
                recreate();
            }
        }).show();
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        p.setMargins(dp(3), dp(4), dp(3), dp(4));
        return p;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        p.setMargins(0, dp(16), 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams squareParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(104), 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String extractVerificationCode(String text) {
        if (text == null) return "";
        Matcher preferred = Pattern.compile("(?i)(?:验证码|校验码|动态码|code)[^0-9A-Za-z]{0,8}([0-9A-Za-z]{4,8})").matcher(text);
        if (preferred.find()) return preferred.group(1);
        return "";
    }

    private static String listPreviewFor(Message message) {
        String preview = displayContentFor(message);
        for (int i = 0; i < 4; i++) {
            String before = preview;
            preview = removeLeadingDateTimePrefix(preview);
            preview = preview.replaceFirst("^\\s*[|｜:：,，;；.。\\-—_]+\\s*", "").trim();
            if (preview.equals(before)) break;
        }
        return removeAllDateTimesForPreview(preview);
    }

    private static String removeAllDateTimesForPreview(String text) {
        if (text == null || text.length() == 0) return "";
        String out = text.replaceAll("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", " ");
        out = out.replaceAll("\\s*[|｜:：,，;；.。\\-—_]+\\s*", " ");
        out = out.replaceAll("[\\u00A0\\u1680\\u180E\\u2000-\\u200F\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]", " ");
        out = out.replaceAll("\\s+", " ").trim();
        return stripPreviewLeadingSymbols(out);
    }

    private static String stripPreviewLeadingSymbols(String text) {
        if (text == null || text.length() == 0) return "";
        String out = text.replaceFirst("^[\\s\\u00A0\\u3000|｜:：,，;；.。\\-—_]+", "").trim();
        while (out.length() > 0) {
            int cp = out.codePointAt(0);
            if (isPreviewTextStart(cp)) break;
            out = out.substring(Character.charCount(cp)).replaceFirst("^[\\s\\u00A0\\u3000|｜:：,，;；.。\\-—_]+", "").trim();
        }
        return out;
    }

    private static boolean isPreviewTextStart(int cp) {
        if (cp >= '0' && cp <= '9') return true;
        if (cp >= 'A' && cp <= 'Z') return true;
        if (cp >= 'a' && cp <= 'z') return true;
        if (cp >= 0x4E00 && cp <= 0x9FFF) return true;
        return cp == '【' || cp == '[' || cp == '(' || cp == '（' || cp == '<' || cp == '《';
    }

    private static String detailBodyFor(Message message) {
        return removeStandaloneDateTimeLines(displayContentFor(message));
    }

    private static String removeStandaloneDateTimeLines(String text) {
        if (text == null || text.length() == 0) return "";
        String[] lines = text.split("\\n");
        StringBuilder out = new StringBuilder();
        boolean previousBlank = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) {
                if (!previousBlank) {
                    out.append('\n');
                    previousBlank = true;
                }
                continue;
            }
            if (isDateTimeLine(line)) continue;
            if (out.length() > 0 && !previousBlank) out.append('\n');
            out.append(line);
            previousBlank = false;
        }
        return out.toString().trim();
    }

    private String sourceOf(Message m) {
        if (m.topicName.length() > 0) return m.topicName;
        if (m.channel.length() > 0) return m.channel;
        return "PushPlus";
    }

    private void startSync(boolean all) {
        String token = prefs.getString(KEY_USER_TOKEN, "");
        String secret = prefs.getString(KEY_SECRET_KEY, "");
        if (token.length() == 0 || secret.length() == 0) { showSetup(); return; }
        new SyncTask(token, secret, all).execute();
    }

    private void refreshList() {
        messages.clear();
        messages.addAll(db.search(keywordInput == null ? "" : keywordInput.getText().toString().trim(), 200));
        adapter.notifyDataSetChanged();
        statusText.setText(getString(R.string.local_count_prefix) + db.count());
    }

    private boolean handlePullRefresh(ListView listView, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            pullStartY = event.getY();
            pullArmed = false;
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && isListAtTop(listView) && pullStartY >= 0) {
            float distance = event.getY() - pullStartY;
            if (distance > dp(90)) {
                pullArmed = true;
                statusText.setText("松开刷新");
            } else if (distance > dp(28)) {
                statusText.setText("下拉刷新");
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            boolean shouldRefresh = pullArmed && isListAtTop(listView);
            pullStartY = -1f;
            pullArmed = false;
            if (shouldRefresh) {
                statusText.setText("正在刷新...");
                startSync(false);
            }
        }
        return false;
    }

    private boolean isListAtTop(ListView listView) {
        if (listView.getChildCount() == 0) return true;
        return listView.getFirstVisiblePosition() == 0 && listView.getChildAt(0).getTop() >= listView.getPaddingTop();
    }

    private void showMessage(Message m) {
        showingDetail = true;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 246, 249));
        styleStatusBar(themeColor());

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(themeColor());

        TextView back = new TextView(this);
        back.setText("<");
        back.setTextSize(30);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(Color.WHITE);
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText(getString(R.string.message_detail));
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(body);

        final String bodyText = detailBodyFor(m);
        final String code = extractVerificationCode(bodyText);
        if (code.length() == 0) {
            TextView titleCard = new TextView(this);
            titleCard.setText(m.title.length() == 0 ? getString(R.string.no_title) : m.title);
            titleCard.setTextSize(18);
            titleCard.setTypeface(Typeface.DEFAULT_BOLD);
            titleCard.setTextColor(Color.rgb(32, 34, 40));
            titleCard.setGravity(Gravity.CENTER);
            titleCard.setPadding(dp(14), dp(12), dp(14), dp(12));
            titleCard.setBackground(rounded(Color.WHITE, dp(16), Color.rgb(226, 226, 232), 1));
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.setMargins(0, 0, 0, dp(12));
            body.addView(titleCard, titleParams);
        }
        if (code.length() > 0) {
            TextView codeView = new TextView(this);
            codeView.setText("验证码 " + code);
            codeView.setTextSize(22);
            codeView.setTypeface(Typeface.DEFAULT_BOLD);
            codeView.setTextColor(themeColor());
            codeView.setGravity(Gravity.CENTER);
            codeView.setPadding(dp(14), dp(12), dp(14), dp(12));
            codeView.setBackground(rounded(Color.WHITE, dp(16), Color.rgb(226, 226, 232), 1));
            LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            codeParams.setMargins(0, 0, 0, dp(12));
            body.addView(codeView, codeParams);
            codeView.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copyText(code); } });
        }
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(16), dp(14), dp(16), dp(14));
        bubble.setBackground(rounded(Color.WHITE, dp(18), Color.rgb(226, 226, 232), 1));
        TextView time = new TextView(this);
        time.setText(m.updateTime.length() > 0 ? "时间：" + m.updateTime : "");
        time.setTextSize(12);
        time.setTextColor(Color.rgb(145, 148, 158));
        time.setPadding(0, 0, 0, m.updateTime.length() > 0 ? dp(10) : 0);
        bubble.addView(time);
        TextView content = new TextView(this);
        content.setText(bodyText);
        content.setTextSize(16);
        content.setTextColor(Color.rgb(36, 38, 44));
        content.setLineSpacing(dp(3), 1.12f);
        content.setTextIsSelectable(true);
        content.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                copyText(bodyText);
                return true;
            }
        });
        bubble.addView(content);
        body.addView(bubble);

        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showHome(); } });
    }

    private void exportDatabase() {
        String name = "pushplus-history-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".db";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importDatabase() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (requestCode == REQ_EXPORT) { copyFile(dbPath(), data.getData(), true); toast(getString(R.string.db_exported)); }
            else if (requestCode == REQ_IMPORT) { db.close(); copyFile(dbPath(), data.getData(), false); db = new MessageDb(this); refreshList(); toast(getString(R.string.db_imported)); }
        } catch (Exception e) { toast(getString(R.string.db_operation_failed, e.getMessage())); }
    }

    private File dbPath() { return getDatabasePath(MessageDb.DB_NAME); }
    private void copyFile(File dbFile, Uri uri, boolean export) throws Exception {
        InputStream in = export ? new FileInputStream(dbFile) : getContentResolver().openInputStream(uri);
        OutputStream out = export ? getContentResolver().openOutputStream(uri) : new FileOutputStream(dbFile, false);
        try { copy(in, out); } finally { if (in != null) in.close(); if (out != null) out.close(); }
    }
    static void copy(InputStream in, OutputStream out) throws Exception { byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n); }

    private class SyncTask extends AsyncTask<Void, String, String> {
        private final String token; private final String secret; private final boolean all; private int saved; private int fetched;
        SyncTask(String token, String secret, boolean all) { this.token = token; this.secret = secret; this.all = all; }
        @Override protected String doInBackground(Void... voids) { try { SyncResult r = syncMessages(MainActivity.this, token, secret, all, new Progress() { @Override public void onPage(int page, int pages) { publishProgress(getString(R.string.syncing_page, page, pages)); } }); fetched = r.fetched; saved = r.saved; return null; } catch (Exception e) { return e.getMessage(); } }
        @Override protected void onProgressUpdate(String... values) { statusText.setText(values[0]); }
        @Override protected void onPostExecute(String error) { if (error == null) { statusText.setText(getString(R.string.sync_done, fetched, saved)); refreshList(); } else statusText.setText(getString(R.string.sync_failed, error)); }
    }

    interface Progress { void onPage(int page, int pages); }
    static class SyncResult { int fetched; int saved; int newMessages; Message latestNew; }

    static SyncResult syncMessages(Context context, String token, String secret, boolean all, Progress progress) throws Exception {
        SyncResult result = new SyncResult();
        String accessKey = getAccessKey(token, secret);
        MessageDb db = new MessageDb(context);
        int page = 1; int pages = 1; int pageSize = 20;
        do {
            if (progress != null) progress.onPage(page, pages);
            JSONObject req = new JSONObject().put("current", page).put("pageSize", pageSize);
            JSONObject pageData = postJson(BASE_URL + "/api/open/message/list", req, accessKey);
            JSONObject data = pageData.optJSONObject("data"); if (data == null) data = pageData;
            pages = Math.max(1, data.optInt("pages", pages));
            JSONArray list = data.optJSONArray("list"); if (list == null || list.length() == 0) break;
            result.fetched += list.length();
            for (int i = 0; i < list.length(); i++) {
                Message message = normalize(list.getJSONObject(i));
                if (message.shortCode.length() > 0) {
                    String detailText = fetchMessageDetailText(message.shortCode);
                    if (detailText.length() > 0) message.contentText = detailText;
                }
                boolean inserted = db.save(message);
                result.saved++;
                if (inserted) {
                    result.newMessages++;
                    if (result.latestNew == null) result.latestNew = message;
                }
            }
            page++;
        } while (all && page <= pages);
        db.close();
        if (result.newMessages > 0) notifyNewMessages(context, result);
        return result;
    }

    static String getAccessKey(String token, String secret) throws Exception {
        JSONObject payload = new JSONObject().put("token", token).put("secretKey", secret);
        JSONObject res = postJson(BASE_URL + "/api/common/openApi/getAccessKey", payload, null);
        if (res.optInt("code") != 200) throw new Exception(res.optString("msg", "getAccessKey failed"));
        JSONObject data = res.optJSONObject("data"); String accessKey = data == null ? "" : data.optString("accessKey", "");
        if (accessKey.length() == 0) throw new Exception("accessKey is empty"); return accessKey;
    }

    static JSONObject postJson(String url, JSONObject payload, String accessKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000); conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "application/json"); conn.setRequestProperty("Content-Type", "application/json; charset=utf-8"); conn.setRequestProperty("User-Agent", "PushPlusHistoryAndroid/1.0");
        if (accessKey != null) conn.setRequestProperty("access-key", accessKey);
        conn.setDoOutput(true); OutputStream out = conn.getOutputStream(); try { out.write(payload.toString().getBytes(StandardCharsets.UTF_8)); } finally { out.close(); }
        int code = conn.getResponseCode(); InputStream in = code >= 200 && code < 400 ? conn.getInputStream() : conn.getErrorStream(); String text = readAll(in);
        if (code < 200 || code >= 400) throw new Exception("HTTP " + code + ": " + text); return new JSONObject(text);
    }

    static String readAll(InputStream in) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); copy(in, out); return out.toString("UTF-8"); }
    static void notifyNewMessages(Context context, SyncResult result) {
        if (context == null || result == null || result.latestNew == null) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "PushPlus 消息", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("PushPlus 新消息提醒");
            manager.createNotificationChannel(channel);
        }
        Message message = result.latestNew;
        String title = result.newMessages > 1 ? "收到 " + result.newMessages + " 条新消息" : (message.title.length() == 0 ? "PushPlus 新消息" : message.title);
        String text = listPreviewFor(message);
        if (text.length() == 0) text = message.title.length() == 0 ? "点击查看消息" : message.title;
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, NOTIFICATION_ID_NEW_MESSAGES, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        manager.notify(NOTIFICATION_ID_NEW_MESSAGES, builder.build());
    }

    static String fetchMessageDetailText(String shortCode) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/shortMessage/" + shortCode).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 PushPlusHistoryAndroid/1.0");
            if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 400) return "";
            return htmlToText(readAll(conn.getInputStream()));
        } catch (Exception ignored) {
            return "";
        }
    }
    static String htmlToText(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?(p|div|section|article|header|footer|h[1-6]|li|ul|ol|tr|table|blockquote)[^>]*>", "\n")
                .replaceAll("(?i)</(td|th)>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        s = removePushPlusFooter(s);
        if (s.length() > 12000) return s.substring(0, 12000);
        return s;
    }
    static String displayContentFor(Message message) {
        if (message == null) return "";
        String text = message.contentText.length() == 0 ? message.rawJson : message.contentText;
        return cleanDisplayContent(text, message.title, message.shortCode, message.updateTime);
    }
    static String cleanDisplayContent(String text, String title, String shortCode) {
        return cleanDisplayContent(text, title, shortCode, "");
    }
    static String trimRepeatedHeading(String text, String title, String shortCode) {
        if (text == null) return "";
        String out = text.trim();
        out = removeLeadingToken(out, shortCode);
        out = removeLeadingToken(out, title);
        String compactTitle = title == null ? "" : title.replaceAll("\\s+", "").trim();
        String compactPrefix = shortCode == null ? "" : shortCode.trim();
        if (compactTitle.length() > 0 && compactPrefix.length() > 0) {
            out = removeLeadingToken(out, compactPrefix + compactTitle);
        }
        return out.trim();
    }
    static String cleanDisplayContent(String text, String title, String shortCode, String updateTime) {
        if (text == null) return "";
        String out = removePushPlusFooter(text).trim();
        out = removeLeadingDateTimeLines(out);
        String cleanTitle = cleanToken(title);
        String cleanCode = cleanToken(shortCode);
        String cleanTime = cleanToken(updateTime);
        for (int i = 0; i < 6; i++) {
            String before = out;
            out = removeLeadingDateTimePrefix(out);
            if (cleanCode.length() > 0 && cleanTitle.length() > 0) {
                out = removeLeadingDisplayToken(out, cleanCode + cleanTitle);
                out = removeLeadingDisplayToken(out, cleanCode + " " + cleanTitle);
                out = removeLeadingDisplayToken(out, cleanTitle + cleanCode);
                out = removeLeadingDisplayToken(out, cleanTitle + " " + cleanCode);
            }
            out = removeLeadingDisplayToken(out, cleanCode);
            out = removeLeadingDisplayToken(out, cleanTitle);
            out = removeLeadingNoiseLines(out, cleanTitle, cleanCode, cleanTime);
            out = removeLeadingDateTimePrefix(out);
            if (out.equals(before)) break;
        }
        return out.trim();
    }
    static String removeLeadingDateTimeLines(String text) {
        if (text == null || text.length() == 0) return "";
        String[] lines = text.split("\\n");
        int start = 0;
        while (start < lines.length) {
            String line = lines[start].trim();
            if (line.length() == 0 || isDateTimeLine(line)) {
                start++;
                continue;
            }
            break;
        }
        return joinLinesFrom(lines, start).trim();
    }
    static String removeLeadingDateTimePrefix(String text) {
        if (text == null || text.length() == 0) return "";
        String out = removeLeadingDateTimeLines(text).trim();
        return out.replaceFirst("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\s*[:：|｜,，;；.。\\-—_]+\\s*|\\s+)", "").trim();
    }
    static boolean isDateTimeLine(String line) {
        return line != null && line.trim().matches("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}");
    }
    static String removeLeadingNoiseLines(String text, String title, String shortCode, String updateTime) {
        if (text == null || text.length() == 0) return "";
        String[] lines = text.split("\\n");
        int start = 0;
        while (start < lines.length) {
            String line = lines[start].trim();
            if (line.length() == 0 || isDateTimeLine(line) || isPushPlusBrandLine(line) || equalsCleanToken(line, title) || equalsCleanToken(line, shortCode) || equalsCleanToken(line, updateTime)) {
                start++;
                continue;
            }
            break;
        }
        return joinLinesFrom(lines, start).trim();
    }
    static String removeLeadingBrandLines(String text) {
        if (text == null || text.length() == 0) return "";
        String[] lines = text.split("\\n");
        int start = 0;
        while (start < lines.length) {
            String line = lines[start].trim();
            if (line.length() == 0 || isPushPlusBrandLine(line)) {
                start++;
                continue;
            }
            break;
        }
        return joinLinesFrom(lines, start).trim();
    }
    static boolean isPushPlusBrandLine(String line) {
        String compact = line == null ? "" : line.replaceAll("\\s+", "").toLowerCase(Locale.US);
        return compact.equals("pushplus(推送加)") || compact.equals("pushplus") || compact.equals("推送加");
    }
    static String removeLeadingRepeatedLines(String text, String title, String shortCode) {
        if (text == null || text.length() == 0) return "";
        String[] lines = text.split("\\n");
        int start = 0;
        while (start < lines.length) {
            String line = lines[start].trim();
            if (line.length() == 0 || equalsCleanToken(line, title) || equalsCleanToken(line, shortCode)) {
                start++;
                continue;
            }
            break;
        }
        return joinLinesFrom(lines, start).trim();
    }
    static boolean equalsCleanToken(String line, String token) {
        String a = cleanToken(line);
        String b = cleanToken(token);
        return b.length() > 0 && a.equals(b);
    }
    static String cleanToken(String token) {
        return token == null ? "" : token.trim().replaceAll("\\s+", " ");
    }
    static String removeLeadingDisplayToken(String text, String token) {
        if (text == null) return "";
        String normalizedToken = cleanToken(token);
        if (normalizedToken.length() == 0) return text.trim();
        String out = stripLeadingSeparators(text);
        if (!out.startsWith(normalizedToken)) return text.trim();
        return stripLeadingSeparators(out.substring(normalizedToken.length())).trim();
    }
    static String stripLeadingSeparators(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("^[\\s:：;；,，.。\\-—_\\[\\]【】（）()<>《》|/\\\\]+", "");
    }
    static String removeLeadingToken(String text, String token) {
        if (text == null) return "";
        if (token == null || token.trim().length() == 0) return text.trim();
        String out = text.trim();
        String normalizedOut = out.replaceAll("^[\\s:：,，.。;；-]+", "");
        String normalizedToken = token.trim();
        if (!normalizedOut.startsWith(normalizedToken)) return out;
        return normalizedOut.substring(normalizedToken.length()).replaceAll("^[\\s:：,，.。;；-]+", "").trim();
    }
    static String removePushPlusFooter(String text) {
        if (text == null || text.length() == 0) return "";
        String[] lines = text.split("\\n");
        int end = lines.length;
        while (end > 0 && lines[end - 1].trim().length() == 0) end--;
        for (int i = Math.max(0, end - 8); i < end; i++) {
            String line = lines[i].trim();
            if (isPushPlusFooterLine(line)) {
                int cut = i;
                if (isVisitCountLine(line) && i > 0 && lines[i - 1].trim().matches("\\d+")) cut = i - 1;
                return joinLines(lines, cut).trim();
            }
        }
        return text.trim();
    }
    static boolean isVisitCountLine(String line) {
        return line.equals("访问次数") || line.equals("璁块棶娆℃暟");
    }
    static boolean isPushPlusFooterLine(String line) {
        return isVisitCountLine(line)
                || line.equals("打开小程序查看")
                || line.endsWith("IP归属地")
                || line.startsWith("鎵撳紑灏忕▼搴忔煡鐪")
                || line.contains("IP褰掑睘鍦");
    }
    static String joinLines(String[] lines, int end) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.toString();
    }
    static String joinLinesFrom(String[] lines, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, start); i < lines.length; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.toString();
    }
    static Message normalize(JSONObject item) { Message m = new Message(); m.shortCode = item.optString("shortCode", item.optString("short_code", item.optString("messageCode", ""))); m.title = item.optString("title", ""); m.contentText = item.optString("content", item.optString("contentText", "")); m.messageType = item.optInt("messageType", 0); m.topicName = item.optString("topicName", ""); m.channel = item.optString("channel", ""); m.isRead = item.optInt("isRead", 0); m.updateTime = item.optString("updateTime", ""); m.rawJson = item.toString(); return m; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    @Override public void onBackPressed() { if (showingDetail) showHome(); else super.onBackPressed(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    static class Message { String shortCode = ""; String title = ""; String contentText = ""; int messageType; String topicName = ""; String channel = ""; int isRead; String updateTime = ""; String rawJson = ""; @Override public String toString() { return title; } }

    static class MessageDb extends SQLiteOpenHelper {
        static final String DB_NAME = "pushplus_history.db";
        MessageDb(Context context) { super(context, DB_NAME, null, 1); }
        @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE IF NOT EXISTS messages (short_code TEXT PRIMARY KEY, title TEXT, content_text TEXT, message_type INTEGER, topic_name TEXT, channel TEXT, is_read INTEGER, update_time TEXT, raw_json TEXT, first_seen_at TEXT, last_seen_at TEXT)"); }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { onCreate(db); }
        boolean save(Message m) { if (m.shortCode.length() == 0) return false; String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); SQLiteDatabase db = getWritableDatabase(); ContentValues v = new ContentValues(); v.put("short_code", m.shortCode); v.put("title", m.title); v.put("content_text", m.contentText); v.put("message_type", m.messageType); v.put("topic_name", m.topicName); v.put("channel", m.channel); v.put("is_read", m.isRead); v.put("update_time", m.updateTime); v.put("raw_json", m.rawJson); v.put("last_seen_at", now); Cursor c = db.rawQuery("SELECT short_code FROM messages WHERE short_code=?", new String[]{m.shortCode}); boolean exists = c.moveToFirst(); c.close(); if (exists) { db.update("messages", v, "short_code=?", new String[]{m.shortCode}); return false; } else { v.put("first_seen_at", now); db.insert("messages", null, v); return true; } }
        List<Message> search(String q, int limit) { ArrayList<Message> out = new ArrayList<Message>(); ArrayList<String> args = new ArrayList<String>(); StringBuilder where = new StringBuilder("1=1"); if (q.length() > 0) { where.append(" AND (title LIKE ? OR content_text LIKE ? OR topic_name LIKE ? OR channel LIKE ?)"); String p = "%" + q.replace("%", "").replace("_", "") + "%"; args.add(p); args.add(p); args.add(p); args.add(p); } args.add(String.valueOf(limit)); Cursor c = getReadableDatabase().rawQuery("SELECT short_code,title,content_text,message_type,topic_name,channel,is_read,update_time,raw_json FROM messages WHERE " + where.toString() + " ORDER BY COALESCE(update_time,last_seen_at) DESC LIMIT ?", args.toArray(new String[args.size()])); try { while (c.moveToNext()) { Message m = new Message(); m.shortCode = value(c,0); m.title = value(c,1); m.contentText = value(c,2); m.messageType = c.getInt(3); m.topicName = value(c,4); m.channel = value(c,5); m.isRead = c.getInt(6); m.updateTime = value(c,7); m.rawJson = value(c,8); out.add(m); } } finally { c.close(); } return out; }
        int count() { Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages", null); try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); } }
        private String value(Cursor c, int index) { return c.getString(index) == null ? "" : c.getString(index); }
    }
}
