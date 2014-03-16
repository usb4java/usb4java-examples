/*
 * Copyright (C) 2014 Klaus Reimer <k@ailis.de>
 * See LICENSE.txt for licensing information.
 */

package org.usb4java.examples;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;
import org.usb4java.Transfer;
import org.usb4java.TransferCallback;

/**
 * Demonstrates how to do asynchronous bulk transfers. This demo sends some
 * hardcoded data to an Android Device (Samsung Galaxy Nexus) and receives some
 * data from it.
 * 
 * If you have a different Android device then you can get this demo working by
 * changing the vendor/product id, the interface number and the endpoint
 * addresses.
 * 
 * In this example the event handling is done with a thread which calls the
 * {@link LibUsb#handleEventsTimeout(org.usb4java.Context, long)} method in a
 * loop. You could also run this command inside the main application loop if
 * there is one.
 * 
 * @author Klaus Reimer <k@ailis.de>
 */
public class AsyncBulkTransfer
{
    /**
     * This is the event handling thread. libusb doesn't start threads by its
     * own so it is our own responsibility to give libusb time to handle the
     * events in our own thread.
     */
    static class EventHandlingThread extends Thread
    {
        /** If thread should abort. */
        private volatile boolean abort;

        /**
         * Aborts the event handling thread.
         */
        public void abort()
        {
            this.abort = true;
        }

        @Override
        public void run()
        {
            while (!this.abort)
            {
                // Let libusb handle pending events. This blocks until events
                // have been handled, a hotplug callback has been deregistered
                // or the specified time of 0.5 seconds (Specified in
                // Microseconds) has passed.
                int result = LibUsb.handleEventsTimeout(null, 500000);
                if (result != LibUsb.SUCCESS)
                    throw new LibUsbException("Unable to handle events", result);
            }
        }
    }

    /** Bytes for a CONNECT ADB message header. */
    static final byte[] CONNECT_HEADER = new byte[] { 0x43, 0x4E, 0x58,
        0x4E, 0x00, 0x00, 0x00, 0x01, 0x00, 0x10, 0x00, 0x00, 0x17, 0x00, 0x00,
        0x00, 0x42, 0x06, 0x00, 0x00, (byte) 0xBC, (byte) 0xB1, (byte) 0xA7,
        (byte) 0xB1 };

    /** Bytes for a CONNECT ADB message body. */
    static final byte[] CONNECT_BODY = new byte[] { 0x68, 0x6F, 0x73,
        0x74, 0x3A, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x3A, 0x41,
        0x44, 0x42, 0x20, 0x44, 0x65, 0x6D, 0x6F, 0x00 };

    /** The vendor ID of the Samsung Galaxy Nexus. */
    private static final short VENDOR_ID = 0x04e8;

    /** The vendor ID of the Samsung Galaxy Nexus. */
    private static final short PRODUCT_ID = 0x6860;

    /** The ADB interface number of the Samsung Galaxy Nexus. */
    private static final byte INTERFACE = 1;

    /** The ADB input endpoint of the Samsung Galaxy Nexus. */
    private static final byte IN_ENDPOINT = (byte) 0x83;

    /** The ADB output endpoint of the Samsung Galaxy Nexus. */
    private static final byte OUT_ENDPOINT = 0x03;

    /** The communication timeout in milliseconds. */
    private static final int TIMEOUT = 5000;

    /**
     * Flag set during the asynchronous transfers to indicate the program is
     * finished.
     */
    static volatile boolean exit = false;

