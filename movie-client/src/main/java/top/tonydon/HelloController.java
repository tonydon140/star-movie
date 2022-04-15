package top.tonydon;


import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import top.tonydon.client.WebClient;
import top.tonydon.message.client.*;
import top.tonydon.message.server.*;
import top.tonydon.util.ActionCode;
import top.tonydon.util.ClientObserver;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

@Slf4j
public class HelloController {

    @FXML
    public Label selfNumberLabel;
    @FXML
    public Label targetNumberLabel;
    public AnchorPane root;
    public Button bindButton;
    public ButtonBar togetherButtonBar;
    public MediaView mediaView;
    public Slider videoSlider;
    public Button playOrPauseButton;


    private WebClient client;
    private Stage primaryStage;
    private boolean mouse = false;



    @FXML
    private void initialize() {
        // 1. 创建 websocket 客户端
        try {
            client = new WebClient(new URI("ws://localhost:8080/websocket"));
            boolean flag = client.connectBlocking();
            if (!flag) {
                selfNumberLabel.setText("连接服务器失败...");
                return;
            }

            // 添加观察者
            client.addObserver(new ClientObserver() {
                @Override
                public void onConnected(ServerConnectMessage message) {
                    Platform.runLater(() -> selfNumberLabel.setText(message.getNumber()));
                }

                @Override
                public void onMovie(ServerMovieMessage message) {
                    if (message.getActionCode() == ActionCode.PLAY) {
                        mediaView.getMediaPlayer().play();
                    } else if (message.getActionCode() == ActionCode.PAUSE) {
                        mediaView.getMediaPlayer().pause();
                    } else if (message.getActionCode() == ActionCode.STOP) {
                        mediaView.getMediaPlayer().stop();
                    }
                }

                @Override
                public void onBind(ServerBindMessage message) {
                    // 将客户端设置为已绑定，并更新 ui
                    client.isBind = true;
                    Platform.runLater(() -> {
                        togetherButtonBar.setDisable(false);
                        targetNumberLabel.setText(message.getTargetNumber());
                        bindButton.setText("解除绑定");
                    });
                }

                @Override
                public void onUnBind(ServerUnbindMessage message) {
                    // 解除绑定
                    client.isBind = false;
                    Platform.runLater(() -> {
                        togetherButtonBar.setDisable(true); // 禁用一起播放按钮组
                        targetNumberLabel.setText("");      // 清空另一半的星星号
                        bindButton.setText("绑定他/她");     // 重置绑定按钮名称
                    });
                }

                @Override
                public void onOffline(ServerOfflineMessage message) {
                    onUnBind(new ServerUnbindMessage());
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("信息");
                        alert.setHeaderText("另一半断开连接");
                        alert.initModality(Modality.WINDOW_MODAL);
                        alert.initOwner(primaryStage);

                        // 4. 显示窗口
                        Optional<ButtonType> result = alert.showAndWait();
                    });
                }
            });
        } catch (URISyntaxException | InterruptedException e) {
            e.printStackTrace();
        }

        // 2. 进度条的监听事件
        videoSlider.setOnMousePressed(event -> mouse = true);
        videoSlider.setOnMouseReleased(event -> {
            mouse = false;
            mediaView.getMediaPlayer().seek(Duration.seconds(videoSlider.getValue()));
        });


