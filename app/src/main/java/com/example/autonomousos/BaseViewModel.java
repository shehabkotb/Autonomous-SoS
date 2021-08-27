package com.example.autonomousos;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Parcel;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.autonomousos.model.Repository;
import com.example.autonomousos.model.VideoModel;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskExecutors;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class BaseViewModel extends AndroidViewModel {
    public static String ACTION_SOS = "ACTION_SOS";
    public static int SOS_REQUEST_CODE = 1040;

    private FirebaseAuth mAuth;
    private Repository mRepository;
    private StorageReference mStorageRef;
    PendingIntent mSosPendingIntent;

    private MutableLiveData<ArrayList<VideoModel>> videoEntries;
    private MutableLiveData<String> status;
    private MutableLiveData<Boolean> serviceStatus;

    public BaseViewModel(@NonNull Application application) {
        super(application);
    }

    public void init() {
        if(videoEntries != null){
            return;
        }
        mRepository = Repository.getInstance();
        videoEntries = mRepository.getVideoEntries();
        mStorageRef = FirebaseStorage.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        status = new MutableLiveData<>();
        status.postValue(null);

        serviceStatus = new MutableLiveData<>();
        serviceStatus.postValue(false);

    }

    public LiveData<ArrayList<VideoModel>> getVideoEntries() {
        return videoEntries;
    }

    public FirebaseUser getUser(){
        return mAuth.getCurrentUser();
    }

    public void signOut(){
        mAuth.signOut();
    }

    public void uploadVideo(Uri videoURI, Location location){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd_MM_yyyy__hh_mm_ss");
        Date currentTime = new Date();
        String timeStamp = simpleDateFormat.format(currentTime);

        StorageReference storageRef = mStorageRef.child("videos/Video_" + timeStamp + ".mp4");

        status.postValue("uploading video ...");

        storageRef.putFile(videoURI)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("firebase", "video failed to upload");
                        status.postValue("video not uploaded check your internet");
                    }
                })
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        // String downloadUrl = taskSnapshot.getMetadata().getReference().getDownloadUrl().toString();
                        Log.d("firebase", "video uploaded");
                        status.postValue("video uploaded");

                    }
                }).continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }

                // Continue with the task to get the download URL
                return storageRef.getDownloadUrl();
            }

        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    String videoUrl = task.getResult().toString();
                    Log.d("firebase", "video url: " + videoUrl.toString());

                    addVideoEntry(videoUrl, currentTime, location, getUser());
                } else {
                    Log.d("firebase", "failed to get video url");
                    status.postValue("failed to add video entry");
                }
            }
        });
    }

    public void addVideoEntry(String currentVideoUrl, Date time, Location location, FirebaseUser user){
        GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> sosEntry = new HashMap<>();
        sosEntry.put("user", user.getUid());
        sosEntry.put("date", time);
        sosEntry.put("link", currentVideoUrl);
        sosEntry.put("location", currentLocation);

        // Add a new document with a generated ID
        db.collection("videos")
                .add(sosEntry)
                .addOnSuccessListener(documentReference ->{
                    Log.d("firebase", "DocumentSnapshot added with ID: " + documentReference.getId());
                    status.postValue("video entry added");
                })
                .addOnFailureListener(e -> {
                    Log.w("firebase", "Error adding document", e);
                    status.postValue("failed to add video entry");
                });
    }

    public MutableLiveData<String> getStatus() {
        return status;
    }

    public MutableLiveData<Boolean> getServiceStatus() {
        return serviceStatus;
    }

    public void verfiyPassword(String password, OnCompleteListener<AuthResult> listener){

        mAuth.signInWithEmailAndPassword(mAuth.getCurrentUser().getEmail(), password)
                .addOnCompleteListener(listener);
    }

    public void startAutomaticDetection(){
        ActivityTransition.Builder builder = new ActivityTransition.Builder();
        ActivityTransition mTransition =
                builder
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .setActivityType(DetectedActivity.RUNNING)
                .build();

        List<ActivityTransition> transitionList = new ArrayList<>();
        transitionList.add(mTransition);

        ActivityTransitionRequest mTransitionRequest = new ActivityTransitionRequest(transitionList);

        Intent sosIntent = new Intent(getApplication(), MainActivity.class);

        sosIntent.setAction(ACTION_SOS)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

         mSosPendingIntent = PendingIntent.getActivity(getApplication(), SOS_REQUEST_CODE, sosIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        ActivityRecognition.getClient(getApplication())
                .requestActivityTransitionUpdates(mTransitionRequest, mSosPendingIntent);
        serviceStatus.postValue(true);
    }

    public void stopAutomaticDetection(){
        ActivityRecognition.getClient(getApplication()).removeActivityTransitionUpdates(mSosPendingIntent);
        serviceStatus.postValue(false);
    }
}
