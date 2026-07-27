import java.io.*;
import java.security.MessageDigest;
import java.util.Scanner;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

public class Main {

    static final String VAULT_DIR = "vault";
    static final String HASH_FILE = VAULT_DIR + File.separator + ".pwhash";

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        new File(VAULT_DIR).mkdirs();

        System.out.println("========= Secret File Vault =========");
        System.out.print("Enter Password: ");
        String password = scanner.nextLine();

        if (!checkPassword(password)) {
            System.out.println("Access Denied. Wrong password.");
            return;
        }
        System.out.println("Access Granted");

        SecretKeySpec key = getAESKey(password);
        boolean running = true;

        while (running) {
            System.out.println();
            System.out.println("1. Encrypt File");
            System.out.println("2. Decrypt File");
            System.out.println("3. View Files");
            System.out.println("4. Delete File");
            System.out.println("5. Exit");
            System.out.print("Choose: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    System.out.print("Enter File Path: ");
                    encryptFile(scanner.nextLine().trim(), key);
                    break;
                case "2":
                    listFiles();
                    System.out.print("Enter encrypted file name (e.g. notes.pdf.enc): ");
                    String encName = scanner.nextLine().trim();
                    System.out.print("Enter output path to save decrypted file: ");
                    decryptFile(encName, scanner.nextLine().trim(), key);
                    break;
                case "3":
                    listFiles();
                    break;
                case "4":
                    listFiles();
                    System.out.print("Enter encrypted file name to delete: ");
                    deleteFile(scanner.nextLine().trim());
                    break;
                case "5":
                    System.out.println("Goodbye.");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Choose 1-5.");
            }
        }
        scanner.close();
    }

    static boolean checkPassword(String typed) throws Exception {
        File hashFile = new File(HASH_FILE);
        String typedHash = sha256Hex(typed);

        if (!hashFile.exists()) {
            try (FileWriter w = new FileWriter(hashFile)) {
                w.write(typedHash);
            }
            System.out.println("No password set yet. Master password created.");
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

    static void encryptFile(String sourcePath, SecretKeySpec key) {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.out.println("File not found: " + sourcePath);
            return;
        }

        String encryptedName = sourceFile.getName() + ".enc";
        File outFile = new File(VAULT_DIR + File.separator + encryptedName);

        System.out.println("Encrypting...");
        try (FileInputStream in = new FileInputStream(sourceFile);
             FileOutputStream fileOut = new FileOutputStream(outFile)) {

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            try (CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    cipherOut.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("File Encrypted Successfully.");
            System.out.println("Stored in vault folder as: " + encryptedName);

        } catch (Exception e) {
            System.out.println("Encryption failed: " + e.getMessage());
            outFile.delete();
        }
    }

    static void decryptFile(String encryptedFileName, String outputPath, SecretKeySpec key) {
        File encryptedFile = new File(VAULT_DIR + File.separator + encryptedFileName);
        if (!encryptedFile.exists()) {
            System.out.println("Encrypted file not found in vault: " + encryptedFileName);
            return;
        }

        File outFile = new File(outputPath);
        System.out.println("Decrypting...");
        try (FileInputStream fileIn = new FileInputStream(encryptedFile);
             FileOutputStream out = new FileOutputStream(outFile)) {

            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);

            try (CipherInputStream cipherIn = new CipherInputStream(fileIn, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = cipherIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("File Decrypted Successfully.");
            System.out.println("Saved to: " + outputPath);

        } catch (Exception e) {
            System.out.println("Decryption failed. Wrong password or corrupted file.");
            outFile.delete();
        }
    }

    static void listFiles() {
        File dir = new File(VAULT_DIR);
        String[] files = dir.list((d, name) -> name.endsWith(".enc"));
        if (files == null || files.length == 0) {
            System.out.println("Vault is empty.");
            return;
        }
        System.out.println("Files in vault:");
        for (int i = 0; i < files.length; i++) {
            System.out.println("  " + (i + 1) + ". " + files[i]);
        }
    }

    static void deleteFile(String encryptedFileName) {
        File f = new File(VAULT_DIR + File.separator + encryptedFileName);
        if (!f.exists()) {
            System.out.println("File not found in vault: " + encryptedFileName);
            return;
        }
        if (f.delete()) {
            System.out.println("File deleted from vault.");
        } else {
            System.out.println("Could not delete file (it may be in use).");
        }
    }
}
