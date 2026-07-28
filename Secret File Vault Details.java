import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.security.MessageDigest;

/**
 * Swing GUI version of the Secret File Vault.
 * Same encryption/vault logic as the original console app,
 * just wrapped in a popup window interface.
 */
public class VaultGUI extends JFrame {

    static final String VAULT_DIR = "vault";
    static final String HASH_FILE = VAULT_DIR + File.separator + ".pwhash";

    private SecretKeySpec aesKey;
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);

    public static void main(String[] args) {
        new File(VAULT_DIR).mkdirs();
        SwingUtilities.invokeLater(VaultGUI::promptLogin);
    }

    // ---------- Login popup ----------

    private static void promptLogin() {
        JPasswordField passwordField = new JPasswordField(20);
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Enter Vault Password:"), BorderLayout.NORTH);
        panel.add(passwordField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                null, panel, "Secret File Vault - Login",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            System.exit(0);
        }

        String password = new String(passwordField.getPassword());

        try {
            boolean firstTime = !new File(HASH_FILE).exists();
            if (!checkPassword(password)) {
                JOptionPane.showMessageDialog(null, "Access Denied. Wrong password.",
                        "Login Failed", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
            if (firstTime) {
                JOptionPane.showMessageDialog(null,
                        "No password was set. This password is now your master password.",
                        "Master Password Created", JOptionPane.INFORMATION_MESSAGE);
            }

            SecretKeySpec key = getAESKey(password);
            VaultGUI gui = new VaultGUI(key);
            gui.setVisible(true);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    // ---------- Main window ----------

    private VaultGUI(SecretKeySpec key) {
        this.aesKey = key;

        setTitle("Secret File Vault");
        setSize(520, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JLabel header = new JLabel("Vault Contents", SwingConstants.LEFT);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        header.setBorder(new EmptyBorder(10, 10, 0, 10));
        add(header, BorderLayout.NORTH);

        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 8, 8));
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton encryptBtn = new JButton("Encrypt File");
        JButton decryptBtn = new JButton("Decrypt File");
        JButton refreshBtn = new JButton("Refresh List");
        JButton deleteBtn = new JButton("Delete File");
        JButton exitBtn = new JButton("Exit");

        encryptBtn.addActionListener(e -> encryptFileAction());
        decryptBtn.addActionListener(e -> decryptFileAction());
        refreshBtn.addActionListener(e -> refreshFileList());
        deleteBtn.addActionListener(e -> deleteFileAction());
        exitBtn.addActionListener(e -> System.exit(0));

        buttonPanel.add(encryptBtn);
        buttonPanel.add(decryptBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(exitBtn);

        add(buttonPanel, BorderLayout.EAST);

        refreshFileList();
    }

    // ---------- Button actions ----------

    private void encryptFileAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select File to Encrypt");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File sourceFile = chooser.getSelectedFile();
        String encryptedName = sourceFile.getName() + ".enc";
        File outFile = new File(VAULT_DIR + File.separator + encryptedName);

        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream fileOut = new FileOutputStream(outFile)) {

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey);

            try (CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    cipherOut.write(buffer, 0, bytesRead);
                }
            }

            JOptionPane.showMessageDialog(this,
                    "File encrypted and stored as:\n" + encryptedName,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            refreshFileList();

        } catch (Exception e) {
            outFile.delete();
            JOptionPane.showMessageDialog(this, "Encryption failed: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void decryptFileAction() {
        String selected = fileList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file from the list first.",
                    "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose where to save the decrypted file");
        String suggestedName = selected.endsWith(".enc")
                ? selected.substring(0, selected.length() - 4) : selected;
        chooser.setSelectedFile(new File(suggestedName));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File outFile = chooser.getSelectedFile();
        File encryptedFile = new File(VAULT_DIR + File.separator + selected);

        try (FileInputStream fileIn = new FileInputStream(encryptedFile);
             FileOutputStream out = new FileOutputStream(outFile)) {

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);

            try (CipherInputStream cipherIn = new CipherInputStream(fileIn, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = cipherIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            JOptionPane.showMessageDialog(this,
                    "File decrypted and saved to:\n" + outFile.getAbsolutePath(),
                    "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            outFile.delete();
            JOptionPane.showMessageDialog(this,
                    "Decryption failed. Wrong password or corrupted file.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteFileAction() {
        String selected = fileList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a file from the list first.",
                    "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete \"" + selected + "\" from the vault?\nThis cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        File f = new File(VAULT_DIR + File.separator + selected);
        if (f.delete()) {
            JOptionPane.showMessageDialog(this, "File deleted from vault.",
                    "Deleted", JOptionPane.INFORMATION_MESSAGE);
            refreshFileList();
        } else {
            JOptionPane.showMessageDialog(this, "Could not delete file (it may be in use).",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshFileList() {
        fileListModel.clear();
        File dir = new File(VAULT_DIR);
        String[] files = dir.list((d, name) -> name.endsWith(".enc"));
        if (files != null) {
            for (String f : files) {
                fileListModel.addElement(f);
            }
        }
    }

    // ---------- Shared crypto/auth helpers (same logic as original console app) ----------

    static boolean checkPassword(String typed) throws Exception {
        File hashFile = new File(HASH_FILE);
        String typedHash = sha256Hex(typed);

        if (!hashFile.exists()) {
            try (FileWriter w = new FileWriter(hashFile)) {
                w.write(typedHash);
            }
            return true;
        }

        try (BufferedReader r = new BufferedReader(new FileReader(hashFile))) {
            String storedHash = r.readLine();
            return typedHash.equals(storedHash);
        }
    }

    static SecretKeySpec getAESKey(String password) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] fullHash = sha.digest(password.getBytes());
        byte[] key16 = new byte[16];
        System.arraycopy(fullHash, 0, key16, 0, 16);
        return new SecretKeySpec(key16, "AES");
    }

    static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
