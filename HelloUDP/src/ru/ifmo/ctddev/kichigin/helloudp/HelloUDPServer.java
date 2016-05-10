package ru.ifmo.ctddev.kichigin.helloudp;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOError;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple UDP server which can recieve parallel requests from UDP client
 *
 * @see ru.ifmo.ctddev.kichigin.helloudp.HelloUDPClient
 * @author Created by azarn on 5/10/16.
 */
public class HelloUDPServer implements HelloServer {
    private final static String ANSWER_PREFIX = "Hello, ";
    private final static int SOCKET_TIMEOUT = 250;
    private ArrayList<Thread> threadList;
    private DatagramSocket socketIn;

    /**
     * Starts HelloUDP server
     *
     * @param port port number to listen
     * @param threadsNumber number of threads to operate with requests
     */
    @Override
    public void start(int port, int threadsNumber) {
        try {
            socketIn = new DatagramSocket(port);
            threadList = new ArrayList<>();
            socketIn.setSoTimeout(SOCKET_TIMEOUT);
            for (int i = 0; i < threadsNumber; ++i) {
                threadList.add(new Thread(() -> {
                    try (DatagramSocket socketOut = new DatagramSocket()) {
                        byte[] dataIn = new byte[socketIn.getReceiveBufferSize()];
                        DatagramPacket packetIn = new DatagramPacket(dataIn, dataIn.length);

                        while (!Thread.interrupted()) {
                            try {
                                socketIn.receive(packetIn);
                            } catch (SocketTimeoutException ignored) {
                                continue;
                            }

                            String requestIn = ANSWER_PREFIX + new String(packetIn.getData(), packetIn.getOffset(),
                                                                          packetIn.getLength(), StandardCharsets.UTF_8);
                            byte[] dataOut = requestIn.getBytes(StandardCharsets.UTF_8);
                            socketOut.send(new DatagramPacket(dataOut, dataOut.length, packetIn.getSocketAddress()));
                        }
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                    }
                }));
            }
            threadList.forEach(Thread::start);
        } catch (SocketException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void close() {
        threadList.forEach(Thread::interrupt);

        try {
            for (Thread t: threadList) {
                t.join();
            }
        } catch (InterruptedException ignored) {

        }
        socketIn.close();
    }

    /**
     * Function for running HelloUDP server from console
     *
     * @param args portNum threadsNum
     */
    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.out.println("Invalid number of arguments");
            System.out.println("Usage: HelloUDPServer <portNum> <threadsNum>");
            return;
        }

        int portNum, threadsNumber;

        try {
            portNum = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Incorrect port number");
            return;
        }

        try {
            threadsNumber = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.err.println("Incorrect threadNumber");
            return;
        }

        new HelloUDPServer().start(portNum, threadsNumber);
    }
}
