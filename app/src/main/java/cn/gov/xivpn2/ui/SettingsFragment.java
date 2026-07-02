package cn.gov.xivpn2.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Objects;

import cn.gov.xivpn2.BuildConfig;
import cn.gov.xivpn2.R;

public class SettingsFragment extends PreferenceFragmentCompat {


    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        findPreference("black_background").setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getContext(), R.string.restart_to_apply, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference("app_version").setSummary(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        findPreference("geoip_geosite").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getContext(), GeoAssetsActivity.class));
            return true;
        });

        findPreference("split_tunnel_apps").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getContext(), SplitTunnelActivity.class));
            return true;
        });

        findPreference("backup_or_restore").setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getContext(), BackupActivity.class));
            return true;
        });
    }

    private void openUrl(String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            Log.e("SettingsFragment", "open browser", e);
        }
    }

    public int dp2px(float dp) {
        return (int) (dp * ((float) requireContext().getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }
}
