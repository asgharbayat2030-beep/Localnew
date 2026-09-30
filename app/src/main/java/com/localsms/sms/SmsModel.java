package com.localsms.sms;

public final class SmsModel {
    public final String id;
    public final String address;
    public final String body;
    public final long date;
    public final int type;
    public final int read;

    public SmsModel(String id, String address, String body, long date, int type, int read) {
        this.id = id;
        this.address = address;
        this.body = body;
        this.date = date;
        this.type = type;
        this.read = read;
    }
}
