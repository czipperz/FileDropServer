package com.github.czipperz.filedrop;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class App extends Application implements ServerVisitor {
    private File downloadFolderFile = new File("C:/Users/gregorcj/.FileDropDownloadFolder.txt");
    private String downloadsDirectory;
    private ArrayList<String> pairedIPs;
    private boolean sendClipboard = true, receiveClipboard = true, showNotifications = true;

    private Server serverThread = null;
    private Timeline ipUpdater;

    private boolean ignoreClipboardEvent = false;
    private FlavorListener clipboardListener = clipboard -> {
        if (ignoreClipboardEvent) {
            ignoreClipboardEvent = false;
        } else if (sendClipboard) {
            for (String pairedIP : pairedIPs) {
                try {
                    String s = (String) ((Clipboard) clipboard.getSource()).getData(DataFlavor.stringFlavor);
                    System.out.printf("Sending new clipboard %s\n", s);
                    Sender.sendSetClipboardRequest(pairedIP, s);
                } catch (UnsupportedFlavorException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private static final String foregroundColorS = "#1B5E20";
    private static final Color foregroundColor = Color.web(foregroundColorS);
    private static final Color backgroundColor = Color.web("#FFFFFF");
    /*
    private static final String foregroundColorS = "#388E3C";
    private static final Color foregroundColor = Color.web(foregroundColorS);
    private static final int foregroundColorV = 0x388E3C;
    private static final Color backgroundColor = Color.web("#616161");
    private static final int backgroundColorV = 0x616161;
    */

    private void readDownloadFolderFile() throws IOException {
        System.out.printf("READ file");
        try (FileInputStream file = new FileInputStream(downloadFolderFile)) {
            try (ObjectInputStream obs = new ObjectInputStream(new BufferedInputStream(file))) {
                downloadsDirectory = (String) obs.readObject();
                pairedIPs = (ArrayList<String>) obs.readObject();
                sendClipboard = obs.readBoolean();
                receiveClipboard = obs.readBoolean();
                showNotifications = obs.readBoolean();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeDownloadFolderFile() {
        downloadFolderFile.delete();
        System.out.printf("WRITE file\n");
        try (FileOutputStream file = new FileOutputStream(downloadFolderFile)) {
            try (ObjectOutputStream obs = new ObjectOutputStream(new BufferedOutputStream(file))) {
                obs.writeObject(downloadsDirectory);
                obs.writeObject(pairedIPs);
                obs.writeBoolean(sendClipboard);
                obs.writeBoolean(receiveClipboard);
                obs.writeBoolean(showNotifications);
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }

    private static BufferedImage createQRCode(String contents, int width, int height) throws WriterException {
        MultiFormatWriter writer = new MultiFormatWriter();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); // create an empty image
        int white = 255 << 16 | 255 << 8 | 255;
        int black = 0;
        EnumMap<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        //hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 0); /* default = 4 */
        BitMatrix bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, width, height, hints);
        final int foregroundColorV = foregroundColor.hashCode() >> 8;
        final int backgroundColorV = backgroundColor.hashCode() >> 8;
        boolean first = true;
        int li = 0, lj = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (bitMatrix.get(i, j) && first) {
                    first = false;
                    System.out.printf("(%d, %d)", i, j);
                }
                if (bitMatrix.get(i, j)) {
                    li = i;
                    lj = j;
                }
                image.setRGB(i, j, bitMatrix.get(i, j) ? foregroundColorV : backgroundColorV); // set pixel one by one
            }
        }
        System.out.printf(" to (%d, %d)\n", li, lj);
        return image;
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            Enumeration<NetworkInterface> eni = NetworkInterface.getNetworkInterfaces();
            while (eni.hasMoreElements()) {
                NetworkInterface ni = eni.nextElement();
                if (ni != null) {
                    byte[] addr = ni.getHardwareAddress();
                    if (addr != null) {
                        for (byte b : addr) {
                            System.out.printf("%02X ", b);
                        }
                        System.out.println();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        StackPane stackPane = new StackPane();
        stackPane.setBackground(new Background(new BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY)));

        GridPane mainPane = new GridPane();
        ColumnConstraints column1 = new ColumnConstraints();
        column1.setHgrow(Priority.ALWAYS);
        mainPane.getColumnConstraints().add(column1);
        RowConstraints row1 = new RowConstraints();
        row1.setVgrow(Priority.NEVER);
        RowConstraints row2 = new RowConstraints();
        row2.setVgrow(Priority.ALWAYS);
        RowConstraints row3 = new RowConstraints();
        row3.setVgrow(Priority.SOMETIMES);
        mainPane.getRowConstraints().addAll(row1, row2, row3);
        //mainPane.setGridLinesVisible(true);
        stackPane.getChildren().add(mainPane);

        VBox ipPane = new VBox();
        ipPane.setPadding(new Insets(16));
        ipPane.setSpacing(8);
        ipPane.setAlignment(Pos.TOP_CENTER);
        mainPane.add(ipPane, 0, 0, 1, 1);

        Label ipLabel = new Label();
        ipLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 40));
        ipLabel.setTextFill(foregroundColor);
        ipLabel.setAlignment(Pos.CENTER);
        ipPane.getChildren().add(ipLabel);
        ImageView qrImage = new ImageView();
        ipPane.getChildren().add(qrImage);

        EventHandler<ActionEvent> ipUpdateHandler = ignore -> {
            try (final DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                String ip = socket.getLocalAddress().getHostAddress();
                if (!ip.equals(ipLabel.getText())) {
                    System.out.printf("IP: %s\n", ip);
                    ipLabel.setText(ip);
                    qrImage.setImage(SwingFXUtils.toFXImage(createQRCode(ip, 200, 200), null));
                }
            } catch (WriterException | SocketException | UnknownHostException e) {
                e.printStackTrace();
            }
        };
        ipUpdateHandler.handle(null);
        ipUpdater = new Timeline(new KeyFrame(Duration.seconds(1), ipUpdateHandler));
        ipUpdater.setCycleCount(Animation.INDEFINITE);
        ipUpdater.play();

        VBox settingsPane = new VBox();
        settingsPane.setPadding(new Insets(16));
        settingsPane.setSpacing(8);
        settingsPane.setVisible(true);
        //settingsPane.setBackground(new Background(new BackgroundFill(Color.web("#666", .9), CornerRadii.EMPTY, Insets.EMPTY)));
        mainPane.add(settingsPane, 0, 2, 1, 1);

        HBox downloadsDirectoryPane = new HBox();
        downloadsDirectoryPane.setSpacing(8);
        downloadsDirectoryPane.setAlignment(Pos.CENTER_LEFT);

        Label downloadsDirectoryLabel = new Label("Downloads Directory:");
        downloadsDirectoryLabel.setTextFill(foregroundColor);
        downloadsDirectoryLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 20));
        downloadsDirectoryPane.getChildren().add(downloadsDirectoryLabel);

        Button downloadsDirectoryOpenButton = new Button("Open");
        downloadsDirectoryOpenButton.setTextFill(foregroundColor);
        downloadsDirectoryOpenButton.setFont(Font.font("Arial", FontWeight.NORMAL, 20));
        downloadsDirectoryOpenButton.setOnAction(ignore -> {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    System.out.printf("Open '%s' in explorer\n", downloadsDirectory);
                    new ProcessBuilder("explorer.exe", "/select," + downloadsDirectory).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    Desktop.getDesktop().browse(new URI(downloadsDirectory));
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });
        downloadsDirectoryPane.getChildren().add(downloadsDirectoryOpenButton);

        settingsPane.getChildren().add(downloadsDirectoryPane);

        TextField downloadsDirectoryField = new TextField();
        System.out.printf("Foreground Color: %s\n", foregroundColor.toString());
        downloadsDirectoryField.setStyle(String.format("-fx-text-fill: %s;", foregroundColorS));
        //downloadsDirectoryField.setTextFill(foregroundColor);
        downloadsDirectoryField.setEditable(false);
        try {
            downloadsDirectory = null;
            pairedIPs = new ArrayList<>();
            readDownloadFolderFile();
            downloadsDirectoryField.setText(downloadsDirectory);
        } catch (IOException e) {
            e.printStackTrace();
            downloadsDirectoryField.setText("No Directory Set");
        }
        System.out.printf("Download Folder File: %s\nPaired IPs: %s\n", downloadsDirectory, pairedIPs.toString());
        downloadsDirectoryField.setFont(Font.font("Arial", FontWeight.NORMAL, 20));
        downloadsDirectoryField.setOnMouseClicked(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setInitialDirectory(new File(downloadsDirectory == null ? System.getenv("HOME") : downloadsDirectory));
            chooser.setTitle("Set Download Directory");
            File selectedDirectory = chooser.showDialog(primaryStage);
            if (selectedDirectory != null) {
                downloadsDirectory = selectedDirectory.toString();
                System.out.printf("New download directory: '%s'\n", downloadsDirectory);
                downloadsDirectoryField.setText(downloadsDirectory);
                writeDownloadFolderFile();
            } else {
                System.out.printf("Canceled selecting download directory\n");
            }
        });
        settingsPane.getChildren().add(downloadsDirectoryField);

        CheckBox sendClipboardCB = new CheckBox();
        sendClipboardCB.setOnAction(ignore -> {
            sendClipboard = !sendClipboard;
            writeDownloadFolderFile();
        });
        sendClipboardCB.setTextFill(foregroundColor);
        sendClipboardCB.setSelected(sendClipboard);
        sendClipboardCB.setText("Send Clipboard");
        sendClipboardCB.setFont(Font.font("Arial", FontWeight.NORMAL, 20));
        settingsPane.getChildren().add(sendClipboardCB);

        CheckBox receiveClipboardCB = new CheckBox();
        receiveClipboardCB.setOnAction(ignore -> {
            receiveClipboard = !receiveClipboard;
            writeDownloadFolderFile();
        });
        receiveClipboardCB.setTextFill(foregroundColor);
        receiveClipboardCB.setSelected(receiveClipboard);
        receiveClipboardCB.setText("Receive Clipboard");
        receiveClipboardCB.setFont(Font.font("Arial", FontWeight.NORMAL, 20));
        settingsPane.getChildren().add(receiveClipboardCB);

        CheckBox showNotificationsCB = new CheckBox();
        showNotificationsCB.setOnAction(ignore -> {
            showNotifications = !showNotifications;
            writeDownloadFolderFile();
        });
        showNotificationsCB.setTextFill(foregroundColor);
        showNotificationsCB.setSelected(showNotifications);
        showNotificationsCB.setText("Show Notifications");
        showNotificationsCB.setFont(Font.font("Arial", FontWeight.NORMAL, 20));
        settingsPane.getChildren().add(showNotificationsCB);

        Group controlsGroup = new Group();
        GridPane.setHalignment(controlsGroup, HPos.RIGHT);
        GridPane.setValignment(controlsGroup, VPos.TOP);
        mainPane.add(controlsGroup, 0, 0, 1, 1);

        HBox controlsPane = new HBox();
        controlsPane.setSpacing(8);
        controlsPane.setPadding(new Insets(8));
        controlsPane.setAlignment(Pos.TOP_RIGHT);
        //controlsPane.setBackground(new Background(new BackgroundFill(foregroundColor, CornerRadii.EMPTY, Insets.EMPTY)));
        controlsGroup.getChildren().add(controlsPane);

        Image closeImageForeground = new Image("close.png");
        StackPane closeImagePane = new StackPane();
        ImageView closeImage = new ImageView(closeImageForeground);
        closeImage.setClip(new ImageView(closeImageForeground));
        closeImage.setCache(true);
        closeImage.setCacheHint(CacheHint.SPEED);
        ColorAdjust monochrome = new ColorAdjust();
        monochrome.setSaturation(-1.0);
        Blend withForegroundColor = new Blend(BlendMode.SRC_OVER, monochrome,
                new ColorInput(0, 0, closeImageForeground.getWidth(), closeImageForeground.getHeight(), foregroundColor));
        Blend withBackgroundColor = new Blend(BlendMode.SRC_OVER, monochrome,
                new ColorInput(0, 0, closeImageForeground.getWidth(), closeImageForeground.getHeight(), backgroundColor));
        closeImagePane.getChildren().add(closeImage);
        closeImage.setEffect(withForegroundColor);
        closeImagePane.setOnMouseEntered(ignore -> {
            closeImagePane.setBackground(new Background(new BackgroundFill(foregroundColor, CornerRadii.EMPTY, Insets.EMPTY)));
            closeImage.setEffect(withBackgroundColor);
        });
        closeImagePane.setOnMouseExited(ignore -> {
            closeImagePane.setBackground(null);
            closeImage.setEffect(withForegroundColor);
        });
        closeImagePane.setOnMouseClicked(ignore -> {
            //close();
            primaryStage.setIconified(true);
        });
        controlsPane.getChildren().add(new Group(closeImagePane));

        Rectangle borderRectangle = new Rectangle();
        borderRectangle.widthProperty().bind(primaryStage.widthProperty());
        borderRectangle.heightProperty().bind(primaryStage.heightProperty());
        borderRectangle.setFill(null);
        borderRectangle.setStroke(foregroundColor);
        borderRectangle.setStrokeType(StrokeType.CENTERED);
        borderRectangle.setStrokeWidth(4);
        stackPane.getChildren().add(borderRectangle);

        StackPane dragPane = new StackPane();
        dragPane.setPadding(new Insets(32));
        dragPane.setBackground(new Background(new BackgroundFill(Color.web("#666", .9), CornerRadii.EMPTY, Insets.EMPTY)));
        Label dropTargetText = new Label();
        dropTargetText.setFont(Font.font("Arial", FontWeight.NORMAL, 40));
        dropTargetText.setTextFill(Color.web("#FFFFFF"));
        dropTargetText.setWrapText(true);
        dropTargetText.setTextAlignment(TextAlignment.CENTER);
        StackPane.setAlignment(dropTargetText, Pos.CENTER);
        dragPane.getChildren().add(dropTargetText);
        dragPane.setVisible(false);
        stackPane.getChildren().add(dragPane);

        stackPane.setOnDragOver(event -> {
            Dragboard dragboard = event.getDragboard();
            if (event.getGestureSource() != stackPane) {
                if (dragboard.hasFiles()) {
                    event.acceptTransferModes(TransferMode.COPY);
                    dropTargetText.setText("Drop here to copy to your phone");
                    event.consume();
                } else if (dragboard.hasUrl()) {
                    event.acceptTransferModes(TransferMode.COPY);
                    dropTargetText.setText("Drop here to open link on your phone");
                    event.consume();
                } else if (dragboard.hasString()) {
                    event.acceptTransferModes(TransferMode.COPY);
                    dropTargetText.setText("Drop here to copy to your phone's clipboard");
                    event.consume();
                }
            }
        });
        stackPane.setOnDragEntered(event -> {
            Dragboard dragboard = event.getDragboard();
            if (event.getGestureSource() != stackPane && (dragboard.hasFiles() || dragboard.hasUrl() || dragboard.hasString())) {
                dragPane.setVisible(true);
                event.consume();
            }
        });
        stackPane.setOnDragExited(event -> {
            Dragboard dragboard = event.getDragboard();
            if (event.getGestureSource() != stackPane && (dragboard.hasFiles() || dragboard.hasUrl() || dragboard.hasString())) {
                dragPane.setVisible(false);
                event.consume();
            }
        });
        stackPane.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            if (event.getGestureSource() != stackPane) {
                Consumer<String> command = pairedIP -> {
                    if (dragboard.hasFiles()) {
                        System.out.printf("Dragged file '%s'... (%d)\n", dragboard.getFiles().get(0).toString(), dragboard.getFiles().size());
                        event.setDropCompleted(true);
                        event.consume();
                    } else if (dragboard.hasUrl()) {
                        System.out.printf("Dragged url '%s'\n", dragboard.getUrl());
                        Sender.sendOpenUriRequest(pairedIP, dragboard.getUrl());
                        event.setDropCompleted(true);
                        event.consume();
                    } else if (dragboard.hasString()) {
                        System.out.printf("Dragged '%s'\n", dragboard.getString());
                        Sender.sendSetClipboardRequest(pairedIP, dragboard.getString());
                        event.setDropCompleted(true);
                        event.consume();
                    }
                };
                if (pairedIPs.isEmpty()) {
                    throw new AssertionError();
                } else if (pairedIPs.size() == 1) {
                    command.accept(pairedIPs.get(0));
                } else {
                    throw new AssertionError();
                }
            }
        });

        Toolkit.getDefaultToolkit().getSystemClipboard().addFlavorListener(clipboardListener);

        serverThread = new Server(this);
        serverThread.start();

        setupTrayIcon(primaryStage);

        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setTitle("File Drop");
        primaryStage.setOnCloseRequest(ignore -> close());
        primaryStage.setScene(new Scene(stackPane));
        primaryStage.show();
        primaryStage.getIcons().add(new Image("ic_launcher48.png"));
        primaryStage.getIcons().add(new Image("ic_launcher72.png"));
        primaryStage.getIcons().add(new Image("ic_launcher96.png"));
        primaryStage.getIcons().add(new Image("ic_launcher144.png"));
        primaryStage.getIcons().add(new Image("ic_launcher192.png"));
        primaryStage.getIcons().add(new Image("ic_launcher512.png"));
        primaryStage.setMinWidth(325);
        primaryStage.minHeightProperty().bind(ipPane.heightProperty().add(settingsPane.heightProperty()));
        primaryStage.setWidth(primaryStage.getMinWidth());
        primaryStage.setHeight(primaryStage.getMinHeight());
        //primaryStage.minWidthProperty().bind(mainPane.prefWidthProperty());
        //primaryStage.minHeightProperty().bind(mainPane.prefHeightProperty());
        ResizeHelper.addResizeListener(primaryStage);

        stackPane.requestFocus();
    }

    private void close() {
        if (serverThread != null) {
            try {
                serverThread.join();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
        this.ipUpdater.stop();
        removeTrayIcon();
        Toolkit.getDefaultToolkit().getSystemClipboard().removeFlavorListener(clipboardListener);
    }

    private TrayIcon trayIcon;
    private AtomicReference<ActionListener> listener = new AtomicReference<>(ignore -> {
    });

    private void setupTrayIcon(Stage stage) {
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            trayIcon = new TrayIcon(image, "File Drop");
            trayIcon.setImageAutoSize(true);
            trayIcon.setToolTip("Open File Drop");
            PopupMenu popup = new PopupMenu();
            MenuItem showMenuItem = new MenuItem("Show");
            showMenuItem.addActionListener(ignore -> {
                // TODO
                System.out.printf("Showing here doesn't work for some reason\n");
                Platform.runLater(() -> stage.setIconified(false));
                //Platform.runLater(stage::show);
            });
            popup.add(showMenuItem);
            MenuItem quitMenuItem = new MenuItem("Quit");
            quitMenuItem.addActionListener(ignore -> {
                if (stage.isShowing()) {
                    Platform.runLater(() -> {
                        close();
                        stage.close();
                    });
                } else {
                    close();
                }
            });
            popup.add(quitMenuItem);
            trayIcon.setPopupMenu(popup);
            trayIcon.addActionListener(ignore -> {
                System.out.printf("Is showing: %b\n", stage.isShowing());
                Platform.runLater(stage::show);
                /*
                if (stage.isShowing()) {
                } else {
                    stage.show();
                }*/
            });
            trayIcon.setImage(SwingFXUtils.fromFXImage(new Image("ic_launcher48.png"), null));
            trayIcon.addActionListener(e -> {
                ActionListener actionListener = listener.get();
                if (actionListener != null) {
                    actionListener.actionPerformed(e);
                }
            });
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                e.printStackTrace();
            }
        }
    }

    private void removeTrayIcon() {
        if (SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    private void displayNotification(String caption, String text, TrayIcon.MessageType type, ActionListener l) {
        if (SystemTray.isSupported()) {
            trayIcon.displayMessage(caption, text, type);
            listener.set(l);
        }
    }

    @Override
    public void onFileTransfer(String fileName, InputStream contents) {
        if (downloadsDirectory == null) {
            displayNotification("File Transfer Error", "No Downloads Directory Specified", TrayIcon.MessageType.ERROR, null);
        } else {
            final String totalFileName = downloadsDirectory + "\\" + fileName;
            System.out.printf("totalFileName: %s\n", totalFileName);
            try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(totalFileName))) {
                /* use file */
                byte[] fileChunk = new byte[1024];
                int total = 0;
                int len;
                while ((len = contents.read(fileChunk)) >= 0) {
                    total += len;
                    fos.write(fileChunk, 0, len);
                }
                System.out.printf("Total %d\n", total);
                if (showNotifications) {
                    displayNotification("New File Downloaded", fileName, TrayIcon.MessageType.INFO, event -> {
                        System.out.printf("SHOW\n");
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            try {
                                new ProcessBuilder("C:\\Windows\\System32\\cmd.exe", "/c", totalFileName).start();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                System.out.printf("Open '%s' in explorer\n", totalFileName);
                                new ProcessBuilder("explorer.exe", "/select," + totalFileName).start();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                Desktop.getDesktop().open(new File(totalFileName));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                Desktop.getDesktop().browse(new URI(totalFileName));
                            } catch (IOException | URISyntaxException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onOpenUri(String uri) {
        try {
            Desktop.getDesktop().browse(new URI(uri));
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSetClipboard(String clipString) {
        if (receiveClipboard) {
            ignoreClipboardEvent = true;
            StringSelection stringSelection = new StringSelection(clipString);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        }
    }

    @Override
    public void onSaveDevice(String pairedIP) {
        pairedIPs.add(pairedIP);
        writeDownloadFolderFile();
        System.out.printf("Paired with %s\n", pairedIPs.toString());
    }
}
