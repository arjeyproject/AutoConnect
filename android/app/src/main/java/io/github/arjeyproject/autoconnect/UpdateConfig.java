package io.github.arjeyproject.autoconnect;

final class UpdateConfig {
    static final String API_URL = "https://api.github.com/repos/arjeyproject/AutoConnect/releases?per_page=30";
    static final String RELEASE_ASSET = "AutoConnect-v%s-Android-Universal.apk";
    static final String CHECKSUM_ASSET = "SHA256SUMS.txt";
    static final String RELEASE_DOWNLOAD_PREFIX = "https://github.com/arjeyproject/AutoConnect/releases/download/";
    static final String PREFS = "app_updates";
    static final String KEY_LATEST_VERSION = "latest_version";
    static final String KEY_RELEASE_NOTES = "release_notes";
    static final String KEY_DOWNLOAD_URL = "download_url";
    static final String KEY_CHECKSUM = "checksum";
    static final String KEY_DOWNLOAD_ID = "download_id";
    static final String KEY_APK_PATH = "apk_path";
    static final String KEY_NOTIFIED_VERSION = "notified_version";
    static final String KEY_AUTO_DOWNLOAD = "auto_download";
    static final String ACTION_STATE = "io.github.arjeyproject.autoconnect.UPDATE_STATE";
    static final String ACTION_DOWNLOAD = "io.github.arjeyproject.autoconnect.DOWNLOAD_UPDATE";
    static final String ACTION_INSTALL = "io.github.arjeyproject.autoconnect.INSTALL_UPDATE";

    private UpdateConfig() { }
}
