package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.example.qualia.R;
import com.example.qualia.data.model.JournalEntry;
import com.example.qualia.data.repository.JournalRepository;

import java.util.List;

public class GraduationActivity extends BaseActivity {

    private static final String GRADUATION_TEXT =
            "You have been here before.\n\n" +
                    "Not in this app. In this feeling. The one where you sit still long enough to remember that you exist.\n\n" +
                    "You did that seventy times.\n\n" +
                    "Most people never do it once.\n\n" +
                    "You came here because something was covering you. The noise. The feed. The feeling that everyone else was living louder and better and more certainly than you. You came here because you forgot what it felt like to be inside your own life instead of watching it from somewhere just outside.\n\n" +
                    "We don't know what you found here. We couldn't see it. It was yours.\n\n" +
                    "But you kept coming back. That means something was worth returning to.\n\n" +
                    "Here is what we know about you.\n\n" +
                    "You have a body that breathes without being asked. It has carried you through every ordinary moment you've ever lived. Every morning you didn't notice. Every meal that tasted like something. Every time you sat in a room and felt, for a second, like you were exactly where you were supposed to be.\n\n" +
                    "You have a memory. It keeps things you didn't ask it to keep. A smell. A voice. The quality of light in a place you'll never go back to. The memory doesn't ask permission. It just holds things. Quietly. For whenever you need them.\n\n" +
                    "You have a self. It was there before the noise started. It will be there when the noise stops. It has been waiting, patiently, in the space between one thought and the next.\n\n" +
                    "That space is what you've been practicing.\n\n" +
                    "There was a man once who spent his whole life trying to get to the stage. When he finally got there, when the music finally happened, he felt — nothing. Or not nothing. Something smaller than he expected. He looked around the room after the applause and thought: is this it?\n\n" +
                    "And then he went home and sat at his piano alone and touched a maple seed someone had left in his pocket and he understood.\n\n" +
                    "It was never about the stage.\n\n" +
                    "It was always this. The seed. The small weight of it in his hand. The fact that he was there to hold it.\n\n" +
                    "You have been holding things this whole time.\n\n" +
                    "The words you wrote in the dark. The questions you sat with instead of answering. The nights you came here tired and left feeling something you couldn't name — something lighter, maybe, or at least something more honest.\n\n" +
                    "Those were yours. We didn't give them to you. You brought them.\n\n" +
                    "You are made of moments no one else has lived. Specific ones. The ones that stayed with you for reasons you don't fully understand. The ones that hurt in a way only you can feel. The ones that were so ordinary they almost disappeared — except they didn't. They stayed.\n\n" +
                    "That is what qualia means.\n\n" +
                    "The redness of a rose. The ache of a headache. The taste of morning coffee. Things that cannot be transferred. Cannot be measured. Cannot be taken.\n\n" +
                    "You are full of them.\n\n" +
                    "Go now. Not because you are done. You are not done. There is no done.\n\n" +
                    "Go because you know the way back to yourself now. You have practiced it seventy times. You can find it without this. You could always find it. You just needed somewhere quiet to remember.\n\n" +
                    "Take that quiet with you.\n\n" +
                    "It was always yours.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graduation);

        TextView txtGraduation = findViewById(R.id.txtGraduation);
        TextView btnClose = findViewById(R.id.btnClose);

        // Load journal entries and weave them in
        new JournalRepository(this).getAllEntries(entries ->
                runOnUiThread(() -> buildGraduationText(txtGraduation, btnClose, entries))
        );
    }

    private void buildGraduationText(TextView txtGraduation, TextView btnClose,
                                     List<JournalEntry> entries) {
        StringBuilder full = new StringBuilder();

        // If they have journal entries, open with their first words
        if (entries != null && !entries.isEmpty()) {
            full.append("You wrote things down.\n\n");
            // Show up to 3 short journal fragments
            int shown = 0;
            for (JournalEntry entry : entries) {
                if (shown >= 3) break;
                String preview = entry.text.length() > 80
                        ? entry.text.substring(0, 80).trim() + "..."
                        : entry.text;
                full.append("\u201C").append(preview).append("\u201D\n\n");
                shown++;
            }
            full.append("Those were real.\n\n");
            full.append("Now read on.\n\n");
            full.append("—\n\n");
        }

        full.append(GRADUATION_TEXT);
        txtGraduation.setText(full.toString());

        // Fade in text slowly
        txtGraduation.animate().alpha(1f).setStartDelay(600).setDuration(1200).start();

        // Button appears after a long delay — let them read first
        new Handler().postDelayed(() ->
                btnClose.animate().alpha(1f).setDuration(800).start(), 8000);

        btnClose.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }
}