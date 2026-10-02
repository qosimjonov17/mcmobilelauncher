package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.accounts.LauncherProfile;
import net.kdt.pojavlaunch.accounts.LauncherProfileManager;
import net.kdt.pojavlaunch.cloud.CloudSync;
import net.kdt.pojavlaunch.cloud.DriveAppDataClient;
import net.kdt.pojavlaunch.cloud.GoogleAuth;
import net.kdt.pojavlaunch.cloud.SyncSnapshot;

/**
 * Google sign-in for the launcher profile, plus backup/restore of launcher data to the Google
 * Drive app data folder. Works with or without a Microsoft account and never touches one.
 */
public class CloudSyncFragment extends Fragment {
    public static final String TAG = "CLOUD_SYNC_FRAGMENT";
    private static final String LOG_TAG = "CloudSyncFragment";

    private enum Action { BACKUP, RESTORE }

    private ActivityResultLauncher<IntentSenderRequest> mConsentLauncher;
    @Nullable private Action mPendingAction;
    private boolean mBusy;
    private EditText mNicknameEdit;
    private View mIdentitySection, mNicknameSave;
    @Nullable private String mDisplayedIdentity;

    private TextView mAccountText, mLastSyncText, mStatusText;
    private View mProgress, mSignInButton, mBackupButton, mRestoreButton, mSignOutButton;

