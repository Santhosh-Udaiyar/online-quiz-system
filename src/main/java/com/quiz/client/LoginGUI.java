package com.quiz.client;

import com.quiz.admin.AdminDashboard;
import com.quiz.common.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;

/**
 * Authentication window for students and administrators connecting to QuizServer.
 */
public class LoginGUI extends JFrame {

    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JComboBox<String> roleCombo;
    private final JButton loginButton;
    private final JLabel statusLabel;

    public LoginGUI() {
        super("Online Quiz System - Authentication");
        UIUtils.applyModernLookAndFeel();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(480, 560);
        setResizable(false);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(UIUtils.BG_LIGHT);

        // Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        headerPanel.setBackground(UIUtils.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel titleLabel = new JLabel("Online Quiz System", SwingConstants.CENTER);
        titleLabel.setFont(UIUtils.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("Secure Raw TCP Socket Examination Platform", SwingConstants.CENTER);
        subtitleLabel.setFont(UIUtils.FONT_SMALL);
        subtitleLabel.setForeground(new Color(224, 231, 255));

        headerPanel.add(titleLabel);
        headerPanel.add(subtitleLabel);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Form Card
        JPanel cardPanel = UIUtils.createCardPanel();
        cardPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Server connection row
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        cardPanel.add(new JLabel("Server Host:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        hostField = new JTextField("localhost", 10);
        cardPanel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        cardPanel.add(new JLabel("Server Port:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        portField = new JTextField("8888", 6);
        cardPanel.add(portField, gbc);

        // Separator
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        cardPanel.add(new JSeparator(), gbc);

        // Role
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        cardPanel.add(new JLabel("Login As:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        roleCombo = new JComboBox<>(new String[]{"STUDENT", "ADMIN"});
        roleCombo.setFont(UIUtils.FONT_BODY);
        cardPanel.add(roleCombo, gbc);

        // Username
        gbc.gridx = 0; gbc.gridy = 4;
        cardPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        usernameField = new JTextField("student1", 15);
        cardPanel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 5;
        cardPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        passwordField = new JPasswordField("password123", 15);
        cardPanel.add(passwordField, gbc);

        // Status Label
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(UIUtils.FONT_SMALL);
        statusLabel.setForeground(UIUtils.DANGER);
        cardPanel.add(statusLabel, gbc);

        // Login Button
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        loginButton = UIUtils.createStyledButton("Log In", UIUtils.PRIMARY, Color.WHITE);
        cardPanel.add(loginButton, gbc);

        // Quick Credentials helper note
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2;
        JLabel hintLabel = new JLabel("<html><center style='color:#64748b; font-size:11px;'>" +
                "Sample Credentials:<br>" +
                "Student: <b>student1</b> / <b>password123</b> &nbsp;|&nbsp; <b>student2</b> / <b>password123</b><br>" +
                "Admin: <b>admin</b> / <b>admin123</b>" +
                "</center></html>", SwingConstants.CENTER);
        cardPanel.add(hintLabel, gbc);

        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
        centerWrapper.setBackground(UIUtils.BG_LIGHT);
        centerWrapper.add(cardPanel);
        mainPanel.add(centerWrapper, BorderLayout.CENTER);

        // Role combo listener to automatically populate sample username/password for smooth testing
        roleCombo.addActionListener(e -> {
            String role = (String) roleCombo.getSelectedItem();
            if ("ADMIN".equalsIgnoreCase(role)) {
                usernameField.setText("admin");
                passwordField.setText("admin123");
            } else {
                usernameField.setText("student1");
                passwordField.setText("password123");
            }
        });

        loginButton.addActionListener(e -> performLogin());
        passwordField.addActionListener(e -> performLogin());

        setContentPane(mainPanel);
    }

    private void performLogin() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String role = (String) roleCombo.getSelectedItem();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            statusLabel.setText("Invalid port number.");
            return;
        }

        loginButton.setEnabled(false);
        statusLabel.setForeground(UIUtils.PRIMARY);
        statusLabel.setText("Connecting to server...");

        // Perform connection on background thread to prevent UI freezing
        new SwingWorker<User, Void>() {
            private QuizClient client;

            @Override
            protected User doInBackground() throws Exception {
                client = new QuizClient();
                client.connect(host, port);
                return client.login(username, password, role);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                try {
                    User user = get();
                    if (user != null) {
                        statusLabel.setForeground(UIUtils.SUCCESS);
                        statusLabel.setText("Authenticated successfully!");
                        dispose(); // Close login window

                        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                            SwingUtilities.invokeLater(() -> new AdminDashboard(client).setVisible(true));
                        } else {
                            SwingUtilities.invokeLater(() -> new StudentDashboard(client).setVisible(true));
                        }
                    }
                } catch (Exception ex) {
                    statusLabel.setForeground(UIUtils.DANGER);
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("Error: " + msg);
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginGUI loginGUI = new LoginGUI();
            loginGUI.setVisible(true);
        });
    }
}
