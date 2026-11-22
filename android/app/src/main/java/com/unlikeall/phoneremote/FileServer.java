package com.unlikeall.phoneremote;

import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Мини HTTP-сервер: отдаёт файлы телефона приставке (Kodi) с поддержкой Range — без копирования. */
public class FileServer {
    static class Entry {
        ParcelFileDescriptor pfd;
        String name, mime;
        long size;
    }

    private static FileServer inst;
    private ServerSocket ss;
    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    static synchronized FileServer get() throws IOException {
        if (inst == null || inst.ss == null || inst.ss.isClosed()) {
            inst = new FileServer();
            inst.start();
        }
        return inst;
    }

    static synchronized void stopAll() {
        if (inst != null) inst.close();
        inst = null;
    }

    private void start() throws IOException {
        ss = new ServerSocket(0);
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    final Socket s = ss.accept();
                    new Thread(() -> serve(s)).start();
                } catch (IOException e) {
                    break;
                }
            }
        }, "pr-accept");
        t.setDaemon(true);
        t.start();
    }

    private void close() {
        try {
            ss.close();
        } catch (Exception ignored) {
        }
        for (Entry e : map.values()) {
            try {
                e.pfd.close();
            } catch (Exception ignored) {
            }
        }
        map.clear();
    }

    int count() {
        return map.size();
    }

    /** Возвращает адрес файла для Kodi. */
    String add(ParcelFileDescriptor pfd, String name, String mime, long size) {
        Entry e = new Entry();
        e.pfd = pfd;
        e.name = name;
        e.mime = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
        if (size <= 0) {
            try {
                size = pfd.getStatSize();
            } catch (Exception ignored) {
            }
        }
        if (size <= 0) {
            try {
                size = new FileInputStream(pfd.getFileDescriptor()).getChannel().size();
            } catch (Exception ignored) {
            }
        }
        e.size = size;
        String tok = UUID.randomUUID().toString().replace("-", "");
        map.put(tok, e);
        String enc;
        try {
            enc = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (Exception ex) {
            enc = "video";
        }
        return "http://" + localIp() + ":" + ss.getLocalPort() + "/f/" + tok + "/" + enc;
    }

    static String localIp() {
        String best = null;
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (n.getName().startsWith("wlan") || n.getName().startsWith("eth")) return a.getHostAddress();
                        if (best == null) best = a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return best == null ? "127.0.0.1" : best;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16384) throw new IOException("long line");
        }
        if (c < 0 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }

    private void serve(Socket s) {
        try {
            s.setSoTimeout(60000);
            s.setTcpNoDelay(true);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String req = readLine(in);
            if (req == null) return;
            String range = null;
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                if (h.toLowerCase(Locale.ROOT).startsWith("range:")) range = h.substring(6).trim();
            }
            String[] p = req.split(" ");
            if (p.length < 2) return;
            boolean head = p[0].equals("HEAD");
            String[] seg = p[1].split("\\?")[0].split("/");
            Entry e = seg.length >= 3 && seg[1].equals("f") ? map.get(seg[2]) : null;
            if (e == null || (!head && !p[0].equals("GET"))) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes());
                out.flush();
                return;
            }
            long size = e.size, start = 0, end = size - 1;
            boolean partial = false;
            if (range != null && range.startsWith("bytes=") && size > 0) {
                String r = range.substring(6).split(",")[0].trim();
                int d = r.indexOf('-');
                try {
                    if (d == 0) {
                        start = Math.max(0, size - Long.parseLong(r.substring(1)));
                    } else {
                        start = Long.parseLong(r.substring(0, d));
                        if (d < r.length() - 1) end = Math.min(end, Long.parseLong(r.substring(d + 1)));
                    }
                    partial = true;
                } catch (Exception ex) {
                    start = 0;
                    end = size - 1;
                }
                if (start > end || start >= size) {
                    out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + size + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes());
                    out.flush();
                    return;
                }
            }
            long len = size > 0 ? end - start + 1 : -1;
            StringBuilder sb = new StringBuilder();
            sb.append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
            sb.append("Content-Type: ").append(e.mime).append("\r\n");
            sb.append("Accept-Ranges: bytes\r\n");
            if (len >= 0) sb.append("Content-Length: ").append(len).append("\r\n");
            if (partial) sb.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(size).append("\r\n");
            sb.append("Connection: close\r\n\r\n");
            out.write(sb.toString().getBytes("UTF-8"));
            if (!head) {
                FileChannel ch = new FileInputStream(e.pfd.getFileDescriptor()).getChannel();
                byte[] buf = new byte[256 * 1024];
                long pos = start, left = len;
                while (left != 0) {
                    int want = left < 0 ? buf.length : (int) Math.min(buf.length, left);
                    int n = ch.read(ByteBuffer.wrap(buf, 0, want), pos);
                    if (n <= 0) break;
                    out.write(buf, 0, n);
                    pos += n;
                    if (left > 0) left -= n;
                }
            }
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Список адресов — для отладки. */
    ArrayList<String> names() {
        ArrayList<String> r = new ArrayList<>();
        for (Entry e : map.values()) r.add(e.name);
        return r;
    }
}
