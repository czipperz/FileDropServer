package com.github.czipperz.filedrop;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Sender {
    public static final int timeout = 1000;
    public static final int port = 5981;

    public static void sendFileTransferRequest(final String ip, final String fileName, final InputStream fileStream) {
        if (ip != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), timeout);
                        try (BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream())) {
                            stream.write(Request.FileTransfer);
                            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_16);
                            int high = (fileNameBytes.length << 16) & 0xFF;
                            int low = fileNameBytes.length & 0xFF;
                            if (fileNameBytes.length >= 256 * 256) throw new AssertionError();
                            stream.write(high);
                            stream.write(low);
                            System.out.printf("High: %d, Low: %d, fileNameBytes.length: %d\n", high, low, fileNameBytes.length);
                            stream.write(fileNameBytes);
                            byte fileStreamSegment[] = new byte[1024];
                            int len;
                            while ((len = fileStream.read(fileStreamSegment)) >= 0) {
                                stream.write(fileStreamSegment, 0, len);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public static void sendOpenUriRequest(final String ip, final String uri) {
        if (ip != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), timeout);
                        try (BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream())) {
                            stream.write(Request.OpenUri);
                            stream.write(uri.getBytes(StandardCharsets.UTF_16));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public static void sendSetClipboardRequest(final String ip, final String clipboardContents) {
        if (ip != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), timeout);
                        try (BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream())) {
                            stream.write(Request.SetClipboard);
                            stream.write(clipboardContents.getBytes("UTF-16"));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public static void sendSaveDeviceRequest(final String ip) {
        if (ip != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), 1000);
                        try (BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream())) {
                            stream.write(Request.SaveDevice);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

}
