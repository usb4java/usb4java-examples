/*
 * Copyright (C) 2014 Klaus Reimer <k@ailis.de>
 * See LICENSE.txt for licensing information.
 */

package org.usb4java.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

/**
 * Controls a USB missile launcher (Only compatible with Vendor/Product
 * 1130:0202).
 * 
 * @author Klaus Reimer <k@ailis.de>
 */
public class MissileLauncher
{
    /** The vendor ID of the missile launcher. */
    private static final short VENDOR_ID = 0x1130;

    /** The product ID of the missile launcher. */
    private static final short PRODUCT_ID = 0x0202;

    /** The USB communication timeout. */
    private static final int TIMEOUT = 1000;

    /** First init packet to send to the missile launcher. */
    private static final byte[] INIT_A = new byte[] { 85, 83, 66, 67, 0, 0, 4,
        0 };

    /** Second init packet to send to the missile launcher. */
    private static final byte[] INIT_B = new byte[] { 85, 83, 66, 67, 0, 64, 2,
        0 };

    /** Command to rotate the launcher up. */
    private static final int CMD_UP = 0x01;

    /** Command to rotate the launcher down. */
    private static final int CMD_DOWN = 0x02;

    /** Command to rotate the launcher to the left. */
    private static final int CMD_LEFT = 0x04;

    /** Command to rotate the launcher to the right. */
    private static final int CMD_RIGHT = 0x08;

    /** Command to fire a missile. */
    private static final int CMD_FIRE = 0x10;

    /**
     * Searches for the missile launcher device and returns it. If there are
     * multiple missile launchers attached then this simple demo only returns
     * the first one.
     * 
     * @return The missile launcher USB device or null if not found.
     */
    public static Device findMissileLauncher()
    {
        // Read the USB device list
        DeviceList list = new DeviceList();
        int result = LibUsb.getDeviceList(null, list);
        if (result < 0)
        {
            throw new RuntimeException(
                "Unable to get device list. Result=" + result);
        }

        try
        {
            // Iterate over all devices and scan for the missile launcher
            for (Device device: list)
            {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result < 0)
                {
                    throw new RuntimeException(
                        "Unable to read device descriptor. Result=" + result);
                }
                if (descriptor.idVendor() == VENDOR_ID
                    && descriptor.idProduct() == PRODUCT_ID) return device;
            }
        }
        finally
        {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }

        // No missile launcher found
        return null;
    }

    /**
     * Sends a message to the missile launcher.
     * 
     * @param handle
     *            The USB device handle.
     * @param message
     *            The message to send.
     */
    public static void sendMessage(DeviceHandle handle, byte[] message)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(message.length);
        buffer.put(message);
        buffer.rewind();
        int transfered = LibUsb.controlTransfer(handle,
            (byte) (LibUsb.REQUEST_TYPE_CLASS | LibUsb.RECIPIENT_INTERFACE),
            (byte) 0x09, (short) 2, (short) 1, buffer, TIMEOUT);
        if (transfered < 0)
            throw new LibUsbException("Control transfer failed", transfered);
        if (transfered != message.length)
            throw new RuntimeException("Not all data was sent to device");
    }

    /**
     * Sends a command to the missile launcher.
     * 
     * @param handle
     *            The USB device handle.
     * @param command
     *            The command to send.
     */
    public static void sendCommand(DeviceHandle handle, int command)
    {
        byte[] message = new byte[64];
        message[1] = (byte) ((command & CMD_LEFT) > 0 ? 1 : 0);
        message[2] = (byte) ((command & CMD_RIGHT) > 0 ? 1 : 0);
        message[3] = (byte) ((command & CMD_UP) > 0 ? 1 : 0);
        message[4] = (byte) ((command & CMD_DOWN) > 0 ? 1 : 0);
        message[5] = (byte) ((command & CMD_FIRE) > 0 ? 1 : 0);
        message[6] = 8;
        message[7] = 8;
        sendMessage(handle, INIT_A);
        sendMessage(handle, INIT_B);
        sendMessage(handle, message);
    }

    /**
     * Read a key from stdin and returns it.
     * 
     * @return The read key.
     */
    public static char readKey()
    {
        try
        {
            String line =
                new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (line.length() > 0) return line.charAt(0);
            return 0;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Unable to read key", e);
        }
    }

    /**
     * Main method.
     * 
     * @param args
     *            Command-line arguments (Ignored)
     */
    public static void main(String[] args)
    {
        // Initialize the libusb context
        int result = LibUsb.init(null);
        if (result < 0)
            throw new LibUsbException("Unable to initialize libusb", result);

        // Search for the missile launcher USB device and stop when not found
        Device device = findMissileLauncher();
        if (device == null)
        {
            System.err.println("Missile launcher not found.");
            System.exit(1);
        }

        // Open the device
        DeviceHandle handle = new DeviceHandle();
        result = LibUsb.open(device, handle);
        if (result != 0)
            throw new LibUsbException("Unable to open USB device", result);
        try
        {
            // Detach kernel driver from interface 0 and 1. This can fail if
            // kernel is not attached to the device or operating system
            // doesn't support this operation. These cases are ignored here.
            result = LibUsb.detachKernelDriver(handle, 1);
            if (result != LibUsb.SUCCESS &&
                result != LibUsb.ERROR_NOT_SUPPORTED &&
                result != LibUsb.ERROR_NOT_FOUND)
            {
                throw new LibUsbException("Unable to detach kernel driver",
                    result);
            }

            // Claim interface
            result = LibUsb.claimInterface(handle, 1);
            if (result != LibUsb.SUCCESS)
                throw new LibUsbException("Unable to claim interface", result);

            // Read commands and execute them
            System.out.println("WADX = Move, S = Stop, F = Fire, Q = Exit");
            boolean exit = false;
            while (!exit)
            {
                System.out.print("> ");
                char key = readKey();
                switch (key)
                {
                    case 'w':
                        sendCommand(handle, CMD_UP);
                        break;

                    case 'x':
                        sendCommand(handle, CMD_DOWN);
                        break;

                    case 'a':
                        sendCommand(handle, CMD_LEFT);
                        break;

                    case 'd':
                        sendCommand(handle, CMD_RIGHT);
                        break;

                    case 'f':
                        sendCommand(handle, CMD_FIRE);
                        break;

                    case 's':
                        sendCommand(handle, 0);
                        break;

                    case 'q':
                        exit = true;
                        break;

                    default:
                }
            }
            System.out.println("Exiting");
        }
        finally
        {
            LibUsb.close(handle);
        }

        // Deinitialize the libusb context
        LibUsb.exit(null);
    }
}
