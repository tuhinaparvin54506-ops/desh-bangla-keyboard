package com.deshbangla.keyboard;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;

public class BanglaIME extends InputMethodService {

    private static final int BN = 0;
    private static final int EN = 1;
    private static final int SYM = 2;

    private int mode = BN;
    private int prevMode = BN;
    private boolean shift = false;
    private LinearLayout root;

    private static final String[][] BN_ROWS = {
        {"অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও", "ঔ"},
        {"\u09BE", "\u09BF", "\u09C0", "\u09C1", "\u09C2", "\u09C3", "\u09C7", "\u09C8", "\u09CB", "\u09CC", "\u09CD"},
        {"ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"},
        {"ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"},
        {"প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ"},
        {"স", "হ", "\u09DC", "\u09DD", "\u09DF", "\u09CE", "\u0982", "\u0983", "\u0981"}
    };

    private static final String[][] EN_ROWS = {
        {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
        {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
        {"<SHIFT>", "z", "x", "c", "v", "b", "n", "m"}
    };

    private static final String[][] SYM_ROWS = {
        {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
        {"১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"},
        {"@", "#", "$", "%", "&", "-", "+", "(", ")", "/"},
        {"*", "\"", "'", ":", ";", "!", "?", ",", ".", "\u09F3"}
    };

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#DCE6F5"));
        int p = dp(3);
        root.setPadding(p, p, p, p);
        render();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        shift = false;
        if (root != null) {
            render();
        }
    }

    private void render() {
        root.removeAllViews();
        String[][] rows;
        if (mode == BN) {
            rows = BN_ROWS;
        } else if (mode == EN) {
            rows = EN_ROWS;
        } else {
            rows = SYM_ROWS;
        }
        for (String[] r : rows) {
            addRow(r);
        }
        addRow(bottomRow());
    }

    private String[] bottomRow() {
        String first = (mode == SYM) ? "<BACK>" : "<SYM>";
        String punct = (mode == BN) ? "।" : ".";
        return new String[]{first, "<LANG>", "<SPACE>", punct, "<DEL>", "<ENTER>"};
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

    private void addRow(String[] keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        for (String k : keys) {
            row.addView(makeKey(k));
        }
        root.addView(row);
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

    private View makeKey(final String key) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setTextColor(Color.BLACK);
        b.setGravity(Gravity.CENTER);
        boolean special = key.startsWith("<");
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, special ? 14 : 18);
        if (special) {
            b.setBackground(bg("#B9CCEE", "#8FAADD"));
        } else {
            b.setBackground(bg("#FFFFFF", "#C8D4EA"));
        }
        b.setText(labelOf(key));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weightOf(key));
        int m = dp(2);
        lp.setMargins(m, m, m, m);
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleKey(key);
            }
        });
        return b;
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
                if (mode == BN) {
                    return "বাংলা";
                }
                if (mode == EN) {
                    return "English";
                }
                return " ";
            case "<SYM>":
                return "123";
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

    private StateListDrawable bg(String normal, String pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, shape(pressed));
        s.addState(new int[]{}, shape(normal));
        return s;
    }

    private GradientDrawable shape(String color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(6));
        return g;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
                             }
