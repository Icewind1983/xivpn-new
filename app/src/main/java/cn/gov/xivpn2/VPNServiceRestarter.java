package cn.gov.xivpn2;

import android.content.Context;
import android.util.Log;

import cn.gov.xivpn2.service.XiVPNService;

public class VPNServiceRestarter {
    private static final String TAG = "VPNServiceRestarter";

    /**
     * Перезапустить VPN сервис через RELOAD action
     */
    public static void restartVPNService(Context context) {
        try {
            Log.d(TAG, "Marking VPN config as stale to trigger reload");
            XiVPNService.markConfigStale(context);
        } catch (Exception e) {
            Log.e(TAG, "Error restarting VPN service", e);
        }
    }
}