//        mediaView.setOnMouseEntered(event -> videoSlider.setVisible(true));
//        mediaView.setOnMouseExited(event -> videoSlider.setVisible(false));
    }

    /**
     * 页面加载完毕后执行的初始化操作
     */
    public void initData() {
        log.debug(root.toString());
        log.debug(root.getScene().toString());
        // 1. 获取主窗口
        primaryStage = (Stage) root.getScene().getWindow();

        // 2. 获取主屏幕
        Screen screen = Screen.getPrimary();

        // 2. 添加全屏效果
        primaryStage.fullScreenProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                Rectangle2D bounds = screen.getBounds();
                mediaView.setFitWidth(bounds.getWidth());
                mediaView.setFitHeight(bounds.getHeight());
            }else{
                mediaView.setFitWidth(960);
                mediaView.setFitHeight(540);
            }
        });
    }


    @FXML
    public void bindButtonAction(ActionEvent actionEvent) {
        if (client.isBind) unbindNumber(actionEvent);
        else bindNumber(actionEvent);
    }

    /**
     * 解除绑定
     *
     * @param actionEvent 点击事件
     */
    public void unbindNumber(ActionEvent actionEvent) {
        // 1. 创建确认信息
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认消息");
        alert.setHeaderText("确定要解绑吗");

        // 2. 设置主窗口
        alert.initModality(Modality.WINDOW_MODAL);
        alert.initOwner(primaryStage);

        // 4. 显示窗口
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ClientUnbindMessage message = new ClientUnbindMessage(client.getSelfNumber());
            client.send(message.toJson());
        }
    }


    /**
     * 绑定另一个人
     *
     * @param actionEvent 点击事件
     */
    public void bindNumber(ActionEvent actionEvent) {
        // 1. 创建对话框
        TextInputDialog inputDialog = new TextInputDialog();
        inputDialog.setTitle("绑定他/她");
        inputDialog.setHeaderText("绑定他/她");
        inputDialog.setContentText("请输入他/她的星星号：");

        // 2. 设置主窗口
        inputDialog.initModality(Modality.WINDOW_MODAL);
        inputDialog.initOwner(primaryStage);

        // 3. 显示窗口
        Optional<String> result = inputDialog.showAndWait();

        // 4. 点击确定后发送消息
        if (result.isPresent()) {
            ClientBindMessage clientBindMessage = new ClientBindMessage(client.getSelfNumber(), result.get());
            client.send(clientBindMessage.toJson());
        }
    }



    @FXML
    public void selectVideo(ActionEvent actionEvent) {
        // 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        // 设置过滤器，第一个参数是描述文本，第二个参数是过滤规则
        FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("MP4 文件（*.mp4）", "*.mp4");
        fileChooser.getExtensionFilters().add(filter);          // 添加过滤器
        File file = fileChooser.showOpenDialog(primaryStage);   // 打开文件选择器，返回选择的文件

        // 如果未选择文件，直接返回
        if(file == null) return;

        // 如果之前已经选择了视频，则销毁之前的视频
        if (mediaView.getMediaPlayer() != null) mediaView.getMediaPlayer().dispose();

        // 将文件转为 uri 路径，加载媒体视频
        String uri = "file:" + file.toPath().toUri().getPath();
        Media media = new Media(uri);
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);

        // 3. 媒体加载完毕后
        mediaPlayer.setOnReady(() -> {
            // 1. 播放按钮解禁
            playOrPauseButton.setDisable(false);

            // 2. 设置进度条
            Duration duration = mediaPlayer.getTotalDuration();
            videoSlider.setMax(duration.toSeconds());
            videoSlider.setVisible(true);
            mediaPlayer.currentTimeProperty().addListener((observable, oldValue, newValue) -> {
                if (!mouse) {
                    videoSlider.setValue(newValue.toSeconds());
                }
            });

            // 3. 设置视频点击播放/暂停
            mediaView.setOnMouseClicked(event -> playOrPause(new ActionEvent()));
        });
    }

    @FXML
    public void playOrPause(ActionEvent actionEvent) {
        MediaPlayer player = mediaView.getMediaPlayer();
        MediaPlayer.Status status = player.getStatus();

        if (status == MediaPlayer.Status.PLAYING) {
            player.pause();
            playOrPauseButton.setText("播放");
        } else if (status == MediaPlayer.Status.STOPPED ||
                status == MediaPlayer.Status.PAUSED ||
                status == MediaPlayer.Status.READY) {
            player.play();
            playOrPauseButton.setText("暂停");
        }
    }

    @FXML
    public void fullScreen(ActionEvent actionEvent) {
        primaryStage.setFullScreen(true);
    }

    @FXML
    public void togetherPlay(ActionEvent actionEvent) {
        ClientMovieMessage message = new ClientMovieMessage(client.getSelfNumber(), ActionCode.PLAY);
        client.send(message.toJson());
    }

    @FXML
    public void togetherPause(ActionEvent actionEvent) {
        ClientMovieMessage message = new ClientMovieMessage(client.getSelfNumber(), ActionCode.PAUSE);
        client.send(message.toJson());
    }

    @FXML
    public void togetherStop(ActionEvent actionEvent) {
        ClientMovieMessage message = new ClientMovieMessage(client.getSelfNumber(), ActionCode.STOP);
        client.send(message.toJson());
    }
}