    public CloudSyncFragment() {
        super(R.layout.fragment_cloud_sync);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Shown when Drive access was never granted or was revoked by the user
        mConsentLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            Action action = mPendingAction;
            mPendingAction = null;
            if (action == null) return;
            if (result.getResultCode() != Activity.RESULT_OK) {
                finish(R.string.cloud_sync_permission_denied);
                return;
            }
            try {
                AuthorizationResult authorization = Identity.getAuthorizationClient(requireActivity())
                        .getAuthorizationResultFromIntent(result.getData());
                runWithToken(action, authorization.getAccessToken(), false);
            } catch (ApiException e) {
                Log.w(LOG_TAG, "Drive authorization failed", e);
                finish(R.string.cloud_sync_permission_denied);
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mIdentitySection = view.findViewById(R.id.mayoq_identity_section);
        mNicknameEdit = view.findViewById(R.id.mayoq_nickname_edit);
        mNicknameSave = view.findViewById(R.id.mayoq_nickname_save);
        mDisplayedIdentity = null;
        mNicknameSave.setOnClickListener(v -> saveNickname());
        mAccountText = view.findViewById(R.id.cloud_account_text);
        mLastSyncText = view.findViewById(R.id.cloud_last_sync_text);
        mStatusText = view.findViewById(R.id.cloud_status_text);
        mProgress = view.findViewById(R.id.cloud_progress);
        mSignInButton = view.findViewById(R.id.cloud_sign_in_button);
        mBackupButton = view.findViewById(R.id.cloud_backup_button);
        mRestoreButton = view.findViewById(R.id.cloud_restore_button);
        mSignOutButton = view.findViewById(R.id.cloud_sign_out_button);

        mSignInButton.setOnClickListener(v -> signIn());
        mSignOutButton.setOnClickListener(v -> signOut());
        mBackupButton.setOnClickListener(v -> start(Action.BACKUP));
        mRestoreButton.setOnClickListener(v -> new AlertDialog.Builder(v.getContext())
                .setTitle(R.string.cloud_sync_restore)
                .setMessage(R.string.cloud_sync_restore_confirm)
                .setPositiveButton(R.string.cloud_sync_restore, (d, w) -> start(Action.RESTORE))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        updateUi();
    }

    private void saveNickname() {
        LauncherProfile google = LauncherProfileManager.getGoogleProfile();
        if (google == null) return;
        try {
            LauncherProfileManager.saveMinecraftNickname(google.id, mNicknameEdit.getText().toString());
            mNicknameEdit.setError(null);
            finish(R.string.mayoq_nickname_saved);
        } catch (IllegalArgumentException error) {
            mNicknameEdit.setError(getString(R.string.mayoq_nickname_invalid));
        } catch (IllegalStateException error) {
            finish(R.string.mayoq_identity_save_failed);
        }
    }

    private void signIn() {
        if (!Tools.isOnline(requireContext())) {
            mStatusText.setText(R.string.cloud_sync_offline);
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        setBusy(true);
        GoogleAuth.signIn(requireActivity(), new GoogleAuth.SignInCallback() {
            @Override
            public void onSignedIn(String subject, String email, @Nullable String displayName) {
                try {
                    LauncherProfile previous = LauncherProfileManager.getGoogleProfile();
                    LauncherProfile google = LauncherProfileManager.saveGoogle(subject, email, displayName);
                    if (previous == null || !previous.id.equals(google.id)) CloudSync.clearState(appContext);
                    finish(R.string.google_sign_in_done);
                } catch (IllegalStateException error) {
                    finish(R.string.mayoq_identity_save_failed);
                }
            }

            @Override
            public void onCancelled() {
                finish(0);
            }

            @Override
            public void onError(@NonNull String message, @Nullable Throwable cause) {
                Log.w(LOG_TAG, "Google sign-in failed", cause);
                if (!isAdded() || getView() == null) return;
                setBusy(false);
                mStatusText.setText(message);
            }
        });
    }

    private void signOut() {
        setBusy(true);
        GoogleAuth.signOut(requireContext(), () -> {
            try {
                LauncherProfileManager.signOutGoogle();
            } catch (IllegalStateException error) {
                finish(R.string.mayoq_identity_save_failed);
                return;
            }
            Context context = getContext();
            if (context != null) CloudSync.clearState(context);
            finish(R.string.google_sign_out_done);
        });
    }

    private void start(Action action) {
        if (mBusy) return;
        if (LauncherProfileManager.getGoogleProfile() == null) {
            mStatusText.setText(R.string.cloud_sync_sign_in_first);
            return;
        }
        if (!Tools.isOnline(requireContext())) {
            mStatusText.setText(R.string.cloud_sync_offline);
            return;
        }
        setBusy(true);
        mStatusText.setText(action == Action.BACKUP ? R.string.cloud_sync_backing_up : R.string.cloud_sync_restoring);
        requestToken(action, true);
    }

    /** Gets a drive.appdata token, showing the consent screen when needed */
    private void requestToken(Action action, boolean allowRetry) {
        LauncherProfile google = LauncherProfileManager.getGoogleProfile();
        if (google == null || google.email == null) {
            finish(R.string.cloud_sync_sign_in_first);
            return;
        }
        GoogleAuth.authorizeDriveAppData(requireActivity(), google.email)
                .addOnSuccessListener(requireActivity(), authorization -> {
                    if (authorization.hasResolution() && authorization.getPendingIntent() != null) {
                        mPendingAction = action;
                        mConsentLauncher.launch(new IntentSenderRequest.Builder(
                                authorization.getPendingIntent().getIntentSender()).build());
                    } else {
                        runWithToken(action, authorization.getAccessToken(), allowRetry);
                    }
                })
                .addOnFailureListener(requireActivity(), e -> {
                    Log.w(LOG_TAG, "Drive authorization request failed", e);
                    finish(R.string.cloud_sync_google_services_error);
                });
    }

    private void runWithToken(Action action, @Nullable String accessToken, boolean allowRetry) {
        if (accessToken == null) {
            finish(R.string.cloud_sync_permission_denied);
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        sExecutorService.execute(() -> {
            try {
                DriveAppDataClient drive = new DriveAppDataClient(accessToken);
                if (action == Action.BACKUP) {
                    drive.uploadBackup(CloudSync.collect().toJson());
                    CloudSync.markBackedUp(appContext);
                    Tools.runOnUiThread(() -> finish(R.string.cloud_sync_backup_done));
                } else {
                    String json = drive.downloadBackup();
                    if (json == null) {
                        Tools.runOnUiThread(() -> finish(R.string.cloud_sync_no_backup));
                        return;
                    }
                    CloudSync.apply(appContext, SyncSnapshot.fromJson(json));
                    Tools.runOnUiThread(() -> finish(R.string.cloud_sync_restore_done));
                }
            } catch (DriveAppDataClient.AuthException e) {
                // Expired token: drop it and ask once more. Revoked access: the retry shows the consent screen.
                Log.w(LOG_TAG, "Drive rejected the token", e);
                try {
                    GoogleAuthUtil.clearToken(appContext, accessToken);
                } catch (Exception clearError) {
                    Log.w(LOG_TAG, "Could not clear the rejected token", clearError);
                }
                Tools.runOnUiThread(() -> {
                    if (allowRetry && isAdded()) requestToken(action, false);
                    else finish(R.string.cloud_sync_permission_denied);
                });
            } catch (JsonParseException e) {
                Log.w(LOG_TAG, "Backup is unreadable", e);
                Tools.runOnUiThread(() -> finish(R.string.cloud_sync_bad_backup));
            } catch (Exception e) {
                Log.w(LOG_TAG, "Cloud sync failed", e);
                Tools.runOnUiThread(() -> finish(R.string.cloud_sync_network_error));
            }
        });
    }

    /** Ends the current operation and shows a status message (0 for none) */
    private void finish(@StringRes int message) {
        mBusy = false;
        if (!isAdded() || getView() == null) return;
        setBusy(false);
        mStatusText.setText(message == 0 ? "" : getString(message));
        updateUi();
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        mSignInButton.setEnabled(!busy);
        mBackupButton.setEnabled(!busy);
        mRestoreButton.setEnabled(!busy);
        mSignOutButton.setEnabled(!busy);
        mNicknameEdit.setEnabled(!busy);
        mNicknameSave.setEnabled(!busy);
    }

    private void updateUi() {
        LauncherProfile google = LauncherProfileManager.getGoogleProfile();
        boolean signedIn = google != null;
        mAccountText.setText(signedIn
                ? getString(R.string.google_signed_in_as, google.displayName, google.email)
                : getString(R.string.google_not_signed_in));
        mIdentitySection.setVisibility(signedIn && google.playerUuid != null ? View.VISIBLE : View.GONE);
        if (signedIn && !google.id.equals(mDisplayedIdentity)) {
            mNicknameEdit.setText(google.minecraftNickname == null ? "" : google.minecraftNickname);
            mDisplayedIdentity = google.id;
        } else if (!signedIn) {
            mDisplayedIdentity = null;
            mNicknameEdit.setText("");
        }
        mSignInButton.setVisibility(signedIn && google.googleSubject != null ? View.GONE : View.VISIBLE);
        mBackupButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        mRestoreButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        mSignOutButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);

        long lastBackup = CloudSync.getLastBackup(requireContext());
        mLastSyncText.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        mLastSyncText.setText(lastBackup == 0
                ? getString(R.string.cloud_sync_never)
                : getString(R.string.cloud_sync_last_backup, DateUtils.getRelativeTimeSpanString(lastBackup)));
    }
}
