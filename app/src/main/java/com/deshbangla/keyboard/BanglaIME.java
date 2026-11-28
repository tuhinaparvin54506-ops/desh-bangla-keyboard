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
        shift = false;
        if (root != null) {
            render();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        if (recognizer != null && listening) {
            recognizer.cancel();
            listening = false;
        }
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
        root.addView(topBar());
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

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        TextView status = new TextView(this);
        status.setText(listening ? "শুনছি... বাংলায় বলুন" : "");
        status.setTextColor(Color.parseColor(C_ACCENT));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setPadding(dp(8), 0, 0, 0);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(status);

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
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVoice();
            }
        });
        bar.addView(mic);
        return bar;
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

    private void toggleVoice() {
        if (listening) {
            if (recognizer != null) {
                recognizer.stopListening();
            }
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("মাইক্রোফোনের অনুমতি নেই। Desh Bangla Keyboard অ্যাপটি খুলে অনুমতি দিন।");
            Intent i = new Intent(this, SetupActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("এই ফোনে ভয়েস টাইপিং সেবা পাওয়া যায়নি।");
            return;
        }
        startVoice();
    }

    private void startVoice() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
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
                    render();
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
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(list.get(0) + " ", 1);
                        }
                    }
                    render();
                }
            });
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-IN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-IN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        muteBeep();
        render();
        recognizer.startListening(intent);
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
    
        
                

                
