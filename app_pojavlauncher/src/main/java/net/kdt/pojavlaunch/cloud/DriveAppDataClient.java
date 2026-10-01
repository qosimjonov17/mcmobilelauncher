package net.kdt.pojavlaunch.cloud;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Google Drive v3 REST client for the hidden app data folder (drive.appdata scope).
 * The app can only see files it created there; the user's own Drive files are never visible.
 * All methods block, so call them off the main thread.
 */
public class DriveAppDataClient {
    public static final String SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata";
    public static final String BACKUP_FILE_NAME = "mayoq_launcher_backup.json";

    private static final String FILES_URL = "https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files";
    private static final int TIMEOUT_MS = 30000;

    /** The access token was rejected (expired or revoked) or lacks the drive.appdata scope */
    public static class AuthException extends IOException {
        public AuthException(String message) { super(message); }
    }

    private final String mAccessToken;

    public DriveAppDataClient(String accessToken) {
        mAccessToken = accessToken;
    }

    /** @return the backup file id, or null when no backup exists yet */
    @Nullable
    public String findBackupFileId() throws IOException {
        String query = URLEncoder.encode("name = '" + BACKUP_FILE_NAME + "' and trashed = false", "UTF-8");
        HttpURLConnection connection = open(FILES_URL + "?spaces=appDataFolder&fields=files(id,modifiedTime)&q=" + query, "GET");
        JsonObject response = JsonParser.parseString(readResponse(connection)).getAsJsonObject();
        JsonArray files = response.getAsJsonArray("files");
        if (files == null || files.size() == 0) return null;
        return files.get(0).getAsJsonObject().get("id").getAsString();
    }

    /** @return the backup content, or null when no backup exists yet */
    @Nullable
    public String downloadBackup() throws IOException {
        String fileId = findBackupFileId();
        if (fileId == null) return null;
        HttpURLConnection connection = open(FILES_URL + "/" + fileId + "?alt=media", "GET");
        return readResponse(connection);
    }

    /** Creates the backup file or replaces its content */
    public void uploadBackup(String json) throws IOException {
        String fileId = findBackupFileId();
        if (fileId == null) {
            createBackup(json);
        } else {
            // HttpURLConnection has no PATCH; Google APIs accept the method override header
            HttpURLConnection connection = open(UPLOAD_URL + "/" + fileId + "?uploadType=media", "POST");
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            writeBody(connection, json.getBytes(StandardCharsets.UTF_8));
            readResponse(connection);
        }
    }

    private void createBackup(String json) throws IOException {
        String boundary = "mayoq" + System.nanoTime();
        JsonObject metadata = new JsonObject();
        metadata.addProperty("name", BACKUP_FILE_NAME);
        JsonArray parents = new JsonArray();
        parents.add("appDataFolder");
        metadata.add("parents", parents);

        String body = "--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + metadata + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + json + "\r\n"
                + "--" + boundary + "--";

        HttpURLConnection connection = open(UPLOAD_URL + "?uploadType=multipart", "POST");
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        writeBody(connection, body.getBytes(StandardCharsets.UTF_8));
        readResponse(connection);
    }

    private HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Authorization", "Bearer " + mAccessToken);
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, byte[] body) throws IOException {
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        try {
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new AuthException("Google Drive rejected the request (HTTP " + code + "): " + readError(connection));
            }
            if (code < 200 || code >= 300) {
                throw new IOException("Google Drive request failed (HTTP " + code + "): " + readError(connection));
            }
            try (InputStream in = connection.getInputStream()) {
                return IOUtils.toString(in, StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readError(HttpURLConnection connection) {
        try (InputStream err = connection.getErrorStream()) {
            return err == null ? "" : IOUtils.toString(err, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
