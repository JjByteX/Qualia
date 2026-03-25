package com.example.qualia.util;

import com.example.qualia.data.model.Session;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SessionPicker {

    public static Session pick(List<Session> sessions, String[] lastSessionKeys) {
        if (sessions == null || sessions.isEmpty()) return null;

        List<String> excluded = Arrays.asList(lastSessionKeys);
        List<Session> available = new java.util.ArrayList<>();

        for (Session s : sessions) {
            if (!excluded.contains(s.key)) {
                available.add(s);
            }
        }

        // If somehow all are excluded, reset and pick from full list
        if (available.isEmpty()) available = sessions;

        return available.get(new Random().nextInt(available.size()));
    }
}