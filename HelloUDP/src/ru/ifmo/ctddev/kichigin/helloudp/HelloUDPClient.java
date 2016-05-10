package ru.ifmo.ctddev.kichigin.helloudp;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Simple UDP client which sends parallel requests to a UDP server
 *
 * @see ru.ifmo.ctddev.kichigin.helloudp.HelloUDPServer
 * @author Created by azarn on 5/10/16.
 */

public class HelloUDPClient implements HelloClient {
    private final static String ANSWER_PREFIX = "Hello, ";
    private final static int SOCKET_TIMEOUT = 250;

    /**
     * Starts HelloUDP client
     *
     * @param address host address
     * @param port host port number
     * @param prefix prefix of each request
     * @param requestsPerThread number of request per thread
     * @param threadsNumber number of threads
     */
    public void start(String address, int port, String prefix,
                      int requestsPerThread, int threadsNumber) {
        InetAddress ia;
        try {
            ia = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            System.err.println("Incorrect server address");
            return;
        }

        InetSocketAddress isa = new InetSocketAddress(ia, port);

        Thread[] threads = new Thread[threadsNumber];
        for (int curThread = 0; curThread < threadsNumber; ++curThread) {
            threads[curThread] = new Thread(() -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(SOCKET_TIMEOUT);

                    for (int curRequest = 0; curRequest < requestsPerThread; ++curRequest) {
                        String requestOut = prefix + Thread.currentThread().getName() + "_" + Integer.toString(curRequest);
                        byte[] dataOut = requestOut.getBytes(StandardCharsets.UTF_8);
                        byte[] dataIn = new byte[socket.getReceiveBufferSize()];
                        DatagramPacket packetOut = new DatagramPacket(dataOut, dataOut.length, isa);
                        DatagramPacket packetIn = new DatagramPacket(dataIn, dataIn.length);

                        while (true) {
                            socket.send(packetOut);
                            try {
                                socket.receive(packetIn);

                                String requestIn = new String(packetIn.getData(), packetIn.getOffset(),
                                                              packetIn.getLength(), StandardCharsets.UTF_8);

                                System.out.println(requestOut);
                                System.out.println(requestIn);

                                if (requestIn.equals(ANSWER_PREFIX + requestOut)) {
                                    break;
                                }
                            } catch (SocketTimeoutException ignored) {

                            }
                        }

                    }
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            });
            threads[curThread].setName(Integer.toString(curThread));
            threads[curThread].start();
        }

        try {
            for (Thread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException ignored) {

        }
    }

    /**
     * Function for running HelloUDP client from console
     *
     * @param args name_or_ip port prefix threadsNum requestsPerThread
     */
    public static void main(String[] args) {
        if (args == null || args.length != 5) {
            System.out.println("Invalid number of arguments");
            System.out.println("Usage: HelloUDPClient <name_or_ip> <port> <prefix> <threadsNum> <requestsPerThread>");
            return;
        }

        int portNum, threadsNumber, requestsPerThread;
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

        try {
            requestsPerThread = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Incorrect requestPerThread");
            return;
        }

        new HelloUDPClient().start(args[0], portNum, args[2], threadsNumber, requestsPerThread);
    }
}
