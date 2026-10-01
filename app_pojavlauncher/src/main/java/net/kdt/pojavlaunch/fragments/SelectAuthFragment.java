package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment(){
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mMicrosoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button mLocalButton = view.findViewById(R.id.button_local_authentication);
        Button mGuestButton = view.findViewById(R.id.button_guest_profile);

        mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        // Offline Minecraft accounts stay limited to devices that already hold a licensed account
        mLocalButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity(), () -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null)));
        // A guest profile only names the launcher user, so it needs no Minecraft account
        mGuestButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), GuestProfileFragment.class, GuestProfileFragment.TAG, null));
    }
}
