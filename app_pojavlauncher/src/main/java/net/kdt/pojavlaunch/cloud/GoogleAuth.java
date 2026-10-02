package net.kdt.pojavlaunch.cloud;

import android.app.Activity;
import android.accounts.Account;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import android.content.Context;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import net.kdt.pojavlaunch.R;

import java.util.Collections;

/**
 * Google identity for launcher profiles and cloud sync. Independent from Microsoft/Minecraft
 * authentication: nothing here reads or creates Minecraft accounts, and a Google sign-in never
 * grants access to the game.
 */
public final class GoogleAuth {
    private GoogleAuth() {}

    public interface SignInCallback {
        void onSignedIn(String subject, String email, @Nullable String displayName);
        void onCancelled();
        void onError(@NonNull String message, @Nullable Throwable cause);
    }

    /** Shows the "Sign in with Google" sheet (Credential Manager) using the project's Web client ID */
    public static void signIn(Activity activity, SignInCallback callback) {
        String webClientId = activity.getString(R.string.google_web_client_id);
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(webClientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();

        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential google = GoogleIdTokenCredential.Companion.createFrom(credential.getData());
                                String[] parts = google.getIdToken().split("\\.", -1);
                                if (parts.length != 3) throw new IllegalArgumentException();
                                String payload = new String(Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8);
                                String subject = GoogleIdentityClaims.subject(payload, webClientId, System.currentTimeMillis() / 1000);
                                callback.onSignedIn(subject, google.getId(), google.getDisplayName());
                            } catch (Exception error) {
                                callback.onError(activity.getString(R.string.google_sign_in_unexpected), null);
                            }
                        } else {
                            callback.onError(activity.getString(R.string.google_sign_in_unexpected), null);
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) callback.onCancelled();
                        else if (e instanceof NoCredentialException) callback.onError(activity.getString(R.string.google_sign_in_no_account), e);
                        else callback.onError(activity.getString(R.string.google_sign_in_failed), e);
                    }
                });
    }

    /** Clears the remembered Google credential so the next sign-in shows the account picker again */
    public static void signOut(Context context, Runnable onDone) {
        CredentialManager.create(context).clearCredentialStateAsync(new ClearCredentialStateRequest(),
                new CancellationSignal(), ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { onDone.run(); }
                    // Local sign-out still happens; the credential will simply be offered again next time
                    @Override public void onError(ClearCredentialException e) { onDone.run(); }
                });
    }

    /**
     * Requests an access token for the hidden Drive app data folder only. If the user has not
     * granted it yet (or revoked it), the result carries a PendingIntent to show the consent screen.
     */
    public static Task<AuthorizationResult> authorizeDriveAppData(Context context, String email) {
        AuthorizationRequest request = AuthorizationRequest.builder()
                .setAccount(new Account(email, "com.google"))
                .setRequestedScopes(Collections.singletonList(new Scope(DriveAppDataClient.SCOPE_DRIVE_APPDATA)))
                .build();
        return Identity.getAuthorizationClient(context).authorize(request);
    }
}
