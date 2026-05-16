package com.example.qualia.data.model;

import java.util.List;

public class Session {
    public String key;
    public String theme;
    /** Which of the three film inspirations shaped this session
     *  (Nine Days / Soul / Eternal Sunshine / Flat / Graduation Arc).
     *  Already present in sessions.json; kept on the model so Gson
     *  doesn't quietly drop the field. Not surfaced in the UI today. */
    public String perspective;
    /** True for sessions that touch heavier material (grief, loss, the
     *  unfixable, mortality). When true, ClosingActivity surfaces a
     *  single quiet crisis-resource line under the closing copy. The
     *  field is optional in JSON; missing == not heavy. */
    public boolean heavy;
    public String closingLine;
    public List<SessionLine> lines;
}
