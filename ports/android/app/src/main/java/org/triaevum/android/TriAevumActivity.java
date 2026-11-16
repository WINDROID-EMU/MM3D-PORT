package org.triaevum.android;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLSurface;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.content.res.Configuration;
import org.triaevum.android.controls.AzaharInputAdapter;
import org.triaevum.android.controls.EmulationMenuSettings;
import org.triaevum.android.controls.InputOverlay;
import org.triaevum.android.controls.OverlayHost;

/** SDL owns Surface/lifecycle. Game, renderer and title loading retain their native owners. */
public final class TriAevumActivity extends SDLActivity {
    private InputOverlay inputOverlay;
    private AndroidOverlayBridge overlayBridge;
    private AzaharInputAdapter overlayAdapter;

    private File gameDataRoot() {
        File root = getExternalFilesDir(null);
        return new File(root != null ? root : getFilesDir(), "mm3d-data");
    }

    @Override protected SDLSurface createSDLSurface(Context context) {
        int maximumShortEdge = 720;
        File config = new File(gameDataRoot(), "TriAevum.android.host.json");
        if (config.isFile()) {
            try {
                maximumShortEdge = new JSONObject(new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8))
                    .getInt("maximum_surface_short_edge");
                if (maximumShortEdge < 0) throw new IllegalArgumentException("Negative surface limit");
            } catch (Exception error) {
                Log.w("TriAevum", "Invalid Android host config; using 720p surface limit", error);
                maximumShortEdge = 720;
            }
        }
        return new TriAevumSurface(context, maximumShortEdge);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        overlayBridge = new AndroidOverlayBridge(this);
        overlayAdapter = new AzaharInputAdapter(overlayBridge);
        OverlayHost.INSTANCE.bind(this, overlayAdapter);
        inputOverlay = new InputOverlay(this, null);
        overlayBridge.attach(inputOverlay);
        addContentView(inputOverlay, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlayBridge.swapScreens(EmulationMenuSettings.INSTANCE.getSwapScreens(),
            getWindowManager().getDefaultDisplay().getRotation());
    }

    private void releaseOverlayInput() {
        if (inputOverlay != null) inputOverlay.releaseAllInput();
        else if (overlayAdapter != null) overlayAdapter.releaseAll();
    }

    @Override protected void onPause() {
        releaseOverlayInput();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) releaseOverlayInput();
        super.onWindowFocusChanged(hasFocus);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (inputOverlay != null) inputOverlay.refreshControls();
    }

    @Override protected void onDestroy() {
        releaseOverlayInput();
        if (inputOverlay != null && inputOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) inputOverlay.getParent()).removeView(inputOverlay);
        }
        if (overlayAdapter != null) OverlayHost.INSTANCE.unbind(overlayAdapter);
        inputOverlay = null;
        overlayAdapter = null;
        overlayBridge = null;
        super.onDestroy();
    }

    @Override protected String[] getLibraries() {
        return new String[] { "SDL2", "triaevum_title_bootstrap", "TriAevum" };
    }

    @Override protected String[] getArguments() {
        File root = gameDataRoot();
        return new String[] {
            "--launch-profile", new File(root, "TriAevum.android.launch.json").getAbsolutePath(),
            "--title-plugin", new File(getApplicationInfo().nativeLibraryDir,
                "libmm3d_title_aot.so").getAbsolutePath()
        };
    }
}
