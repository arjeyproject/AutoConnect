package io.github.arjeyproject.autoconnect;

import android.animation.ValueAnimator;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import io.github.arjeyproject.autoconnect.databinding.ActivityMainBinding;

import java.util.LinkedHashSet;
import java.util.Set;

public final class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST = 41;
    private static final int NOTIFICATION_REQUEST = 42;
    private static final int APPS_REQUEST = 43;
    private static final String INTERNAL_PERMISSION = "io.github.arjeyproject.autoconnect.permission.INTERNAL";
    private static final String PAGE_CONNECT = "connect";
    private static final String PAGE_TUNNEL = "tunnel";
    private static final String PAGE_SETTINGS = "settings";
    private static final String PAGE_ABOUT = "about";

    private ActivityMainBinding binding;
    private SharedPreferences preferences;
    private String state = "disconnected";
    private String page = PAGE_CONNECT;
    private boolean receiverRegistered;
    private String endpoint = "";
    private final Handler updateHandler = new Handler(Looper.getMainLooper());

    private final Runnable updateProgressPoll = new Runnable() {
        @Override public void run() {
            if (binding == null) return;
            renderUpdateState();
            if ("downloading".equals(getSharedPreferences(UpdateConfig.PREFS, MODE_PRIVATE).getString("status", ""))) updateHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AutoConnectVpnService.ACTION_STATUS.equals(intent.getAction())) {
                endpoint = intent.getStringExtra("endpoint");
                renderState(intent.getStringExtra("state"), intent.getStringExtra("message"));
            }
            else if (AutoConnectVpnService.ACTION_STATS.equals(intent.getAction())) renderStats(intent);
            else if (UpdateConfig.ACTION_STATE.equals(intent.getAction())) renderUpdateState();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences("autoconnect", MODE_PRIVATE);
        applyLanguage(preferences.getString("language", "en"), false);
        AppCompatDelegate.setDefaultNightMode(themeMode(preferences.getInt("theme", 0)));
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        applyInsets();
        enterAnimation();
        setupDropdowns();
        restoreSettings();
        setupTabs();
        setupActions();
        requestNotificationPermission();
        AppUpdateManager.initialize(this);
        binding.statusVersion.setText(getString(R.string.version_format, BuildConfig.VERSION_NAME));
        binding.currentVersionValue.setText(BuildConfig.VERSION_NAME);
        binding.autoDownloadSwitch.setChecked(getSharedPreferences(UpdateConfig.PREFS, MODE_PRIVATE).getBoolean(UpdateConfig.KEY_AUTO_DOWNLOAD, false));
        renderUpdateState();
        renderState("disconnected", getString(R.string.status_ready_message));
        showPage(PAGE_CONNECT);
        if (getIntent().getBooleanExtra(AutoConnectTileService.EXTRA_CONNECT_FROM_TILE, false)) {
            getIntent().removeExtra(AutoConnectTileService.EXTRA_CONNECT_FROM_TILE);
            binding.root.post(this::connect);
        }
    }

    /** Content stays clear of the status bar, cutouts and the home indicator. */
    private void applyInsets() {
        final int headerHeight = binding.toolbar.getLayoutParams().height;
        final int tabMargin = ((android.view.ViewGroup.MarginLayoutParams) binding.tabBar.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.mainContent.setPadding(bars.left, 0, bars.right, 0);
            binding.toolbar.setPadding(binding.toolbar.getPaddingLeft(), bars.top, binding.toolbar.getPaddingRight(), binding.toolbar.getPaddingBottom());
            android.view.ViewGroup.LayoutParams header = binding.toolbar.getLayoutParams();
            header.height = headerHeight + bars.top;
            binding.toolbar.setLayoutParams(header);
            android.view.ViewGroup.MarginLayoutParams tabs = (android.view.ViewGroup.MarginLayoutParams) binding.tabBar.getLayoutParams();
            tabs.bottomMargin = tabMargin + bars.bottom;
            binding.tabBar.setLayoutParams(tabs);
            return insets;
        });
    }

    private void enterAnimation() {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        binding.getRoot().setAlpha(0f);
        binding.getRoot().setTranslationY(16f);
        binding.getRoot().post(() -> binding.getRoot().animate().alpha(1f).translationY(0f).setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start());
    }

    private void setupDropdowns() {
        setAdapter(binding.protocolInput, R.array.protocol_labels);
        setAdapter(binding.scanInput, R.array.scan_labels);
        setAdapter(binding.transportInput, R.array.transport_labels);
        setAdapter(binding.ipInput, R.array.ip_labels);
        setAdapter(binding.obfuscationInput, R.array.obfuscation_labels);
        setAdapter(binding.logInput, R.array.log_labels);
        setAdapter(binding.themeInput, R.array.theme_labels);
        setAdapter(binding.languageInput, R.array.language_labels);
        binding.protocolInput.setOnItemClickListener((p, v, position, id) -> { binding.protocolInput.setTag(position); updateModeUi(); saveSettings(); });
        binding.scanInput.setOnItemClickListener((p, v, position, id) -> { binding.scanInput.setTag(position); saveSettings(); });
        binding.transportInput.setOnItemClickListener((p, v, position, id) -> { binding.transportInput.setTag(position); saveSettings(); });
        binding.ipInput.setOnItemClickListener((p, v, position, id) -> { binding.ipInput.setTag(position); saveSettings(); });
        binding.obfuscationInput.setOnItemClickListener((p, v, position, id) -> { binding.obfuscationInput.setTag(position); saveSettings(); });
        binding.logInput.setOnItemClickListener((p, v, position, id) -> { binding.logInput.setTag(position); saveSettings(); });
        binding.themeInput.setOnItemClickListener((p, v, position, id) -> { binding.themeInput.setTag(position); saveSettings(); applyTheme(position); });
        binding.languageInput.setOnItemClickListener((p, v, position, id) -> {
            String language = position == 1 ? "fa" : "en";
            preferences.edit().putString("language", language).apply();
            applyLanguage(language, true);
        });
    }

    private void setAdapter(MaterialAutoCompleteTextView view, int arrayId) {
        view.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, getResources().getStringArray(arrayId)));
    }

    // ---------------------------------------------------------------- navigation

    private void setupTabs() {
        binding.tabConnect.setOnClickListener(v -> selectTab(v, PAGE_CONNECT));
        binding.tabTunnel.setOnClickListener(v -> selectTab(v, PAGE_TUNNEL));
        binding.tabSettings.setOnClickListener(v -> selectTab(v, PAGE_SETTINGS));
        binding.tabAbout.setOnClickListener(v -> selectTab(v, PAGE_ABOUT));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!PAGE_CONNECT.equals(page)) showPage(PAGE_CONNECT);
                else finish();
            }
        });
    }

    private void selectTab(View source, String destination) {
        if (destination.equals(page)) return;
        source.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        showPage(destination);
    }

    private void showPage(String destination) {
        page = destination;
        binding.homePage.setVisibility(PAGE_CONNECT.equals(page) ? View.VISIBLE : View.GONE);
        binding.configurationsPage.setVisibility(PAGE_TUNNEL.equals(page) ? View.VISIBLE : View.GONE);
        binding.settingsPage.setVisibility(PAGE_SETTINGS.equals(page) ? View.VISIBLE : View.GONE);
        binding.aboutPage.setVisibility(PAGE_ABOUT.equals(page) ? View.VISIBLE : View.GONE);

        paintTab(binding.tabConnect, binding.tabConnectIcon, binding.tabConnectLabel, PAGE_CONNECT.equals(page));
        paintTab(binding.tabTunnel, binding.tabTunnelIcon, binding.tabTunnelLabel, PAGE_TUNNEL.equals(page));
        paintTab(binding.tabSettings, binding.tabSettingsIcon, binding.tabSettingsLabel, PAGE_SETTINGS.equals(page));
        paintTab(binding.tabAbout, binding.tabAboutIcon, binding.tabAboutLabel, PAGE_ABOUT.equals(page));

        View visible = PAGE_TUNNEL.equals(page) ? binding.configurationsPage
                : PAGE_SETTINGS.equals(page) ? binding.settingsPage
                : PAGE_ABOUT.equals(page) ? binding.aboutPage : binding.homePage;
        if (ValueAnimator.areAnimatorsEnabled()) {
            visible.setAlpha(0f);
            visible.setTranslationY(10f);
            visible.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
    }

    private void paintTab(View container, ImageView icon, TextView caption, boolean selected) {
        container.setSelected(selected);
        int tint = ContextCompat.getColor(this, selected ? R.color.brand : R.color.muted);
        icon.setColorFilter(tint);
        caption.setTextColor(tint);
    }

    // ---------------------------------------------------------------- actions

    private void setupActions() {
        binding.connectButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (shouldDisconnect()) disconnect(); else connect();
        });
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            preferences.edit().putString("mode", checkedId == R.id.proxy_mode_button ? "manual" : checkedId == R.id.smart_mode_button ? "smart" : "vpn").apply();
            updateModeUi();
        });
        binding.splitSwitch.setOnCheckedChangeListener((button, checked) -> { binding.splitContainer.setVisibility(checked ? View.VISIBLE : View.GONE); saveSettings(); });
        binding.routingGroup.setOnCheckedChangeListener((group, checkedId) -> { saveSettings(); updateSelectedCount(); });
        binding.chooseAppsButton.setOnClickListener(v -> openAppSelection());
        binding.advancedToggle.setOnClickListener(v -> {
            boolean show = binding.advancedContainer.getVisibility() != View.VISIBLE;
            binding.advancedContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            binding.advancedToggle.setText(show ? R.string.hide_advanced : R.string.show_advanced);
        });
        binding.resetButton.setOnClickListener(v -> resetDefaults());
        binding.checkUpdatesButton.setOnClickListener(v -> checkForUpdates());
        binding.downloadUpdateButton.setOnClickListener(v -> {
            String status = getSharedPreferences(UpdateConfig.PREFS, MODE_PRIVATE).getString("status", "");
            if ("ready_install".equals(status)) sendBroadcast(new Intent(this, AppUpdateReceiver.class).setAction(UpdateConfig.ACTION_INSTALL));
            else Toast.makeText(this, AppUpdateManager.startDownload(this, false) ? R.string.update_download_started : R.string.update_download_failed, Toast.LENGTH_SHORT).show();
        });
        binding.autoDownloadSwitch.setOnCheckedChangeListener((button, checked) -> {
            getSharedPreferences(UpdateConfig.PREFS, MODE_PRIVATE).edit().putBoolean(UpdateConfig.KEY_AUTO_DOWNLOAD, checked).apply();
            AppUpdateManager.setAutomaticChecks(this, checked);
            if (checked) checkForUpdates();
        });
        binding.notificationSettingsButton.setOnClickListener(v -> openNotificationSettings());
        binding.addTileButton.setOnClickListener(v -> requestQuickSettingsTile());
        binding.telegramAction.setOnClickListener(v -> openTelegram(getString(R.string.channel_handle), getString(R.string.channel_url)));
        binding.telegramCard.setOnClickListener(v -> openTelegram(getString(R.string.channel_handle), getString(R.string.channel_url)));
        binding.developerCard.setOnClickListener(v -> openTelegram(getString(R.string.developer_handle), getString(R.string.developer_url)));
        // Our other project. The app never talks to it: this is a plain Telegram link, nothing more.
        binding.botCard.setOnClickListener(v -> openTelegram(getString(R.string.bot_handle), getString(R.string.bot_url)));
        binding.githubCard.setOnClickListener(v -> openLink(getString(R.string.app_repository)));
        binding.donateBtc.setOnClickListener(v -> copyAddress(getString(R.string.donate_btc_label), getString(R.string.donate_btc_value)));
        binding.donateUsdt.setOnClickListener(v -> copyAddress(getString(R.string.donate_usdt_label), getString(R.string.donate_usdt_value)));
        binding.donateTon.setOnClickListener(v -> copyAddress(getString(R.string.donate_ton_label), getString(R.string.donate_ton_value)));
    }

    private void copyAddress(String label, String value) {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        findViewById(android.R.id.content).performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        // Android 13+ shows its own copy confirmation, so avoid a duplicate toast.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show();
    }

    private void restoreSettings() {
        String mode = preferences.getString("mode", "vpn");
        binding.modeGroup.check("manual".equals(mode) ? R.id.proxy_mode_button : "smart".equals(mode) ? R.id.smart_mode_button : R.id.vpn_mode_button);
        setSelection(binding.protocolInput, "protocol", ConnectionDefaults.PROTOCOL_INDEX, R.array.protocol_labels);
        setSelection(binding.scanInput, "scan", ConnectionDefaults.SCAN_INDEX, R.array.scan_labels);
        setSelection(binding.transportInput, "transport", 0, R.array.transport_labels);
        setSelection(binding.ipInput, "ip", 0, R.array.ip_labels);
        setSelection(binding.obfuscationInput, "obfuscation", 0, R.array.obfuscation_labels);
        setSelection(binding.logInput, "log", 0, R.array.log_labels);
        setSelection(binding.themeInput, "theme", 0, R.array.theme_labels);
        setSelection(binding.languageInput, "fa".equals(preferences.getString("language", "en")) ? 1 : 0, R.array.language_labels);
        binding.socksInput.setText(preferences.getString("socks", getString(R.string.default_socks_address)));
        binding.peerInput.setText(preferences.getString("peer", ""));
        binding.mtuInput.setText(preferences.getString("mtu", getString(R.string.default_mtu)));
        binding.dnsSwitch.setChecked(preferences.getBoolean("dnsLeak", true));
        binding.killswitchSwitch.setChecked(preferences.getBoolean("killSwitch", false));
        binding.reconnectSwitch.setChecked(preferences.getBoolean("quickReconnect", true));
        boolean split = preferences.getInt("routing", 0) >= 2;
        binding.splitSwitch.setChecked(split);
        binding.splitContainer.setVisibility(split ? View.VISIBLE : View.GONE);
        binding.routingGroup.check(preferences.getInt("routing", 2) == 3 ? R.id.exclude_apps_radio : R.id.include_apps_radio);
        updateModeUi();
        updateSelectedCount();
    }

    private void setSelection(MaterialAutoCompleteTextView view, String key, int fallback, int arrayId) { setSelection(view, preferences.getInt(key, fallback), arrayId); }
    private void setSelection(MaterialAutoCompleteTextView view, int index, int arrayId) {
        String[] values = getResources().getStringArray(arrayId);
        index = Math.max(0, Math.min(values.length - 1, index));
        view.setText(values[index], false);
        view.setTag(index);
    }

    private void updateModeUi() {
        String mode = preferences.getString("mode", "vpn");
        binding.modeSummary.setText("smart".equals(mode) ? R.string.smart_mode_summary : "manual".equals(mode) ? R.string.proxy_mode_summary : R.string.vpn_mode_summary);
        binding.protocolLayout.setVisibility("smart".equals(mode) ? View.GONE : View.VISIBLE);
        binding.transportLayout.setVisibility("smart".equals(mode) || selectedIndex(binding.protocolInput) != 0 ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------- connection

    private void connect() {
        if (!validSocks(text(binding.socksInput))) {
            binding.socksInput.setError(getString(R.string.invalid_socks));
            showPage(PAGE_TUNNEL);
            return;
        }
        if (binding.splitSwitch.isChecked() && selectedPackages().isEmpty() && binding.routingGroup.getCheckedRadioButtonId() == R.id.include_apps_radio) {
            Toast.makeText(this, R.string.split_include_empty, Toast.LENGTH_LONG).show();
            return;
        }
        saveSettings();
        if (!"manual".equals(preferences.getString("mode", "vpn"))) {
            Intent permission = VpnService.prepare(this);
            if (permission != null) { startActivityForResult(permission, VPN_REQUEST); return; }
        }
        VpnConnectionController.connect(this, preferences);
    }

    private void disconnect() { VpnConnectionController.disconnect(this); }

    private void openAppSelection() {
        String key = binding.routingGroup.getCheckedRadioButtonId() == R.id.exclude_apps_radio ? "splitExcludeApps" : "splitIncludeApps";
        startActivityForResult(new Intent(this, AppSelectionActivity.class).putExtra(AppSelectionActivity.EXTRA_PACKAGES, preferences.getString(key, "")), APPS_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST) {
            if (resultCode == RESULT_OK) VpnConnectionController.connect(this, preferences);
            else Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_LONG).show();
        } else if (requestCode == APPS_REQUEST) {
            if (data != null && data.getBooleanExtra(AppSelectionActivity.EXTRA_RETURN_HOME, false)) showPage(PAGE_CONNECT);
            else if (resultCode == RESULT_OK && data != null) {
                String key = binding.routingGroup.getCheckedRadioButtonId() == R.id.exclude_apps_radio ? "splitExcludeApps" : "splitIncludeApps";
                preferences.edit().putString(key, data.getStringExtra(AppSelectionActivity.EXTRA_PACKAGES)).apply();
                updateSelectedCount();
                saveSettings();
            }
        }
    }

    private void renderState(String newState, String message) {
        state = newState == null ? "disconnected" : newState;
        boolean connected = "connected".equals(state);
        boolean fault = "error".equals(state) || "blocked".equals(state);
        boolean transitioning = "starting".equals(state) || "smart-testing".equals(state) || "scanning".equals(state)
                || "securing".equals(state) || "reconnecting".equals(state) || "disconnecting".equals(state);
        binding.connectButton.setEnabled(!"disconnecting".equals(state));
        String orbLabel = connected ? getString(R.string.disconnect)
                : transitioning ? ("disconnecting".equals(state) ? getString(R.string.disconnecting) : getString(R.string.connecting))
                : getString(R.string.connect);
        binding.connectButton.setConnectionState(state, orbLabel);
        binding.connectButton.setContentDescription(orbLabel);
        binding.connectionStatus.setText(connected ? R.string.status_connected
                : transitioning ? ("disconnecting".equals(state) ? R.string.status_disconnecting : R.string.status_connecting)
                : fault ? R.string.status_error : R.string.status_disconnected);
        binding.statusDot.setBackgroundResource(connected ? R.drawable.status_dot_connected
                : transitioning ? R.drawable.status_dot_connecting
                : fault ? R.drawable.status_dot_error : R.drawable.status_dot);
        binding.progress.setVisibility(View.GONE);
        binding.connectionInfo.setVisibility(View.VISIBLE);
        if (connected) {
            binding.connectionMessage.setVisibility(View.GONE);
            binding.locationValue.setText(endpoint == null || endpoint.isEmpty() ? getString(R.string.connection_location_unavailable) : endpoint);
        } else if (transitioning) {
            binding.connectionMessage.setVisibility(View.GONE);
            binding.locationValue.setText(R.string.location_detecting);
        } else {
            binding.connectionMessage.setText(message == null || message.isEmpty() ? getString(R.string.status_error) : message);
            binding.connectionMessage.setVisibility(fault ? View.VISIBLE : View.GONE);
        }
        preferences.edit().putString("state", state).putString("message", message == null ? "" : message).apply();
        if (!connected) resetStats();
    }

    private boolean shouldDisconnect() {
        return "connected".equals(state) || "starting".equals(state) || "smart-testing".equals(state)
                || "scanning".equals(state) || "securing".equals(state) || "reconnecting".equals(state) || "disconnecting".equals(state);
    }

    private void renderStats(Intent intent) {
        long tx = Math.max(0, intent.getLongExtra("tx", 0));
        long rx = Math.max(0, intent.getLongExtra("rx", 0));
        animateMetric(binding.uploadValue, formatTraffic(tx));
        animateMetric(binding.downloadValue, formatTraffic(rx));
        long ping = intent.getLongExtra("ping", -1);
        animateMetric(binding.pingValue, ping >= 0 ? getString(R.string.ping_millis, Long.toString(ping)) : getString(R.string.metric_unavailable));
    }

    private void animateMetric(TextView view, String value) {
        if (value.equals(view.getTag())) return;
        view.setTag(value);
        view.setText(value);
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.animate().cancel();
        view.setAlpha(0.5f);
        view.animate().alpha(1f).setDuration(200).start();
    }

    private String formatTraffic(long bytes) {
        if (bytes < 1024L * 1024L) return getString(R.string.traffic_kilobytes, bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return getString(R.string.traffic_megabytes, bytes / (1024.0 * 1024.0));
        return getString(R.string.traffic_gigabytes, bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void resetStats() {
        binding.uploadValue.setTag(null);
        binding.downloadValue.setTag(null);
        binding.pingValue.setTag(null);
        binding.uploadValue.setText(R.string.metric_unavailable);
        binding.downloadValue.setText(R.string.metric_unavailable);
        binding.pingValue.setText(R.string.metric_unavailable);
        if (!"connected".equals(state)) binding.locationValue.setText(R.string.connection_location_unavailable);
    }

    private void saveSettings() {
        int routing = binding.splitSwitch.isChecked() ? (binding.routingGroup.getCheckedRadioButtonId() == R.id.exclude_apps_radio ? 3 : 2) : 0;
        String include = preferences.getString("splitIncludeApps", "");
        String exclude = preferences.getString("splitExcludeApps", "");
        preferences.edit()
                .putInt("protocol", selectedIndex(binding.protocolInput))
                .putInt("scan", selectedIndex(binding.scanInput))
                .putInt("transport", selectedIndex(binding.transportInput))
                .putInt("ip", selectedIndex(binding.ipInput))
                .putInt("obfuscation", selectedIndex(binding.obfuscationInput))
                .putInt("log", selectedIndex(binding.logInput))
                .putInt("theme", selectedIndex(binding.themeInput))
                .putInt("routing", routing)
                .putString("splitApps", routing == 3 ? exclude : include)
                .putString("socks", text(binding.socksInput))
                .putString("peer", text(binding.peerInput))
                .putString("mtu", text(binding.mtuInput))
                .putBoolean("dnsLeak", binding.dnsSwitch.isChecked())
                .putBoolean("killSwitch", binding.killswitchSwitch.isChecked())
                .putBoolean("quickReconnect", binding.reconnectSwitch.isChecked())
                .apply();
    }

    private Set<String> selectedPackages() {
        Set<String> result = new LinkedHashSet<>();
        String key = binding.routingGroup.getCheckedRadioButtonId() == R.id.exclude_apps_radio ? "splitExcludeApps" : "splitIncludeApps";
        AppSelectionActivity.parsePackages(preferences.getString(key, ""), result);
        return result;
    }

    private void updateSelectedCount() {
        if (binding == null) return;
        int count = selectedPackages().size();
        binding.selectedAppsCount.setText(getResources().getQuantityString(R.plurals.app_picker_selected_count, count, count));
    }

    private void resetDefaults() {
        String language = preferences.getString("language", "en");
        preferences.edit().clear().putString("language", language).putInt("theme", 0).apply();
        restoreSettings();
        saveSettings();
        applyTheme(0);
    }

    // ---------------------------------------------------------------- updates

    private void checkForUpdates() {
        SharedPreferences updates = getSharedPreferences(UpdateConfig.PREFS, MODE_PRIVATE);
        updates.edit().putString("status", "checking").apply();
        renderUpdateState();
        binding.checkUpdatesButton.setEnabled(false);
        AppUpdateManager.checkNow(this, new AppUpdateManager.Listener() {
            @Override public void onComplete() { binding.checkUpdatesButton.setEnabled(true); renderUpdateState(); }
            @Override public void onError(Throwable error) {
                binding.checkUpdatesButton.setEnabled(true);
                renderUpdateState();
                String detail = error == null ? "" : error.getMessage();
                Toast.makeText(MainActivity.this, detail == null || detail.isEmpty() ? getString(R.string.update_failed) : getString(R.string.update_failed) + ": " + detail, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderUpdateState() {
        if (binding == null) return;
        SharedPreferences updates = getSharedPreferences(UpdateConfig.PREFS, MODE_PRIVATE);
        String latest = updates.getString(UpdateConfig.KEY_LATEST_VERSION, "");
        String status = updates.getString("status", "");
        binding.latestVersionValue.setText(latest.isEmpty() ? getString(R.string.not_checked) : latest);
        int id = "up_to_date".equals(status) ? R.string.update_up_to_date
                : "available".equals(status) ? R.string.update_available
                : "downloading".equals(status) ? R.string.update_downloading
                : "ready_install".equals(status) ? R.string.update_ready_install
                : "checking".equals(status) ? R.string.update_checking
                : "download_failed".equals(status) ? R.string.update_download_failed
                : "verification_failed".equals(status) ? R.string.update_verification_failed
                : "failed".equals(status) ? R.string.update_failed : R.string.not_checked;
        binding.updateStatusValue.setText(id);
        String notes = updates.getString(UpdateConfig.KEY_RELEASE_NOTES, "");
        binding.releaseNotesValue.setText(notes);
        binding.releaseNotesValue.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
        boolean downloading = "downloading".equals(status);
        int progress = downloading ? AppUpdateManager.downloadProgress(this) : -1;
        binding.updateProgress.setVisibility(downloading ? View.VISIBLE : View.GONE);
        binding.updateProgress.setIndeterminate(downloading && progress <= 0);
        if (progress > 0) binding.updateProgress.setProgress(progress);
        boolean action = "available".equals(status) || "download_failed".equals(status) || "verification_failed".equals(status) || "ready_install".equals(status);
        binding.downloadUpdateButton.setVisibility(action ? View.VISIBLE : View.GONE);
        binding.downloadUpdateButton.setText("ready_install".equals(status) ? R.string.install_update : R.string.download_update);
    }

    // ---------------------------------------------------------------- system

    private void applyTheme(int choice) {
        preferences.edit().putInt("theme", choice).apply();
        AppCompatDelegate.setDefaultNightMode(themeMode(choice));
    }

    private static int themeMode(int choice) {
        return choice == 1 ? AppCompatDelegate.MODE_NIGHT_NO : choice == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private void applyLanguage(String language, boolean recreate) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fa".equals(language) ? "fa" : "en"));
        if (recreate) recreate();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_REQUEST);
    }

    private void openNotificationSettings() {
        try { startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())); }
        catch (Exception ignored) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
    }

    private void requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager manager = getSystemService(StatusBarManager.class);
            manager.requestAddTileService(new ComponentName(this, AutoConnectTileService.class), getString(R.string.tile_name),
                    Icon.createWithResource(this, R.drawable.ic_brand_mono), getMainExecutor(),
                    result -> Toast.makeText(this, R.string.tile_add_requested, Toast.LENGTH_SHORT).show());
            return;
        }
        try { startActivity(new Intent("android.settings.QUICK_SETTINGS_SETTINGS")); }
        catch (Exception ignored) { Toast.makeText(this, R.string.tile_add_manual, Toast.LENGTH_LONG).show(); }
    }

    private void openTelegram(String handle, String webUrl) {
        String domain = handle.startsWith("@") ? handle.substring(1) : handle;
        Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + domain));
        for (String packageName : new String[]{"org.telegram.messenger", "org.telegram.messenger.web"}) {
            try {
                direct.setPackage(packageName);
                startActivity(direct);
                return;
            } catch (ActivityNotFoundException ignored) { }
        }
        openLink(webUrl);
    }

    private void openLink(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (ActivityNotFoundException ignored) { Toast.makeText(this, R.string.telegram_fallback, Toast.LENGTH_SHORT).show(); }
    }

    private int selectedIndex(MaterialAutoCompleteTextView view) { Object tag = view.getTag(); return tag instanceof Integer ? (Integer) tag : 0; }
    private String text(com.google.android.material.textfield.TextInputEditText view) { return view.getText() == null ? "" : view.getText().toString().trim(); }

    private boolean validSocks(String value) {
        int split = value.lastIndexOf(':');
        if (split <= 0) return false;
        try {
            int port = Integer.parseInt(value.substring(split + 1));
            return port > 0 && port <= 65535;
        } catch (Exception ignored) { return false; }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(AutoConnectVpnService.ACTION_STATUS);
            filter.addAction(AutoConnectVpnService.ACTION_STATS);
            filter.addAction(UpdateConfig.ACTION_STATE);
            ContextCompat.registerReceiver(this, receiver, filter, INTERNAL_PERMISSION, null, ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        startService(new Intent(this, AutoConnectVpnService.class).setAction(AutoConnectVpnService.ACTION_QUERY));
        updateHandler.removeCallbacks(updateProgressPoll);
        updateHandler.post(updateProgressPoll);
    }

    @Override protected void onStop() {
        updateHandler.removeCallbacks(updateProgressPoll);
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; }
        super.onStop();
    }
}
