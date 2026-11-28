package com.deshbangla.keyboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class BanglaIME extends InputMethodService {

    private static final int BN = 0;
    private static final int EN = 1;
    private static final int SYM = 2;

    private static final int VL_EN = 0;
    private static final int VL_BN = 1;
    private static final int VL_BANGLISH = 2;

    private static final String MSG_TAP = "মাইক টিপে বলুন";
    private static final String MSG_WAIT = "অনুগ্রহ করে অপেক্ষা করুন...";
    private static final String MSG_SPEAK = "এখন বলুন...";

    // Colors (dark theme)
    private static final String C_BG = "#0D1B1C";
    private static final String C_KEY = "#26302F";
    private static final String C_KEY_PRESSED = "#3C4D4A";
    private static final String C_SPECIAL = "#1C2928";
    private static final String C_SPECIAL_PRESSED = "#33423F";
    private static final String C_ENTER = "#4F8478";
    private static final String C_ENTER_PRESSED = "#6AA394";
    private static final String C_ACCENT = "#7FCBBE";
    private static final String C_HINT = "#9AA5A3";

    private int mode = BN;
    private int prevMode = BN;
    private boolean shift = false;
    private boolean listening = false;
    private boolean voiceBar = false;
    private int voiceLang = VL_BN;
    private String statusMsg = MSG_TAP;
    private boolean trOn = false;
    private StringBuilder bnBuf = new StringBuilder();
    private TextView trPreview;
    private String trVoiceText = "";
    private LinearLayout root;
    private SpeechRecognizer recognizer;
    private AudioManager audio;
    private boolean mutedMusic = false;
    private boolean mutedSystem = false;

    private static final String[][] BN_ROWS = {
        {"অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও", "ঔ"},
        {"\u09BE", "\u09BF", "\u09C0", "\u09C1", "\u09C2", "\u09C3", "\u09C7", "\u09C8", "\u09CB", "\u09CC", "\u09CD"},
        {"ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"},
        {"ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"},
        {"প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ"},
        {"স", "হ", "\u09DC", "\u09DD", "\u09DF", "\u09CE", "\u0982", "\u0983", "\u0981", "<DEL>"}
    };

    private static final String[][] EN_ROWS = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"<SHIFT>", "z", "x", "c", "v", "b", "n", "m", "<DEL>"}
    };

    private static final String[][] SYM_ROWS = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"},
        {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
        {"*", "\"", "'", ":", ";", "!", "?", "\u09F3", "<DEL>"}
    };

    // Banglish (Bangla -> English letters) tables
    private static final String CONS = "কখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহ\u09DC\u09DD\u09DF\u09CE";
    private static final String[] CONS_R = {
        "k", "kh", "g", "gh", "ng", "ch", "chh", "j", "jh", "n",
        "t", "th", "d", "dh", "n", "t", "th", "d", "dh", "n",
        "p", "f", "b", "bh", "m", "j", "r", "l", "sh", "sh",
        "s", "h", "r", "rh", "y", "t"
    };
    private static final String VOW_I = "অআইঈউঊঋএঐওঔ";
    private static final String[] VOW_I_R = {"o", "a", "i", "i", "u", "u", "ri", "e", "oi", "o", "ou"};
    private static final String VOW_S = "\u09BE\u09BF\u09C0\u09C1\u09C2\u09C3\u09C7\u09C8\u09CB\u09CC";
    private static final String[] VOW_S_R = {"a", "i", "i", "u", "u", "ri", "e", "oi", "o", "ou"};
    private static final String DIG = "০১২৩৪৫৬৭৮৯";

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(C_BG));
        int p = dp(3);
        root.setPadding(p, p, p, p);
        render();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        bnBuf.setLength(0);
        shift = false;
        voiceBar = false;
        statusMsg = MSG_TAP;
        if (root != null) {
            render();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        bnBuf.setLength(0);
        if (recognizer != null && listening) {
            recognizer.cancel();
        }
        listening = false;
        voiceBar = false;
        statusMsg = MSG_TAP;
        unmuteBeep();
    }

    @Override
    public void onDestroy() {
        unmuteBeep();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    private void render() {
        root.removeAllViews();
        root.addView(voiceBar ? voiceTop() : (trOn ? translitTop() : topBar()));
        String[][] rows;
        if (mode == BN) {
            rows = BN_ROWS;
        } else if (mode == EN) {
            rows = EN_ROWS;
        } else {
            rows = SYM_ROWS;
        }
        for (int i = 0; i < rows.length; i++) {
            addRow(rows[i], i);
        }
        addRow(bottomRow(), -1);
    }

    private ImageButton makeMic() {
        ImageButton mic = new ImageButton(this);
        mic.setImageResource(R.drawable.ic_mic);
        mic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mic.setColorFilter(Color.WHITE);
        mic.setPadding(dp(9), dp(9), dp(9), dp(9));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(listening ? "#E53935" : C_KEY));
        mic.setBackground(circle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.setMargins(dp(4), dp(3), dp(6), dp(3));
        mic.setLayoutParams(lp);
        return mic;
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(space);
        bar.addView(makeLogo());

        ImageButton mic = makeMic();
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVoice();
            }
        });
        bar.addView(mic);
        return bar;
    }

    private GradientDrawable pill(String color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private View voiceTop() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView st = new TextView(this);
        st.setText(statusMsg);
        st.setTextColor(Color.parseColor(C_ACCENT));
        st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        st.setGravity(Gravity.CENTER);
        st.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));
        box.addView(st);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        TextView back = new TextView(this);
        back.setText("\u2190");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setLayoutParams(new LinearLayout.LayoutParams(
                dp(44), LinearLayout.LayoutParams.MATCH_PARENT));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeVoice();
            }
        });
        row.addView(back);

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(pill(C_SPECIAL, 19));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        sp.setMargins(dp(6), 0, dp(6), 0);
        seg.setLayoutParams(sp);
        String[] names = {"English", "বাংলা", "Banglish"};
        for (int i = 0; i < names.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(names[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            if (voiceLang == i) {
                tv.setBackground(pill(C_ACCENT, 16));
                tv.setTextColor(Color.parseColor(C_BG));
            } else {
                tv.setTextColor(Color.WHITE);
            }
            final int idx = i;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setVoiceLang(idx);
                }
            });
            seg.addView(tv);
        }
        row.addView(seg);

        ImageButton mic = makeMic();
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listening) {
                    if (recognizer != null) {
                        recognizer.stopListening();
                    }
                } else if (voiceReady()) {
                    startVoice();
                }
            }
        });
        row.addView(mic);

        box.addView(row);
        return box;
    }

    // ---------- Banglish typing bar (type Bangla, get English letters) ----------

    private View makeLogo() {
        TextView logo = new TextView(this);
        logo.setText("A\u6587");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(pill(C_KEY, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(34));
        lp.setMargins(dp(4), dp(3), dp(4), dp(3));
        logo.setLayoutParams(lp);
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTranslit();
            }
        });
        return logo;
    }

    private TextView trPill(String text, boolean strong) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(dp(14), 0, dp(14), 0);
        if (strong) {
            tv.setBackground(pill(C_ACCENT, 18));
            tv.setTextColor(Color.parseColor(C_BG));
        } else {
            tv.setBackground(pill(C_SPECIAL, 18));
            tv.setTextColor(Color.WHITE);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(3), 0, dp(3), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private View translitTop() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        row.addView(trPill("বাংলা", false));
        row.addView(trPill("\u21C4", false));
        row.addView(trPill("Banglish", true));

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        row.addView(sp);

        ImageButton mic = makeMic();
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listening) {
                    if (recognizer != null) {
                        recognizer.stopListening();
                    }
                    return;
                }
                if (!voiceReady()) {
                    return;
                }
                voiceLang = VL_BN;
                startVoice();
            }
        });
        row.addView(mic);

        TextView close = new TextView(this);
        close.setText("\u2715");
        close.setTextColor(Color.WHITE);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        close.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(C_KEY));
        close.setBackground(circle);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(40), dp(40));
        cp.setMargins(dp(2), dp(3), dp(6), dp(3));
        close.setLayoutParams(cp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeTranslit();
            }
        });
        row.addView(close);
        box.addView(row);

        trPreview = new TextView(this);
        trPreview.setGravity(Gravity.CENTER_VERTICAL);
        trPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        trPreview.setSingleLine(true);
        trPreview.setPadding(dp(14), 0, dp(14), 0);
        trPreview.setBackground(pill(C_SPECIAL, 17));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        pp.setMargins(dp(4), 0, dp(4), dp(3));
        trPreview.setLayoutParams(pp);
        updatePreview();
        box.addView(trPreview);
        return box;
    }

    private void updatePreview() {
        if (trPreview == null) {
            return;
        }
        if (bnBuf.length() > 0) {
            trPreview.setText(bnBuf.toString());
            trPreview.setTextColor(Color.WHITE);
        } else if (listening) {
            trPreview.setText(statusMsg);
            trPreview.setTextColor(Color.parseColor(C_ACCENT));
        } else if (trVoiceText.length() > 0) {
            trPreview.setText(trVoiceText);
            trPreview.setTextColor(Color.WHITE);
        } else {
            trPreview.setText("বাংলা টাইপ করুন, Banglish লেখা হবে।");
            trPreview.setTextColor(Color.parseColor(C_HINT));
        }
    }

    private void openTranslit() {
        trOn = true;
        bnBuf.setLength(0);
        trVoiceText = "";
        if (mode != BN) {
            mode = BN;
            shift = false;
        }
        render();
    }

    private void closeTranslit() {
        if (recognizer != null && listening) {
            recognizer.cancel();
            listening = false;
            unmuteBeep();
        }
        trVoiceText = "";
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && bnBuf.length() > 0) {
            ic.finishComposingText();
        }
        bnBuf.setLength(0);
        trOn = false;
        render();
    }

    // true = কী এখানেই সামলানো হয়েছে, false = সাধারণ নিয়মে চলবে
    private boolean handleTranslit(InputConnection ic, String key) {
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= '\u0980' && c <= '\u09FF' && c != '\u0964') {
                trVoiceText = "";
                bnBuf.append(c);
                ic.setComposingText(toBanglish(bnBuf.toString()), 1);
                updatePreview();
                return true;
            }
        }
        if (key.equals("<DEL>")) {
            if (bnBuf.length() > 0) {
                bnBuf.setLength(bnBuf.length() - 1);
                if (bnBuf.length() == 0) {
                    ic.setComposingText("", 1);
                    ic.finishComposingText();
                } else {
                    ic.setComposingText(toBanglish(bnBuf.toString()), 1);
                }
                updatePreview();
                return true;
            }
            return false;
        }
        if (bnBuf.length() > 0) {
            ic.finishComposingText();
            bnBuf.setLength(0);
            updatePreview();
        }
        if (key.equals("\u0964")) {
            ic.commitText(".", 1);
            return true;
        }
        return false;
    }

    private String[] bottomRow() {
        String first = (mode == SYM) ? "<BACK>" : "<SYM>";
        String punct = (mode == BN) ? "\u0964" : ".";
        return new String[]{first, ",", "<LANG>", "<SPACE>", punct, "<ENTER>"};
    }

    private int langTarget() {
        if (mode == BN) {
            return EN;
        }
        if (mode == EN) {
            return BN;
        }
        return (prevMode == BN) ? EN : BN;
    }

    private void addRow(String[] keys, int rowIndex) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int h = (mode == BN) ? dp(42) : dp(48);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h));
        boolean halfPad = (mode == EN && rowIndex == 1);
        if (halfPad) {
            row.addView(spacer());
        }
        for (int i = 0; i < keys.length; i++) {
            String hint = null;
            if (mode == EN && rowIndex == 0) {
                hint = String.valueOf((i + 1) % 10);
            }
            boolean accent = (mode == BN && (rowIndex == 0 || rowIndex == 1));
            row.addView(makeKey(keys[i], accent, hint));
        }
        if (halfPad) {
            row.addView(spacer());
        }
        root.addView(row);
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));
        return v;
    }

    private float weightOf(String k) {
        if (k.equals("<SPACE>")) {
            return 4f;
        }
        if (k.startsWith("<")) {
            return 1.5f;
        }
        return 1f;
    }

    private float textSizeOf(String key) {
        if (key.equals("<SPACE>")) {
            return 14f;
        }
        if (key.equals("<SHIFT>") || key.equals("<DEL>") || key.equals("<ENTER>")) {
            return 22f;
        }
        if (key.startsWith("<")) {
            return 16f;
        }
        if (mode == BN) {
            return 19f;
        }
        if (mode == EN) {
            return 22f;
        }
        return 20f;
    }

    private View makeKey(final String key, boolean accent, final String hint) {
        FrameLayout f = new FrameLayout(this);
        f.setClickable(true);
        boolean special = key.startsWith("<");
        if (key.equals("<ENTER>")) {
            f.setBackground(bg(C_ENTER, C_ENTER_PRESSED));
        } else if (special) {
            f.setBackground(bg(C_SPECIAL, C_SPECIAL_PRESSED));
        } else {
            f.setBackground(bg(C_KEY, C_KEY_PRESSED));
        }

        TextView t = new TextView(this);
        t.setText(labelOf(key));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeOf(key));
        if (key.equals("<SPACE>")) {
            t.setTextColor(Color.parseColor(C_HINT));
        } else if (accent) {
            t.setTextColor(Color.parseColor(C_ACCENT));
        } else {
            t.setTextColor(Color.WHITE);
        }
        t.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        f.addView(t);

        if (hint != null) {
            TextView h = new TextView(this);
            h.setText(hint);
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            h.setTextColor(Color.parseColor(C_HINT));
            h.setIncludeFontPadding(false);
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            hp.setMargins(0, dp(3), dp(5), 0);
            h.setLayoutParams(hp);
            f.addView(h);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weightOf(key));
        int m = dp(2);
        lp.setMargins(m, m, m, m);
        f.setLayoutParams(lp);
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleKey(key);
            }
        });
        if (hint != null) {
            f.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.commitText(hint, 1);
                    }
                    return true;
                }
            });
        }
        return f;
    }

    private String labelOf(String key) {
        switch (key) {
            case "<SHIFT>":
                return shift ? "\u21EA" : "\u21E7";
            case "<DEL>":
                return "\u232B";
            case "<ENTER>":
                return "\u21B5";
            case "<SPACE>":
                return "Desh Keyboard";
            case "<SYM>":
                return "?123";
            case "<BACK>":
                return (prevMode == BN) ? "অআ" : "ABC";
            case "<LANG>":
                return (langTarget() == EN) ? "EN" : "বাং";
        }
        if (isMark(key)) {
            return "\u25CC" + key;
        }
        if (mode == EN && shift) {
            return key.toUpperCase();
        }
        return key;
    }

    private boolean isMark(String s) {
        if (s.length() != 1) {
            return false;
        }
        int t = Character.getType(s.charAt(0));
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;
    }

    private void handleKey(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        if (trOn && mode == BN && handleTranslit(ic, key)) {
            return;
        }
        switch (key) {
            case "<DEL>": {
                CharSequence sel = ic.getSelectedText(0);
                if (sel != null && sel.length() > 0) {
                    ic.commitText("", 1);
                } else {
                    ic.deleteSurroundingText(1, 0);
                }
                return;
            }
            case "<ENTER>":
                if (!sendDefaultEditorAction(true)) {
                    ic.commitText("\n", 1);
                }
                return;
            case "<SPACE>":
                ic.commitText(" ", 1);
                return;
            case "<SHIFT>":
                shift = !shift;
                render();
                return;
            case "<SYM>":
                prevMode = mode;
                mode = SYM;
                render();
                return;
            case "<BACK>":
                mode = prevMode;
                render();
                return;
            case "<LANG>":
                mode = langTarget();
                shift = false;
                render();
                return;
        }
        if (mode == EN && shift) {
            ic.commitText(key.toUpperCase(), 1);
            shift = false;
            render();
            return;
        }
        ic.commitText(key, 1);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void muteBeep() {
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            if (!audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                mutedMusic = true;
            }
        } catch (Throwable t) {
        }
        try {
            if (!audio.isStreamMute(AudioManager.STREAM_SYSTEM)) {
                audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0);
                mutedSystem = true;
            }
        } catch (Throwable t) {
        }
    }

    private void unmuteBeep() {
        if (audio == null) {
            return;
        }
        if (mutedMusic) {
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            } catch (Throwable t) {
            }
            mutedMusic = false;
        }
        if (mutedSystem) {
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0);
            } catch (Throwable t) {
            }
            mutedSystem = false;
        }
    }

    // ---------- Voice ----------

    private boolean voiceReady() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("মাইক্রোফোনের অনুমতি নেই। Desh Bangla Keyboard অ্যাপটি খুলে অনুমতি দিন।");
            Intent i = new Intent(this, SetupActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return false;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("এই ফোনে ভয়েস টাইপিং সেবা পাওয়া যায়নি।");
            return false;
        }
        return true;
    }

    private void openVoice() {
        if (!voiceReady()) {
            return;
        }
        int cur = (mode == SYM) ? prevMode : mode;
        voiceLang = (cur == EN) ? VL_EN : VL_BN;
        voiceBar = true;
        startVoice();
    }

    private String capFirst(String t) {
        if (t.length() == 0) {
            return t;
        }
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private void closeVoice() {
        if (recognizer != null && listening) {
            recognizer.cancel();
        }
        listening = false;
        unmuteBeep();
        voiceBar = false;
        statusMsg = MSG_TAP;
        render();
    }

    private void setVoiceLang(int l) {
        if (l == voiceLang) {
            return;
        }
        voiceLang = l;
        if (listening) {
            if (recognizer != null) {
                recognizer.cancel();
            }
            listening = false;
            statusMsg = MSG_WAIT;
            render();
            root.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (voiceBar && !listening) {
                        startVoice();
                    }
                }
            }, 350);
        } else {
            render();
        }
    }

    private void startVoice() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    statusMsg = MSG_SPEAK;
                    render();
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }

                @Override
                public void onError(int error) {
                    listening = false;
                    unmuteBeep();
                    statusMsg = MSG_TAP;
                    render();
                    if (error == SpeechRecognizer.ERROR_CLIENT) {
                        return;
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH
                            || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        toast("কিছু শোনা যায়নি, আবার চেষ্টা করুন।");
                    } else if (error == SpeechRecognizer.ERROR_NETWORK
                            || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        toast("ইন্টারনেট সংযোগ চেক করুন।");
                    } else {
                        toast("ভয়েস টাইপিং ত্রুটি (কোড " + error + ")");
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    listening = false;
                    unmuteBeep();
                    ArrayList<String> list =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) {
                        String text = list.get(0);
                        InputConnection ic = getCurrentInputConnection();
                        if (trOn && !voiceBar) {
                            trVoiceText = text;
                            if (ic != null) {
                                if (bnBuf.length() > 0) {
                                    ic.finishComposingText();
                                    bnBuf.setLength(0);
                                }
                                ic.commitText(capFirst(toBanglish(text)) + " ", 1);
                            }
                        } else {
                            if (voiceLang == VL_BANGLISH) {
                                text = toBanglish(text);
                            }
                            if (ic != null) {
                                ic.commitText(text + " ", 1);
                            }
                        }
                    }
                    statusMsg = MSG_TAP;
                    render();
                }
            });
        }
        String lang = (voiceLang == VL_EN) ? "en-IN" : "bn-IN";
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        statusMsg = MSG_WAIT;
        muteBeep();
        render();
        recognizer.startListening(intent);
    }

    // ---------- Banglish converter (approximate) ----------

    private String toBanglish(String s) {
        StringBuilder out = new StringBuilder();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0980' && c <= '\u09FF' && c != '\u0964') {
                run.append(c);
            } else {
                if (run.length() > 0) {
                    out.append(bnWord(run.toString()));
                    run.setLength(0);
                }
                if (c == '\u0964') {
                    out.append('.');
                } else {
                    out.append(c);
                }
            }
        }
        if (run.length() > 0) {
            out.append(bnWord(run.toString()));
        }
        return out.toString();
    }

    private String bnWord(String w) {
        ArrayList<String> txt = new ArrayList<String>();
        ArrayList<Integer> typ = new ArrayList<Integer>();
        int n = w.length();
        int i = 0;
        while (i < n) {
            char c = w.charAt(i);
            int ci = CONS.indexOf(c);
            if (ci >= 0) {
                String r = CONS_R[ci];
                int j = i + 1;
                if (j < n && w.charAt(j) == '\u09BC') {
                    if (c == '\u09A1') {
                        r = "r";
                    } else if (c == '\u09A2') {
                        r = "rh";
                    } else if (c == '\u09AF') {
                        r = "y";
                    }
                    j++;
                }
                if (j < n && w.charAt(j) == '\u09CD') {
                    txt.add(r);
                    typ.add(0);
                    i = j + 1;
                    continue;
                }
                int si = (j < n) ? VOW_S.indexOf(w.charAt(j)) : -1;
                if (si >= 0) {
                    txt.add(r + VOW_S_R[si]);
                    typ.add(2);
                    i = j + 1;
                    continue;
                }
                txt.add(r);
                typ.add(1);
                i = j;
                continue;
            }
            int vi = VOW_I.indexOf(c);
            if (vi >= 0) {
                txt.add(VOW_I_R[vi]);
                typ.add(0);
            } else if (c == '\u0982') {
                txt.add("ng");
                typ.add(0);
            } else if (c == '\u0983') {
                txt.add("h");
                typ.add(0);
            } else {
                int di = DIG.indexOf(c);
                if (di >= 0) {
                    txt.add(String.valueOf(di));
                    typ.add(0);
                }
            }
            i++;
        }
        StringBuilder out = new StringBuilder();
        boolean prevInh = false;
        int m = txt.size();
        for (int k = 0; k < m; k++) {
            out.append(txt.get(k));
            int ty = typ.get(k);
            if (ty == 1) {
                boolean last = (k == m - 1);
                boolean drop = last || (k > 0 && prevInh && k + 1 < m && typ.get(k + 1) == 2);
                if (!drop) {
                    out.append('o');
                }
                prevInh = !drop;
            } else {
                prevInh = false;
            }
        }
        return out.toString();
    }

    private StateListDrawable bg(String normal, String pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, shape(pressed));
        s.addState(new int[]{}, shape(normal));
        return s;
    }

    private GradientDrawable shape(String color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(8));
        return g;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
            }
     

