package com.example.autonomousos.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.autonomousos.BaseViewModel;
import com.example.autonomousos.R;

public class SettingsFragment extends Fragment {
    private BaseViewModel mBaseViewModel;
    private Switch mServiceSwitchroot;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        mBaseViewModel = new ViewModelProvider(this).get(BaseViewModel.class);
        mBaseViewModel.init();

        mServiceSwitchroot = root.findViewById(R.id.service_switch);

        mServiceSwitchroot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(!isChecked){
                    mBaseViewModel.stopAutomaticDetection();
                } else {
                    mBaseViewModel.startAutomaticDetection();
                }
            }
        });

        mBaseViewModel.getServiceStatus().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                mServiceSwitchroot.setChecked(aBoolean);
            }
        });

        return root;
    }
}