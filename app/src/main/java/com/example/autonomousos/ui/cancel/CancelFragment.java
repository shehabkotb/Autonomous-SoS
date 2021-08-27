package com.example.autonomousos.ui.cancel;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.autonomousos.BaseViewModel;
import com.example.autonomousos.MainActivity;
import com.example.autonomousos.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

import java.util.Locale;


public class CancelFragment extends Fragment {

    private BaseViewModel mBaseViewModel;
    private TextView countText;
    private EditText mPassword;
    private CountDownTimer mTimer;
    TextToSpeech tts;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_cancel, container, false);

        tts = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {

                if (status == TextToSpeech.SUCCESS) {
                    Log.d("speech", " ==== PASS");
                    tts.setLanguage(Locale.ENGLISH);
                    tts.setPitch((float) 1);
                    tts.setSpeechRate((float) 1);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts.speak("You seem to be in distress, we will notify authorities in 30 seconds, if not canceled", TextToSpeech.QUEUE_ADD, null, "560");
                    }
                }
            }
        });

        mBaseViewModel = new ViewModelProvider(this).get(BaseViewModel.class);
        mBaseViewModel.init();

        countText = root.findViewById(R.id.countdown_text);
        mPassword = root.findViewById(R.id.password_input);

        Button cancelBtn = root.findViewById(R.id.cancel_button);


        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(mPassword.getText().toString().isEmpty()){
                    Toast.makeText(getActivity(), "wrong password", Toast.LENGTH_SHORT).show();
                    return;
                }

                mBaseViewModel.verfiyPassword(mPassword.getText().toString(), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d("firebase", "canceled sos");
                            mTimer.cancel();
                            getParentFragmentManager().popBackStackImmediate();

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w("firebase", "wrong password cancel", task.getException());
                            Toast.makeText(getActivity(), "wrong password", Toast.LENGTH_SHORT).show();
                            mPassword.getText().clear();
                        }
                    }
                });
            }
        });


        // code to make the back button unresponsive
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        root.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        return true;
                    }
                }
                return false;
            }
        });
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTimer = new CountDownTimer(30000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                int timeLeft = (int) millisUntilFinished / 1000;
                countText.setText("" +timeLeft);
            }

            @Override
            public void onFinish() {
                Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30);
                takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                getActivity().startActivityForResult( takeVideoIntent, MainActivity.REQUEST_VIDEO_CAPTURE);
                Navigation.findNavController(getView()).navigate(R.id.navigation_home);
            }
        }.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mTimer.cancel();
    }


}