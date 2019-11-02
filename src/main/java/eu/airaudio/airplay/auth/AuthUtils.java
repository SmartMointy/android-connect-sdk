package eu.airaudio.airplay.auth;

import android.net.Uri;
import android.util.Log;

import com.dd.plist.NSDictionary;
import com.dd.plist.PropertyListParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;


/**
 * Created by Martin on 24.05.2017.
 */
public class AuthUtils {
    public static byte[] concatByteArrays(byte[]... byteArrays) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        for (byte[] bytes : byteArrays) {
            byteArrayOutputStream.write(bytes);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static byte[] createPList(Map<String, ? extends Object> properties) throws IOException {
        ByteArrayOutputStream plistOutputStream = new ByteArrayOutputStream();
        NSDictionary root = new NSDictionary();
        for (Map.Entry<String, ? extends Object> property : properties.entrySet()) {
            root.put(property.getKey(), property.getValue());
        }
        PropertyListParser.saveAsBinary(root, plistOutputStream);
        return plistOutputStream.toByteArray();
    }

    public static byte[] performRequest(Socket socket, String method, String path, @Nullable String contentType, @Nullable byte[] data) throws IOException {
        final Uri uri = Uri.parse("http:/" + socket.getInetAddress().toString() + ":" + socket.getPort() + path);
        Log.d("AirPlayService","performRequest: " + method + " " + uri.toString() + " > " + contentType + ": " + (data != null ? data.length : 0));
        Log.d("AirPlayService", "body: " + (data != null ? bytesToHex(data) : "<none>"));
        DataOutputStream wr = new DataOutputStream(socket.getOutputStream());
        wr.writeBytes(method + " " + path + " HTTP/1.1\r\n");
        wr.writeBytes("Host: " + uri.getHost() + "\r\n");
        wr.writeBytes("Connection: keep-alive\r\n");

        if (data != null) {
            wr.writeBytes("Content-Length: " + data.length + "\r\n");
            wr.writeBytes("Content-Type: " + contentType + "\r\n");
        }
        wr.writeBytes("\r\n");

        if (data != null) {
            wr.write(data);
        }
        wr.flush();

        String line;

        Pattern statusPattern = Pattern.compile("HTTP[^ ]+ (\\d{3})");
        Pattern contentLengthPattern = Pattern.compile("Content-Length: (\\d+)");

        int contentLength = 0;
        int statusCode = 0;

        while ((line = AuthUtils.readLine(socket.getInputStream())) != null) {
            System.out.println(line);
            Matcher statusMatcher = statusPattern.matcher(line);
            if (statusMatcher.find()) {
                statusCode = Integer.parseInt(statusMatcher.group(1));
            }
            Matcher contentLengthMatcher = contentLengthPattern.matcher(line);
            if (contentLengthMatcher.find()) {
                contentLength = Integer.parseInt(contentLengthMatcher.group(1));
            }
            if (line.trim().isEmpty()) {
                break;
            }
        }

        if (statusCode != 200) {
            throw new IOException("Invalid status code " + statusCode);
        }

        ByteArrayOutputStream response = null;
        try {
            response = new ByteArrayOutputStream();
            byte[] buffer = new byte[0xFFFF];

            for (int len; response.size() < contentLength && (len = socket.getInputStream().read(buffer)) != -1; ) {
                response.write(buffer, 0, len);
            }

            response.flush();

            return response.toByteArray();
        }finally{
            if(response != null) {
                response.close();
            }
        }
    }

    private static String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int c;
        for (c = inputStream.read(); c != '\n' && c != -1; c = inputStream.read()) {
            byteArrayOutputStream.write(c);
        }
        if (c == -1 && byteArrayOutputStream.size() == 0) {
            return null;
        }
        String line = byteArrayOutputStream.toString("UTF-8");
        return line;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String randomString(final int length) {
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
            sb.append(chars[rnd.nextInt(chars.length)]);

        return sb.toString();
    }

}
