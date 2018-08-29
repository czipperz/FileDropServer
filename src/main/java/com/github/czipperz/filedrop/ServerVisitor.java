package com.github.czipperz.filedrop;

import java.io.InputStream;

public interface ServerVisitor {
    public void onFileTransfer(String fileName, InputStream contents);
    public void onOpenUri(String uri);
    public void onSetClipboard(String clipString);
    public void onSaveDevice(String ip);
}
