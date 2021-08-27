package com.example.autonomousos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.autonomousos.model.VideoModel;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

    public static final int LOCATION_REQUEST_CODE = 506;
    public static final int REQUEST_VIDEO_CAPTURE = 1;
    public static final int RC_SIGN_IN = 105;
    public static final int ENABLE_GPS_CODE = 456;

    private BaseViewModel mBaseViewModel;
    private FusedLocationProviderClient mFusedLocationClient;
    private Uri mCurrentVideoUri;

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBaseViewModel = new ViewModelProvider(this).get(BaseViewModel.class);
        mBaseViewModel.init();
        mBaseViewModel.getVideoEntries().observe(this, new Observer<ArrayList<VideoModel>>() {
            @Override
            public void onChanged(ArrayList<VideoModel> videoModels) {
                Log.d("firebase", "onChanged: videos entries changed");
            }
        });

        Intent intent = getIntent();
        if(intent != null){
            String action = intent.getAction();
            Log.d("intent", "intent recieved");

            if(action == BaseViewModel.ACTION_SOS){
                navigateToAutomaticFragment();
            }
        }


        if (mBaseViewModel.getUser() == null) {
            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.EmailBuilder().build(),
                    new AuthUI.IdpConfig.AnonymousBuilder().build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .setLogo(R.raw.logo)
                            .build(),
                    RC_SIGN_IN);
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings, R.id.navigation_automatic)
                .build();
        NavController mNavController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, mNavController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, mNavController);


        mBaseViewModel.getStatus().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s != null) {
                    Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
                    mBaseViewModel.getStatus().setValue(null);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {

            mCurrentVideoUri = data.getData();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
                }
            } else {
                if (!isLocationEnabled(this)) {
                    showGPSNotEnabledDialog(this);
                    return;
                }
                if(!isInternetEnabled(this)){
                    Toast.makeText(this, "no internet, video didn't upload", Toast.LENGTH_LONG).show();
                    return;
                }
                startVideoUpload();
            }

        } else if (requestCode == ENABLE_GPS_CODE) {
            startVideoUpload();

        } else if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in
                Log.d("firebase", "logged in");
            } else {
                Log.d("firebase", "failed login");
                finish();
            }
        }
    }

    private boolean isInternetEnabled(MainActivity mainActivity) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!isLocationEnabled(this)) {
                    showGPSNotEnabledDialog(this);
                    return;
                }
                startVideoUpload();

            } else {
                Toast.makeText(getApplicationContext(), "location permission denied enable it in settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void startVideoUpload() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Task<Location> locationTask = mFusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null);
        locationTask.addOnCompleteListener(task -> {
            Location location = task.getResult();
            if (location == null) {
                Log.d("firebase", "location is null");
                Toast.makeText(getApplicationContext(), "couldn't get location, video not uploaded", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadVideo(mCurrentVideoUri, location);
        });
    }

    public void uploadVideo(Uri videoURI, Location location){
        mBaseViewModel.uploadVideo(videoURI, location);
    }

    public void handleClick(View view) {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30);
        takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
    }

    public void showGPSNotEnabledDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("please enable gps")
                .setMessage("This app needs GPS to work")
                .setCancelable(false)
                .setPositiveButton("enable", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //Prompt the user once explanation has been shown
                        startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), ENABLE_GPS_CODE);

                    }
                })
                .create()
                .show();
    }

    public static Boolean isLocationEnabled(Context context){
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }


    @SuppressLint("ResourceType")
    public void onSignOut(View view){
        mBaseViewModel.signOut();

        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.AnonymousBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .setLogo(R.raw.logo)
                        .build(),
                RC_SIGN_IN);
    }

    public void simulateAutomaticSos(View view){
        navigateToAutomaticFragment();
    }

    public void navigateToAutomaticFragment(){
        NavController mNavController = Navigation.findNavController(this, R.id.nav_host_fragment);
        mNavController.navigate(R.id.navigation_automatic);
    }
}

