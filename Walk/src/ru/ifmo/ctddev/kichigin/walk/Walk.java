package ru.ifmo.ctddev.kichigin.walk;

import java.math.BigInteger;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class Walk {
    public static final String HASH_ERROR = "00000000000000000000000000000000";

    public static String toHex(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "X", bi);
    }

    public static String hashFile(Path path) throws NoSuchAlgorithmException {
        byte buf[] = new byte[4096];
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        String hash;
        try (InputStream is = Files.newInputStream(path, StandardOpenOption.READ)) {
            int c;
            while ((c = is.read(buf, 0, buf.length)) >= 0) {
                md5.update(buf, 0, c);
            }
            hash = toHex(md5.digest());
        } catch (IOException e) {
            hash = HASH_ERROR;
        }
        return hash;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Incorrect number of arguments! Usage: Walk <file.in> <file.out>");
            return;
        }

        Path reader_path = FileSystems.getDefault().getPath(args[0]);
        Path writer_path = FileSystems.getDefault().getPath(args[1]);
        try (BufferedReader br = Files.newBufferedReader(reader_path, StandardCharsets.UTF_8);
             BufferedWriter bw = Files.newBufferedWriter(writer_path, StandardCharsets.UTF_8)) {

            String s;
            while ((s = br.readLine()) != null) {
                String out = String.format("%s %s", hashFile(Paths.get(s)), s);
                bw.write(out);
                bw.newLine();
            }
        } catch (FileNotFoundException e) {
            System.out.println("[ERROR]: File not found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[ERROR]: Error with file: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[ERROR]: MD5 algorithm is not found!");
        }
    }
}
