package com.localsms.sms;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

public final class SmsRepository {
    private SmsRepository() {}

    public static List<SmsModel> getLatest(ContentResolver resolver, int limit) {
        List<SmsModel> result = new ArrayList<>();
        Cursor c = null;
        try {
            c = resolver.query(Uri.parse("content://sms"),
                    new String[]{"_id", "address", "body", "date", "type", "read"},
                    null, null, "date DESC");
            if (c == null) return result;
            int count = 0;
            while (c.moveToNext() && count < limit) {
                result.add(new SmsModel(
                        c.getString(c.getColumnIndexOrThrow("_id")),
                        c.getString(c.getColumnIndexOrThrow("address")),
                        c.getString(c.getColumnIndexOrThrow("body")),
                        c.getLong(c.getColumnIndexOrThrow("date")),
                        c.getInt(c.getColumnIndexOrThrow("type")),
                        c.getInt(c.getColumnIndexOrThrow("read"))
                ));
                count++;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    public static String toJson(ContentResolver resolver, int limit) {
        StringBuilder j = new StringBuilder("[");
        boolean first = true;
        for (SmsModel s : getLatest(resolver, limit)) {
            if (!first) j.append(',');
            first = false;
            j.append("{\"id\":\"").append(json(s.id))
                    .append("\",\"address\":\"").append(json(s.address))
                    .append("\",\"body\":\"").append(json(s.body))
                    .append("\",\"date\":").append(s.date)
                    .append(",\"type\":").append(s.type)
                    .append(",\"read\":").append(s.read)
                    .append('}');
        }
        return j.append(']').toString();
    }

    private static String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
