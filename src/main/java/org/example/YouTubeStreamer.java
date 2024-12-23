package org.example;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.*;

public class YouTubeStreamer {
    private JTextField serverKeyField;
    private JTextField bitrateField;
    private JTextField fpsField;
    private JTextField videoPathField;
    private JTextArea statusArea;
    private JButton startButton;
    private JButton stopButton;
    private boolean streaming = false;
    private Process ffmpegProcess;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(YouTubeStreamer::new);
    }

    public YouTubeStreamer() {
        JFrame frame = new JFrame("YouTube Streamer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        serverKeyField = new JTextField();
        bitrateField = new JTextField("2500k"); // Default bitrate
        fpsField = new JTextField("30"); // Default FPS
        videoPathField = new JTextField();
        JButton browseButton = new JButton("Browse");

        inputPanel.add(new JLabel("YouTube Server Key:"));
        inputPanel.add(serverKeyField);
        inputPanel.add(new JLabel("Bitrate (e.g., 2500k):"));
        inputPanel.add(bitrateField);
        inputPanel.add(new JLabel("FPS:"));
        inputPanel.add(fpsField);
        inputPanel.add(new JLabel("Video File:"));
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add(videoPathField, BorderLayout.CENTER);
        filePanel.add(browseButton, BorderLayout.EAST);
        inputPanel.add(filePanel);

        statusArea = new JTextArea();
        statusArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(statusArea);

        JPanel controlPanel = new JPanel(new FlowLayout());
        startButton = new JButton("Start Streaming");
        stopButton = new JButton("Stop Streaming");
        stopButton.setEnabled(false);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        frame.add(inputPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);

        // Browse button functionality
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setAcceptAllFileFilterUsed(true); // Allow all files
            int result = fileChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                videoPathField.setText(selectedFile.getAbsolutePath());
            }
        });

        // Start button functionality
        startButton.addActionListener(e -> {
            if (validateInputs()) {
                if (isFFmpegInstalled()) {
                    startStreaming();
                } else {
                    showPopup("FFmpeg is not installed or not accessible in the system PATH.");
                }
            }
        });

        // Stop button functionality
        stopButton.addActionListener(e -> stopStreaming());

        frame.setVisible(true);
    }

    private boolean validateInputs() {
        String serverKey = serverKeyField.getText().trim();
        String bitrate = bitrateField.getText().trim();
        String fps = fpsField.getText().trim();
        String videoPath = videoPathField.getText().trim();

        if (serverKey.isEmpty()) {
            showPopup("YouTube Server Key is required.");
            return false;
        }

        if (videoPath.isEmpty()) {
            showPopup("Video file is required.");
            return false;
        }

        Path path = Paths.get(videoPath);
        if (!Files.exists(path)) {
            showPopup("The selected file does not exist.");
            return false;
        }

        try {
            String mimeType = Files.probeContentType(path);
            if (mimeType == null || !mimeType.startsWith("video")) {
                showPopup("The selected file is not a valid video format.");
                return false;
            }
        } catch (IOException e) {
            showPopup("Unable to verify the file format.");
            return false;
        }

        if (!bitrate.matches("\\d+k")) {
            showPopup("Invalid bitrate. Use a format like '2500k'.");
            return false;
        }

        if (!fps.matches("\\d+") || Integer.parseInt(fps) <= 0) {
            showPopup("Invalid FPS. FPS must be a positive number.");
            return false;
        }

        return true;
    }

    private boolean isFFmpegInstalled() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void startStreaming() {
        String serverKey = serverKeyField.getText().trim();
        String bitrate = bitrateField.getText().trim();
        String fps = fpsField.getText().trim();
        String videoPath = videoPathField.getText().trim();

        try {
            // Escape file path
            videoPath = videoPath.replace("\\", "\\\\");

            String command = String.format(
                "ffmpeg -re -i \"%s\" -preset veryfast -b:v %s -maxrate %s -bufsize %s -r %s " +
                "-f flv rtmp://a.rtmp.youtube.com/live2/%s",
                videoPath, bitrate, bitrate, bitrate, fps, serverKey
            );

            statusArea.append("Starting FFmpeg with command:\n" + command + "\n");

            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.redirectErrorStream(true);
            ffmpegProcess = processBuilder.start();

            streaming = true;
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        statusArea.append(line + "\n");
                        statusArea.setCaretPosition(statusArea.getDocument().getLength());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        } catch (Exception e) {
            showPopup("Failed to start streaming: " + e.getMessage());
        }
    }

    private void stopStreaming() {
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            streaming = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusArea.append("Streaming stopped.\n");
        }
    }

    private void showPopup(String message) {
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
