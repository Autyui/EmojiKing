package com.example.myapplication;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** EmojiKing 图片托管接口的轻量客户端。 */
// 类作用：定义 ImageHostingClient，承载所在模块的主要职责。
public final class ImageHostingClient {
    public static final long MAX_UPLOAD_BYTES = 3L * 1024L * 1024L;
    public static final String DEFAULT_BASE_URL =
            "https://emoji-king-imagehosting.vercel.app";

    private static final String PREFERENCES = "emoji-image-hosting";
    private static final String BASE_URL = "base-url";
    private static final String DEVICE_ID = "device-id";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final String baseUrl;

// 方法作用：初始化 ImageHostingClient 对象并建立其运行所需状态。
    public ImageHostingClient(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("云端地址不能为空");
        }
        String normalized = baseUrl.trim();
        try {
            URL url = new URL(normalized);
            if (!("http".equalsIgnoreCase(url.getProtocol())
                    || "https".equalsIgnoreCase(url.getProtocol()))) {
                throw new IllegalArgumentException("云端地址必须使用 HTTP 或 HTTPS");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("云端地址无效", exception);
        }
        this.baseUrl = normalized.replaceAll("/+$", "");
    }

// 方法作用：读取并返回持久化或运行时状态（getConfiguredBaseUrl）。
    public static String getConfiguredBaseUrl(Context context) {
        return context.getSharedPreferences(PREFERENCES, 0)
                .getString(BASE_URL, DEFAULT_BASE_URL);
    }

// 方法作用：校验并持久化用户提供的数据（saveConfiguredBaseUrl）。
    public static void saveConfiguredBaseUrl(Context context, String baseUrl) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences(
                PREFERENCES, 0).edit();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            editor.remove(BASE_URL);
        } else {
            editor.putString(BASE_URL, baseUrl.trim());
        }
        editor.apply();
    }

// 方法作用：读取并返回持久化或运行时状态（getMachineCode）。
    public static String getMachineCode(Context context) {
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId != null && !androidId.trim().isEmpty()) {
            return "android-" + androidId.trim();
        }
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, 0);
        String saved = preferences.getString(DEVICE_ID, null);
        if (saved != null && !saved.trim().isEmpty()) {
            return saved;
        }
        String generated = "device-" + UUID.randomUUID().toString().replace("-", "");
        preferences.edit().putString(DEVICE_ID, generated).apply();
        return generated;
    }

// 方法作用：处理 upload 对应的输入并返回或更新相关结果（upload）。
    public UploadResult upload(
            String machineCode,
            File file,
            String filename,
            String contentType) throws IOException {
        if (file == null || !file.isFile() || file.length() <= 0) {
            throw new IOException("待上传图片不存在");
        }
        if (file.length() > MAX_UPLOAD_BYTES) {
            throw new IOException("云端单张图片不能超过 3 MiB");
        }
        byte[] content = readFile(file);
        JsonObject payload = new JsonObject();
        payload.addProperty("machine_code", requireMachineCode(machineCode));
        payload.addProperty("filename", filename == null ? file.getName() : filename);
        payload.addProperty("content_type", contentType);
        // 服务端接口使用无换行 Base64，避免 JSON 字符串携带不可预期的换行符。
        payload.addProperty("data", Base64.encodeToString(content, Base64.NO_WRAP));
        JsonObject result = requestJson("/api/upload", "POST", payload.toString());
        String path = requiredString(result, "path");
        String url = requiredString(result, "url");
        return new UploadResult(path, url);
    }

