package com.github.czipperz.filedrop;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.czipperz.filedrop.Sender.port;

public class Server implements Runnable {
    private ServerVisitor visitor;
    private Thread thread;
    private AtomicBoolean done = new AtomicBoolean(false);

    public Server(ServerVisitor visitor) {
        this.visitor = visitor;
        this.thread = new Thread(this);
    }

    public void start() {
        thread.start();
    }

    public void join() throws InterruptedException {
        done.set(true);
        thread.join();
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server in");
            serverSocket.setSoTimeout(10);
            while (!done.get()) {
                try (Socket clientSocket = serverSocket.accept()) {
                    try (BufferedInputStream in = new BufferedInputStream(clientSocket.getInputStream())) {
                        switch (in.read()) {
                            case Request.FileTransfer: {
                                int high = in.read();
                                int low = in.read();
                                int fileNameLength = high * 256 + low;
                                System.out.printf("High: %d, Low: %d, fileNameBytes.length: %d\n", high, low, fileNameLength);
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                byte array[] = new byte[fileNameLength];
                                int len;
                                if ((len = in.read(array)) >= 0) {
                                    out.write(array, 0, len);
                                } else {
                                    throw new IOException();
                                }
                                String fileName = new String(out.toByteArray(), "UTF-16");
                                visitor.onFileTransfer(fileName, in);
                                break;
                            }
                            case Request.OpenUri: {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                byte array[] = new byte[1024];
                                int len;
                                while ((len = in.read(array)) >= 0) {
                                    out.write(array, 0, len);
                                }
                                String uri = new String(out.toByteArray(), "UTF-16");
                                System.out.printf("Received uri '%s'\n", uri);
                                visitor.onOpenUri(uri);
                                break;
                            }
                            case Request.SetClipboard: {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                byte array[] = new byte[1024];
                                int len;
                                while ((len = in.read(array)) >= 0) {
                                    out.write(array, 0, len);
                                }
                                String clipString = new String(out.toByteArray(), "UTF-16");
                                System.out.printf("Received clipboard '%s'\n", clipString);
                                visitor.onSetClipboard(clipString);
                                break;
                            }
                            case Request.SaveDevice: {
                                visitor.onSaveDevice(clientSocket.getInetAddress().getHostAddress());
                                break;
                            }
                            default:
                                System.err.println("Invalid object id.");
                                break;
                        }
                    }
                } catch (SocketTimeoutException ignored) {

                }
            }
            System.out.println("Server out");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
