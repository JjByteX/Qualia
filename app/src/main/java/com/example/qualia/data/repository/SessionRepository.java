package com.example.qualia.data.repository;

import android.content.Context;

import com.example.qualia.data.model.Session;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

public class SessionRepository {

    private final List<Session> sessions;

    public SessionRepository(Context context) {
        sessions = loadSessions(context);
    }

    private List<Session> loadSessions(Context context) {
        try {
            InputStream is = context.getAssets().open("sessions.json");
            InputStreamReader reader = new InputStreamReader(is);
            Type listType = new TypeToken<List<Session>>() {}.getType();
            return new Gson().fromJson(reader, listType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Session> getAll() {
        return sessions;
    }
}