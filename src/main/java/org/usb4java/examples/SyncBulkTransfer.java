/*
 * Copyright (C) 2014 Klaus Reimer <k@ailis.de>
 * See LICENSE.txt for licensing information.
 */

package org.usb4java.examples;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

/**
 * Demonstrates how to do synchronous bulk transfers. This demo sends some
 * hardcoded data to an Android Device (Samsung Galaxy Nexus) and receives some
 * data from it.
 * 
 * If you have a different Android device then you can get this demo working by
 * changing the vendor/product id, the interface number and the endpoint
 * addresses.
 * 
 * @author Klaus Reimer <k@ailis.de>
 */
public class SyncBulkTransfer
{
    /** Bytes for a CONNECT ADB message header. */
    private static final byte[] CONNECT_HEADER = new byte[] { 0x43, 0x4E, 0x58,
        0x4E, 0x00, 0x00, 0x00, 0x01, 0x00, 0x10, 0x00, 0x00, 0x17, 0x00, 0x00,
        0x00, 0x42, 0x06, 0x00, 0x00, (byte) 0xBC, (byte) 0xB1, (byte) 0xA7,
        (byte) 0xB1 };

    /** Bytes for a CONNECT ADB message body. */
    private static final byte[] CONNECT_BODY = new byte[] { 0x68, 0x6F, 0x73,
        0x74, 0x3A, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x3A, 0x41,
        0x44, 0x42, 0x20, 0x44, 0x65, 0x6D, 0x6F, 0x00 };

    /** The vendor ID of the Samsung Galaxy Nexus. */
    private static final short VENDOR_ID = 0x04e8;

    /** The product ID of the Samsung Galaxy Nexus. */
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
     * Writes some data to the device.
     * 
     * @param handle
     *            The device handle.
     * @param data
     *            The data to send to the device.
     */
    public static void write(DeviceHandle handle, byte[] data)
    {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(data.length);
        buffer.put(data);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, OUT_ENDPOINT, buffer,
            transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to send data", result);
        }
        System.out.println(transferred.get() + " bytes sent to device");
    }

    /**
     * Reads some data from the device.
     * 
     * @param handle
     *            The device handle.
     * @param size
     *            The number of bytes to read from the device.
     * @return The read data.
     */
    public static ByteBuffer read(DeviceHandle handle, int size)
    {
        ByteBuffer buffer = BufferUtils.allocateByteBuffer(size).order(
            ByteOrder.LITTLE_ENDIAN);
        IntBuffer transferred = BufferUtils.allocateIntBuffer();
        int result = LibUsb.bulkTransfer(handle, IN_ENDPOINT, buffer,
            transferred, TIMEOUT);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to read data", result);
        }
        System.out.println(transferred.get() + " bytes read from device");
        return buffer;
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

        // Open test device (Samsung Galaxy Nexus)
        DeviceHandle handle = LibUsb.openDeviceWithVidPid(null, VENDOR_ID,
            PRODUCT_ID);
        if (handle == null)
        {
            System.err.println("Test device not found.");
            System.exit(1);
        }

        // Claim the ADB interface
        result = LibUsb.claimInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to claim interface", result);
        }

        // Send ADB CONNECT message
        write(handle, CONNECT_HEADER);
        write(handle, CONNECT_BODY);

        // Receive the header of the ADB answer (Most likely an AUTH message)
        ByteBuffer header = read(handle, 24);
        header.position(12);
        int dataSize = header.asIntBuffer().get();

        // Receive the body of the ADB answer
        @SuppressWarnings("unused")
        ByteBuffer data = read(handle, dataSize);

        // Release the ADB interface
        result = LibUsb.releaseInterface(handle, INTERFACE);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to release interface", result);
        }

        // Close the device
        LibUsb.close(handle);

        // Deinitialize the libusb context
        LibUsb.exit(null);
    }
}
