package com.example.autonomousos.model;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

public class Repository {
    private static Repository instance;

    public static Repository getInstance(){
        if (instance==null){
            instance= new Repository();
        }
        return instance;
    }

    public MutableLiveData<ArrayList<VideoModel>> getVideoEntries(){
        final MutableLiveData<ArrayList<VideoModel>> videoEntries = new MutableLiveData<>();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("videos")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("firebase", "Listen failed.", e);
                            return;
                        }

                        ArrayList<VideoModel> documentVideos = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            VideoModel entry = doc.toObject(VideoModel.class);
                            documentVideos.add(entry);

                        }
                        videoEntries.postValue(documentVideos);
                        Log.d("firebase", "updated live data" );
                    }
                });
        return videoEntries;
    }
}
