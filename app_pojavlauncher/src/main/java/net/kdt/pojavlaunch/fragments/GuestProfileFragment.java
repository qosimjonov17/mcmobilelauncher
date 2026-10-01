package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.accounts.LauncherProfile;
import net.kdt.pojavlaunch.accounts.LauncherProfileManager;

/** Creates or renames the guest launcher profile. A guest profile only names the launcher user; it does not unlock the game. */
public class GuestProfileFragment extends Fragment {
    public static final String TAG = "GUEST_PROFILE_FRAGMENT";

    public GuestProfileFragment() {
        super(R.layout.fragment_guest_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText nicknameEdit = view.findViewById(R.id.guest_nickname_edit);
        LauncherProfile current = LauncherProfileManager.getCurrent();
        if (savedInstanceState == null && current != null && current.type == LauncherProfile.Type.GUEST) {
            nicknameEdit.setText(current.displayName);
        }

        view.findViewById(R.id.guest_continue_button).setOnClickListener(v -> {
            Context context = v.getContext();
            String nickname = nicknameEdit.getText().toString();
            if (!LauncherProfileManager.isValidNickname(nickname)) {
                Tools.dialog(context, context.getString(R.string.guest_profile_bad_nickname_title),
                        context.getString(R.string.guest_profile_bad_nickname_text));
                return;
            }
            LauncherProfile guest = LauncherProfileManager.saveGuest(nickname);
            Toast.makeText(context, context.getString(R.string.guest_profile_welcome, guest.displayName), Toast.LENGTH_SHORT).show();
            Tools.backToMainMenu(requireActivity());
        });
    }
}