    /**
     * Asynchronously writes some data to the device.
     * 
     * @param handle
     *            The device handle.
     * @param data
     *            The data to send to the device.
     * @param callback
     *            The callback to execute when data has been transfered.
     */
    public static void write(DeviceHandle handle, byte[] data,
        TransferCallback callback)
    {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
        buffer.put(data);
        Transfer transfer = LibUsb.allocTransfer();
        LibUsb.fillBulkTransfer(transfer, handle, OUT_ENDPOINT, buffer,
            callback, null, TIMEOUT);
        System.out.println("Sending " + data.length + " bytes sent to device");
        int result = LibUsb.submitTransfer(transfer);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to submit transfer", result);
        }
    }

    /**
     * Asynchronously reads some data from the device.
     * 
     * @param handle
     *            The device handle.
     * @param size
     *            The number of bytes to read from the device.
     * @param callback
     *            The callback to execute when data has been received.
     */
    public static void read(DeviceHandle handle, int size,
        TransferCallback callback)
    {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(
            ByteOrder.LITTLE_ENDIAN);
        Transfer transfer = LibUsb.allocTransfer();
        LibUsb.fillBulkTransfer(transfer, handle, IN_ENDPOINT, buffer,
            callback, null, TIMEOUT);
        System.out.println("Reading " + size + " bytes from device");
        int result = LibUsb.submitTransfer(transfer);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to submit transfer", result);
        }
    }

    /**
     * Main method.
     * 
     * @param args
     *            Command-line arguments (Ignored)
     * @throws Exception
     *             When something goes wrong.
     */
    public static void main(String[] args) throws Exception
    {
        // Initialize the libusb context
        int result = LibUsb.init(null);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to initialize libusb", result);
        }

        // Open test device
        final DeviceHandle handle = LibUsb.openDeviceWithVidPid(null,
            VENDOR_ID, PRODUCT_ID);
        if (handle == null)
        {
            System.err.println("Test device not found.");
            System.exit(1);
        }

        // Start event handling thread
        EventHandlingThread thread = new EventHandlingThread();
        thread.start();

        // Claim the ADB interface
        result = LibUsb.claimInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to claim interface", result);
        }

        // This callback is called after the ADB answer body has been
        // received. The asynchronous transfer chain ends here.
        final TransferCallback bodyReceived = new TransferCallback()
        {
            @Override
            public void processTransfer(Transfer transfer)
            {
                System.out.println(transfer.actualLength() + " bytes received");
                LibUsb.freeTransfer(transfer);
                System.out.println("Asynchronous communication finished");
                exit = true;
            }
        };

        // This callback is called after the ADB answer header has been
        // received and reads the ADB answer body
        final TransferCallback headerReceived = new TransferCallback()
        {
            @Override
            public void processTransfer(Transfer transfer)
            {
                System.out.println(transfer.actualLength() + " bytes received");
                ByteBuffer header = transfer.buffer();
                header.position(12);
                int dataSize = header.asIntBuffer().get();
                read(handle, dataSize, bodyReceived);
                LibUsb.freeTransfer(transfer);
            }
        };

        // This callback is called after the ADB CONNECT message body is sent
        // and starts reads the ADB answer header.
        final TransferCallback bodySent = new TransferCallback()
        {
            @Override
            public void processTransfer(Transfer transfer)
            {
                System.out.println(transfer.actualLength() + " bytes received");
                read(handle, 24, headerReceived);
                // write(handle, CONNECT_BODY, receiveHeader);
                LibUsb.freeTransfer(transfer);
            }
        };

        // This callback is called after the ADB CONNECT message header is
        // sent and sends the ADB CONNECT message body.
        final TransferCallback headerSent = new TransferCallback()
        {
            @Override
            public void processTransfer(Transfer transfer)
            {
                System.out.println(transfer.actualLength() + " bytes sent");
                write(handle, CONNECT_BODY, bodySent);
                LibUsb.freeTransfer(transfer);
            }
        };

        // Send ADB CONNECT message header asynchronously. The rest of the
        // communication is handled by the callbacks defined above.
        write(handle, CONNECT_HEADER, headerSent);

        // Fake application loop
        while (!exit)
        {
            Thread.yield();
        }

        // Release the ADB interface
        result = LibUsb.releaseInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to release interface", result);
        }

        // Close the device
        LibUsb.close(handle);

        // Stop event handling thread
        thread.abort();
        thread.join();

        // Deinitialize the libusb context
        LibUsb.exit(null);

        System.out.println("Program finished");
    }
}
