package com.example.autonomousos.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

public class VideoModel {
    Timestamp date;
    String link;
    GeoPoint location;
    String user;

    public VideoModel(Timestamp date, String link, GeoPoint location, String user) {
        this.user = user;
        this.date = date;
        this.link = link;
        this.location = location;
    }

    public VideoModel(){

    }

    public GeoPoint getLocation() {
        return location;
    }

    public String getLink() {
        return link;
    }

    public String getUser() {
        return user;
    }

    public Timestamp getDate() { return date; }
}
