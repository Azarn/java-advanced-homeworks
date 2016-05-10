package ru.ifmo.ctddev.kichigin.walk;

import java.math.BigInteger;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class RecursiveWalk {
    public static final String HASH_ERROR = "00000000000000000000000000000000";

    public static String toHex(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "X", bi);
    }

    public static String hashFile(MessageDigest md, Path path) {
        byte buf[] = new byte[4096];
        String hash;
        try (InputStream is = Files.newInputStream(path, StandardOpenOption.READ)) {
            int c;
            while ((c = is.read(buf, 0, buf.length)) >= 0) {
                md.update(buf, 0, c);
            }
            hash = toHex(md.digest());
        } catch (IOException e) {
            hash = HASH_ERROR;
        }
        return hash;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Incorrect number of arguments! Usage: RecursiveWalk <file.in> <file.out>");
            return;
        }

        Path reader_path = Paths.get(args[0]);
        Path writer_path = Paths.get(args[1]);
        try (BufferedReader br = Files.newBufferedReader(reader_path, StandardCharsets.UTF_8);
             BufferedWriter bw = Files.newBufferedWriter(writer_path, StandardCharsets.UTF_8)) {

            String s;
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            while ((s = br.readLine()) != null) {
                Files.walkFileTree(Paths.get(s), new SimpleFileVisitor<Path>() {
                    private void writeResult(String hash, Path path) throws IOException {
                        String out = String.format("%s %s", hash, path.toString());
                        bw.write(out);
                        bw.newLine();
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        writeResult(hashFile(md5, file), file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                        writeResult(HASH_ERROR, file);
                        return FileVisitResult.CONTINUE;
                    }
                });
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
