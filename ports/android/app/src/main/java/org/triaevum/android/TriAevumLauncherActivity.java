package org.triaevum.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** First-run importer for a privately prepared MM3D data package. */
public final class TriAevumLauncherActivity extends Activity {
    private static final int REQUEST_MM3D_DATA = 4317;
    private static final long MAX_UNPACKED_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 100000;
    private static final ExecutorService IMPORTER = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button importButton;
    private Button launchButton;

    private File appFilesRoot() {
        File root = getExternalFilesDir(null);
        return root != null ? root : getFilesDir();
    }

    private File mm3dDataRoot() {
        return new File(appFilesRoot(), "mm3d-data");
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        recoverInterruptedReplacement();
        buildLauncherUi();
        updateStatus(null);
    }

    private void buildLauncherUi() {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Majora's Mask 3D");
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView instructions = new TextView(this);
        instructions.setText("Importe o ZIP de dados MM3D preparado por você. O módulo do jogo precisa estar incluído neste APK; os dados ficam no armazenamento privado do app e não são incluídos no APK.");
        instructions.setTextSize(16);
        instructions.setPadding(0, pad / 2, 0, pad / 2);
        content.addView(instructions, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, pad / 2, 0, pad / 2);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        importButton = new Button(this);
        importButton.setText("Importar ZIP de dados MM3D");
        importButton.setOnClickListener(view -> chooseDataPackage());
        content.addView(importButton, new LinearLayout.LayoutParams(-1, -2));

        launchButton = new Button(this);
        launchButton.setText("Iniciar Majora's Mask 3D");
        launchButton.setOnClickListener(view -> {
            if (isReady()) startActivity(new Intent(this, TriAevumActivity.class));
            else updateStatus("Importe um pacote MM3D válido e instale um APK compilado com o módulo MM3D.");
        });
        content.addView(launchButton, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void updateStatus(String message) {
        boolean modulePresent = new File(getApplicationInfo().nativeLibraryDir,
                "libmm3d_title_aot.so").isFile();
        boolean dataPresent = new File(mm3dDataRoot(), "TriAevum.android.launch.json").isFile();
        if (status != null) {
            if (message != null) status.setText(message);
            else if (!modulePresent) status.setText("Este APK não contém o módulo MM3D ARM64. Gere um APK privado usando sua própria instalação preparada do jogo.");
            else if (!dataPresent) status.setText("Os dados do jogo ainda não foram importados.");
            else status.setText("Módulo MM3D e dados privados prontos.");
        }
        if (launchButton != null) launchButton.setEnabled(modulePresent && dataPresent);
    }

    private boolean isReady() {
        return new File(getApplicationInfo().nativeLibraryDir, "libmm3d_title_aot.so").isFile()
                && new File(mm3dDataRoot(), "TriAevum.android.launch.json").isFile();
    }

    private void chooseDataPackage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_MM3D_DATA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MM3D_DATA || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        importButton.setEnabled(false);
        status.setText("Importando e validando os dados MM3D. Arquivos grandes podem levar alguns minutos.");
        IMPORTER.execute(() -> {
            String result;
            try {
                importPackage(uri);
                result = "Dados MM3D importados e verificados no armazenamento privado do app.";
            } catch (Exception error) {
                android.util.Log.e("TriAevum", "MM3D data import failed", error);
                result = "Falha ao importar. Confira se o ZIP foi gerado pelo preparador MM3D e tente novamente.";
            }
            final String message = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                importButton.setEnabled(true);
                updateStatus(message);
            });
        });
    }

    private void importPackage(Uri uri) throws Exception {
        File parent = appFilesRoot();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create app data directory");
        cleanupInterruptedImports(parent);
        recoverInterruptedReplacement();
        File temporary = new File(parent, ".mm3d-import-" + UUID.randomUUID());
        File candidate = new File(temporary, "mm3d-data");
        if (!candidate.mkdirs()) throw new IllegalStateException("Cannot create temporary import directory");
        try (InputStream raw = getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IllegalStateException("The selected ZIP could not be opened");
            unzipSafely(raw, candidate);
            validatePackage(candidate);
            preserveAppState(mm3dDataRoot(), candidate);
            replaceDataRoot(candidate);
        } finally {
            deleteRecursively(temporary);
        }
    }

    private static boolean isAllowedPath(String name, boolean directory) {
        if (name.startsWith("resources/")) return true;
        if (name.equals("TriAevum.android.launch.json") || name.equals("process-manifest.json")
                || name.equals("game/exheader.bin") || name.equals("game/code.bin")
                || name.equals("game/romfs.bin") || name.equals("data/config/TriAevum.android.json")) return !directory;
        if (!directory) return false;
        return name.equals("game") || name.equals("resources") || name.equals("data")
                || name.equals("data/config");
    }

    private static void unzipSafely(InputStream source, File root) throws Exception {
        Set<String> names = new HashSet<>();
        long total = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IllegalArgumentException("ZIP contains too many entries");
                String rawName = entry.getName();
                if (rawName == null || rawName.isEmpty() || rawName.indexOf('\\') >= 0 || rawName.indexOf('\0') >= 0
                        || rawName.startsWith("/") || rawName.matches("^[A-Za-z]:.*"))
                    throw new IllegalArgumentException("ZIP contains an unsafe path");
                String name = entry.isDirectory() && rawName.endsWith("/")
                        ? rawName.substring(0, rawName.length() - 1) : rawName;
                for (String part : name.split("/", -1)) {
                    if (part.isEmpty() || part.equals(".") || part.equals(".."))
                        throw new IllegalArgumentException("ZIP contains path traversal");
                }
                if (!names.add(name)) throw new IllegalArgumentException("ZIP contains duplicate entries");
                if (!isAllowedPath(name, entry.isDirectory()))
                    throw new IllegalArgumentException("ZIP contains an unexpected file: " + name);
                File target = new File(root, name);
                String canonicalRoot = root.getCanonicalPath() + File.separator;
                if (!target.getCanonicalPath().startsWith(canonicalRoot))
                    throw new IllegalArgumentException("ZIP entry escapes the import directory");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IllegalStateException("Cannot create package directory");
                } else {
                    File directory = target.getParentFile();
                    if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create package directory");
                    try (FileOutputStream file = new FileOutputStream(target);
                         BufferedOutputStream output = new BufferedOutputStream(file)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) {
                            total += count;
                            if (total > MAX_UNPACKED_BYTES) throw new IllegalArgumentException("MM3D package exceeds the 4-GiB safety limit");
                            output.write(buffer, 0, count);
                        }
                        output.flush();
                        file.getFD().sync();
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void validatePackage(File root) throws Exception {
        File profileFile = new File(root, "TriAevum.android.launch.json");
        File manifestFile = new File(root, "process-manifest.json");
        File code = new File(root, "game/code.bin");
        File exheader = new File(root, "game/exheader.bin");
        File romfs = new File(root, "game/romfs.bin");
        File resources = new File(root, "resources");
        File config = new File(root, "data/config/TriAevum.android.json");
        for (File required : new File[]{profileFile, manifestFile, code, exheader, romfs, config})
            if (!required.isFile()) throw new IllegalArgumentException("MM3D package is missing " + required.getName());
        if (!resources.isDirectory() || !hasFilesRecursively(resources))
            throw new IllegalArgumentException("MM3D resources are missing");

        JSONObject profile = new JSONObject(readUtf8(profileFile));
        if (!"oot3d_native_game_launch_profile_v1".equals(profile.optString("format")))
            throw new IllegalArgumentException("Unsupported Android launch profile");
        JSONArray args = profile.optJSONArray("arguments");
        if (args == null || args.length() == 0 || (args.length() & 1) != 0)
            throw new IllegalArgumentException("Launch profile arguments are invalid");
        JSONObject options = new JSONObject();
        for (int i = 0; i < args.length(); i += 2) {
            String key = args.getString(i);
            if (!key.startsWith("--") || options.has(key)) throw new IllegalArgumentException("Launch profile has duplicate/invalid options");
            options.put(key, args.getString(i + 1));
        }
        Set<String> expectedOptions = new HashSet<>();
        for (String key : new String[]{"--a32-process-manifest", "--resource-root", "--renderer", "--ui-profile",
                "--config", "--save-data", "--renderer-cache-directory", "--gameplay-timing",
                "--presentation-rate", "--width", "--height", "--output"}) expectedOptions.add(key);
        if (options.length() != expectedOptions.size()) throw new IllegalArgumentException("Launch profile contains unexpected arguments");
        for (String key : expectedOptions) if (!options.has(key)) throw new IllegalArgumentException("Launch profile is missing " + key);
        requireOption(options, "--a32-process-manifest", "${profile_dir}/process-manifest.json");
        requireOption(options, "--resource-root", "${profile_dir}/resources");
        requireOption(options, "--renderer", "nri");
        requireOption(options, "--ui-profile", "oot3d");
        requireOption(options, "--config", "${profile_dir}/data/config/TriAevum.android.json");
        requireOption(options, "--save-data", "${profile_dir}/data/savedata");
        requireOption(options, "--renderer-cache-directory", "${profile_dir}/data/cache/renderer");
        requireOption(options, "--gameplay-timing", "native30_no_interpolation");
        requireOption(options, "--presentation-rate", "30");
        requireOption(options, "--width", "640");
        requireOption(options, "--height", "360");
        requireOption(options, "--output", "${profile_dir}/data/runtime-state.json");

        JSONObject manifest = new JSONObject(readUtf8(manifestFile));
        if (!"oot3d_native_process_manifest_v1".equals(manifest.optString("format")))
            throw new IllegalArgumentException("Unsupported process manifest");
        JSONObject title = manifest.optJSONObject("title");
        if (title == null || !"mm3d".equals(title.optString("id"))
                || !"0004000000125500".equalsIgnoreCase(title.optString("program_id")))
            throw new IllegalArgumentException("The package is not Majora's Mask 3D USA");
        JSONObject source = manifest.optJSONObject("source");
        if (source == null || !"game/code.bin".equals(source.optString("code_bin_path"))
                || !"game/exheader.bin".equals(source.optString("exheader_path"))
                || !"game/romfs.bin".equals(source.optString("romfs_image_path")))
            throw new IllegalArgumentException("Manifest paths do not match the prepared MM3D package");
        if (!source.optString("code_bin_sha256").equals(sha256(code)))
            throw new IllegalArgumentException("MM3D code.bin hash does not match its manifest");
        if (source.optLong("code_bin_size", -1) != code.length()
                || source.optLong("romfs_image_file_size", -1) != romfs.length()
                || code.length() == 0 || romfs.length() == 0 || exheader.length() < 0x400)
            throw new IllegalArgumentException("MM3D data sizes do not match the manifest");
        long romfsOffset = source.optLong("romfs_service_offset", -1);
        long romfsServiceSize = source.optLong("romfs_service_size", -1);
        if (romfsOffset < 0 || romfsServiceSize <= 0 || romfsOffset > romfs.length()
                || romfsServiceSize > romfs.length() - romfsOffset)
            throw new IllegalArgumentException("MM3D RomFS service window is invalid");
        if (source.has("exheader_sha256") && !source.optString("exheader_sha256").equals(sha256(exheader)))
            throw new IllegalArgumentException("MM3D ExHeader hash does not match its manifest");
    }

    private static void requireOption(JSONObject options, String key, String expected) throws Exception {
        if (!expected.equals(options.getString(key))) throw new IllegalArgumentException("Unexpected launch profile value for " + key);
    }

    private static boolean hasFilesRecursively(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile() || (child.isDirectory() && hasFilesRecursively(child))) return true;
        }
        return false;
    }

    private static String readUtf8(File file) throws Exception {
        if (file.length() > 4L * 1024L * 1024L) throw new IllegalArgumentException("JSON file exceeds size limit");
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != bytes.length) throw new IllegalArgumentException("JSON file is truncated");
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static void preserveAppState(File current, File candidate) throws Exception {
        for (String relative : new String[]{"data/savedata", "data/cache/renderer", "data/config/TriAevum.android.json", "TriAevum.android.host.json"}) {
            File source = new File(current, relative);
            if (source.exists()) copyRecursively(source, new File(candidate, relative));
        }
    }

    private static void copyRecursively(File source, File destination) throws Exception {
        if (Files.isSymbolicLink(source.toPath())) throw new IllegalArgumentException("Existing app data contains an unsupported symbolic link");
        if (source.isDirectory()) {
            if (!destination.isDirectory() && !destination.mkdirs()) throw new IllegalStateException("Cannot preserve app data");
            File[] children = source.listFiles();
            if (children == null) throw new IllegalStateException("Cannot read existing app data");
            for (File child : children) copyRecursively(child, new File(destination, child.getName()));
        } else if (source.isFile()) {
            File parent = destination.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot preserve app data");
            try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.getFD().sync();
            }
        }
    }

    private void replaceDataRoot(File candidate) throws Exception {
        File current = mm3dDataRoot();
        File backup = new File(appFilesRoot(), ".mm3d-data-backup");
        if (!current.exists() && backup.exists() && !backup.renameTo(current))
            throw new IllegalStateException("Cannot recover the previous MM3D data after an interrupted import");
        deleteRecursively(backup);
        boolean movedCurrent = current.exists();
        if (movedCurrent && !current.renameTo(backup)) throw new IllegalStateException("Cannot preserve the previous MM3D data");
        if (!candidate.renameTo(current)) {
            if (movedCurrent) backup.renameTo(current);
            throw new IllegalStateException("Cannot activate imported MM3D data");
        }
        deleteRecursively(backup);
    }

    private void recoverInterruptedReplacement() {
        File current = mm3dDataRoot();
        File backup = new File(appFilesRoot(), ".mm3d-data-backup");
        if (!current.exists() && backup.exists() && !backup.renameTo(current))
            android.util.Log.e("TriAevum", "Could not recover the previous MM3D data directory");
        else if (current.exists() && backup.exists()) deleteRecursively(backup);
    }

    private static void cleanupInterruptedImports(File parent) {
        File[] children = parent.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.getName().startsWith(".mm3d-import-")) deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

}
