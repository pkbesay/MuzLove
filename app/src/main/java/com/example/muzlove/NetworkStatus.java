package com.example.muzlove;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Проверка реального доступа к интернету (не только наличия Wi-Fi или мобильной сети). */
final class NetworkStatus {
    private NetworkStatus() {}

    static boolean hasInternet(Context context) {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network active = manager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = manager.getNetworkCapabilities(active);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (SecurityException | RuntimeException e) {
            return false;
        }
    }
}
