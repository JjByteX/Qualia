package com.example.qualia.util;

import android.os.Handler;
import android.widget.TextView;

import java.util.Random;

public class TypingAnimator {

    public interface OnComplete {
        void onComplete();
    }

    private final Handler handler;
    private final Random random;
    private boolean cancelled = false;

    // Timing
    private static final int BASE_SPEED = 38;
    private static final int PUNCTUATION_PAUSE = 280;
    private static final int COMMA_PAUSE = 160;
    private static final int TYPO_PAUSE_BEFORE_DELETE = 120;
    private static final int TYPO_PAUSE_AFTER_DELETE = 60;
    private static final float TYPO_CHANCE = 0.018f;

    // Adjacent keys for realistic typos
    private static final String[][] NEIGHBORS = {
            {"a","s"},{"b","v","n"},{"c","x","v"},{"d","s","f"},
            {"e","r","w"},{"f","d","g"},{"g","f","h"},{"h","g","j"},
            {"i","u","o"},{"j","h","k"},{"k","j","l"},{"l","k"},
            {"m","n"},{"n","m","b"},{"o","i","p"},{"p","o"},
            {"q","w"},{"r","e","t"},{"s","a","d"},{"t","r","y"},
            {"u","y","i"},{"v","c","b"},{"w","q","e"},{"x","z","c"},
            {"y","t","u"},{"z","x"}
    };

    public TypingAnimator(Handler handler) {
        this.handler = handler;
        this.random = new Random();
    }

    public void cancel() {
        cancelled = true;
    }

    public void type(final TextView textView, final String text, final OnComplete onComplete) {
        cancelled = false;
        typeChar(textView, text, 0, new StringBuilder(), onComplete);
    }

    private void typeChar(final TextView textView, final String text,
                          final int index, final StringBuilder current,
                          final OnComplete onComplete) {

        if (cancelled) return;

        if (index >= text.length()) {
            if (onComplete != null) onComplete.onComplete();
            return;
        }

        char c = text.charAt(index);

        // Decide if we make a typo here
        boolean makeTypo = (Character.isLetter(c) && random.nextFloat() < TYPO_CHANCE);

        if (makeTypo) {
            char wrongChar = getNeighbor(c);
            // Type wrong char
            current.append(wrongChar);
            textView.setText(current.toString());

            // Pause, then delete it
            handler.postDelayed(() -> {
                if (cancelled) return;
                current.deleteCharAt(current.length() - 1);
                textView.setText(current.toString());

                // Short pause, then type correct char
                handler.postDelayed(() -> {
                    if (cancelled) return;
                    current.append(c);
                    textView.setText(current.toString());

                    long delay = nextDelay(c);
                    handler.postDelayed(() ->
                                    typeChar(textView, text, index + 1, current, onComplete),
                            delay);

                }, TYPO_PAUSE_AFTER_DELETE);

            }, TYPO_PAUSE_BEFORE_DELETE + random.nextInt(80));

        } else {
            current.append(c);
            textView.setText(current.toString());

            long delay = nextDelay(c);
            handler.postDelayed(() ->
                            typeChar(textView, text, index + 1, current, onComplete),
                    delay);
        }
    }

    private long nextDelay(char c) {
        // Punctuation pauses
        if (c == '.' || c == '…') return PUNCTUATION_PAUSE + random.nextInt(120);
        if (c == ',') return COMMA_PAUSE + random.nextInt(80);
        if (c == '?' || c == '!') return PUNCTUATION_PAUSE + random.nextInt(100);
        if (c == ' ') return BASE_SPEED + random.nextInt(30);

        // Natural rhythm variation
        int variation = random.nextInt(60) - 20;
        long base = BASE_SPEED + variation;

        // Occasional slight hesitation — as if thinking
        if (random.nextFloat() < 0.06f) base += random.nextInt(180);

        return Math.max(18, base);
    }

    private char getNeighbor(char c) {
        String lower = String.valueOf(c).toLowerCase();
        for (String[] group : NEIGHBORS) {
            if (group[0].equals(lower) && group.length > 1) {
                String neighbor = group[1 + random.nextInt(group.length - 1)];
                return Character.isUpperCase(c)
                        ? neighbor.toUpperCase().charAt(0)
                        : neighbor.charAt(0);
            }
        }
        // Fallback — adjacent on keyboard
        return (char)(c + (random.nextBoolean() ? 1 : -1));
    }
}