// 方法作用：处理 list 对应的输入并返回或更新相关结果（list）。
    public List<RemoteImage> list(String machineCode) throws IOException {
        String encoded = URLEncoder.encode(requireMachineCode(machineCode), "UTF-8");
        JsonObject result = requestJson("/api/images?machine_code=" + encoded, "GET", null);
        JsonElement value = result.get("items");
        if (value == null || !value.isJsonArray()) {
            throw new IOException("云端返回的图片列表无效");
        }
        List<RemoteImage> images = new ArrayList<>();
        JsonArray items = value.getAsJsonArray();
        for (JsonElement itemElement : items) {
            if (!itemElement.isJsonObject()) {
                continue;
            }
            JsonObject item = itemElement.getAsJsonObject();
            String path = optionalString(item, "path");
            String url = optionalString(item, "url");
            if (!path.isEmpty() && !url.isEmpty()) {
                images.add(new RemoteImage(path, url, item.has("size") ? item.get("size").getAsLong() : 0));
            }
        }
        return Collections.unmodifiableList(images);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（delete）。
    public void delete(String machineCode, RemoteImage image) throws IOException {
        if (image == null || image.getPath().trim().isEmpty()) {
            throw new IOException("待删除云端图片无效");
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("machine_code", requireMachineCode(machineCode));
        payload.addProperty("path", image.getPath());
        requestJson("/api/delete", "DELETE", payload.toString());
    }

// 方法作用：处理 download 对应的输入并返回或更新相关结果（download）。
    public void download(RemoteImage image, File destination) throws IOException {
        if (image == null || destination == null) {
            throw new IOException("云端图片参数无效");
        }
        HttpURLConnection connection = open(new URL(image.getUrl()), "GET");
        try {
            ensureSuccess(connection);
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("无法创建云端暂存目录");
            }
            long total = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    // 同时限制响应体大小，防止异常云端响应耗尽本地磁盘。
                    if (total > EmojiFileStore.MAX_IMAGE_BYTES) {
                        throw new IOException("云端图片超过本地 20 MiB 限制");
                    }
                    output.write(buffer, 0, read);
                }
                if (total == 0) {
                    throw new IOException("云端图片为空");
                }
                output.flush();
                output.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

// 方法作用：处理 requestJson 对应的输入并返回或更新相关结果（requestJson）。
    private JsonObject requestJson(String path, String method, String body) throws IOException {
        HttpURLConnection connection = open(new URL(baseUrl + path), method);
        try {
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            ensureSuccess(connection);
            String response = readText(connection.getInputStream());
            JsonElement parsed = JsonParser.parseString(response);
            if (!parsed.isJsonObject()) {
                throw new IOException("云端返回了无效 JSON");
            }
            JsonObject result = parsed.getAsJsonObject();
            if (result.has("error") && !result.get("error").isJsonNull()) {
                throw new IOException(result.get("error").getAsString());
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

// 方法作用：显示或打开对应的交互界面（open）。
    private HttpURLConnection open(URL url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（ensureSuccess）。
    private static void ensureSuccess(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            String detail = "";
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                detail = readText(errorStream);
            }
            throw new IOException("云端请求失败 (" + status + ")" + extractError(detail));
        }
    }

// 方法作用：处理 extractError 对应的输入并返回或更新相关结果（extractError）。
    private static String extractError(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "";
        }
        try {
            JsonElement parsed = JsonParser.parseString(response);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("error")) {
                return ": " + parsed.getAsJsonObject().get("error").getAsString();
            }
        } catch (RuntimeException ignored) {
            // 云端返回非 JSON 文本时，HTTP 状态码仍能提供有效的失败信息。
        }
        return "";
    }

// 方法作用：从输入源读取并转换数据（readText）。
    private static String readText(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = source.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

// 方法作用：从输入源读取并转换数据（readFile）。
    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireMachineCode）。
    private static String requireMachineCode(String value) throws IOException {
        String machineCode = value == null ? "" : value.trim();
        if (!machineCode.matches("[a-zA-Z0-9._:-]{1,128}")) {
            throw new IOException("设备 ID 格式无效");
        }
        return machineCode;
    }

// 方法作用：读取 JSON 中必需的字符串字段并校验类型（requiredString）。
    private static String requiredString(JsonObject object, String name) throws IOException {
        String value = optionalString(object, name);
        if (value.isEmpty()) {
            throw new IOException("云端响应缺少 " + name);
        }
        return value;
    }

// 方法作用：读取 JSON 中可选的字符串字段并处理缺省值（optionalString）。
    private static String optionalString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

// 类作用：定义 UploadResult，承载所在模块的主要职责。
    public static final class UploadResult {
        private final String path;
        private final String url;

// 方法作用：初始化 UploadResult 对象并建立其运行所需状态。
        private UploadResult(String path, String url) {
            this.path = path;
            this.url = url;
        }

// 方法作用：读取并返回持久化或运行时状态（getPath）。
        public String getPath() {
            return path;
        }

// 方法作用：读取并返回持久化或运行时状态（getUrl）。
        public String getUrl() {
            return url;
        }
    }

// 类作用：定义 RemoteImage，承载所在模块的主要职责。
    public static final class RemoteImage {
        private final String path;
        private final String url;
        private final long size;

// 方法作用：初始化 RemoteImage 对象并建立其运行所需状态。
        private RemoteImage(String path, String url, long size) {
            this.path = path;
            this.url = url;
            this.size = size;
        }

// 方法作用：读取并返回持久化或运行时状态（getPath）。
        public String getPath() {
            return path;
        }

// 方法作用：读取并返回持久化或运行时状态（getUrl）。
        public String getUrl() {
            return url;
        }

// 方法作用：读取并返回持久化或运行时状态（getSize）。
        public long getSize() {
            return size;
        }

// 方法作用：读取并返回持久化或运行时状态（getFileName）。
        public String getFileName() {
            int slash = path.lastIndexOf('/');
            return slash < 0 ? path : path.substring(slash + 1);
        }
    }
}
