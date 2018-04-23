package com.kenrui.packetbroker.utilities;

import org.pcap4j.util.ByteArrays;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Utilities class for writing to and reading from file.
 * This is useful for testing by not sending and receiving over network connection.
 */
public class UtilsFileHandler {
    /**
     * Reads packet from file
     * @param filepath
     * @return
     */
    public static ByteBuffer FileReader(String filepath) {
        Path path = Paths.get(filepath);

        AsynchronousFileChannel fileChannel = null;
        try {
            fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Long fileSize = null;
        try {
            fileSize = fileChannel.size();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (fileSize > Integer.MAX_VALUE) {
            try {
                throw new Exception(filepath + " is larger than supported size of " + Integer.MAX_VALUE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize.intValue());
        long position = 0;
        Future<Integer> operation = fileChannel.read(byteBuffer, position);

        while (!operation.isDone()) ;

        byteBuffer.flip();
        try {
            fileChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteBuffer;
    }

    /**
     * Writes packet to file
     * @param filepath
     * @param byteBuffer
     */
    public static void FileWriter(String filepath, ByteBuffer byteBuffer) {
        Path path = Paths.get(filepath);
        AsynchronousFileChannel fileChannel = null;

        Set options = new HashSet();
        options.add(StandardOpenOption.CREATE);
        options.add(StandardOpenOption.WRITE);
        options.add(StandardOpenOption.READ);

        try {
            fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Future<Integer> operation = null;

        try {
            fileChannel.truncate(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        operation = fileChannel.write(byteBuffer, 0);


        while (!operation.isDone()) ;

        try {
            fileChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Prints content of packet written to file
     * @param filepath
     */
    public static void PrintFileToConsole(String filepath) {
        ByteBuffer byteBuffer = FileReader(filepath);
        byte[] byteArray = new byte[byteBuffer.limit()];
        byteBuffer.get(byteArray, 0, byteBuffer.limit());
        System.out.println(ByteArrays.toHexString(byteArray, " "));
    }
}
