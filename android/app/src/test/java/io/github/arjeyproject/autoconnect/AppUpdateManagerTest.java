package io.github.arjeyproject.autoconnect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AppUpdateManagerTest {
    @Test public void semanticVersionsCompareNumerically() {
        assertTrue(AppUpdateManager.compareVersions("1.2", "1.1") > 0);
        assertTrue(AppUpdateManager.compareVersions("1.12.0", "1.11.9") > 0);
        assertEquals(0, AppUpdateManager.compareVersions("v1.2", "1.2"));
    }

    @Test public void checksumFileSelectsExactAsset() {
        String sums = "aaa  AutoConnect_1.2_android-arm64.apk\nabcdef  AutoConnect_1.2_android-universal.apk\n";
        assertEquals("abcdef", AppUpdateManager.checksumFromFile(sums, "AutoConnect_1.2_android-universal.apk"));
    }
}
