/*
 * Copyright (C) 2013 Klaus Reimer <k@ailis.de>
 * See LICENSE.txt for licensing information.
 */

package de.ailis.usb4java.examples.libusb;

import de.ailis.usb4java.libusb.ConfigDescriptor;
import de.ailis.usb4java.libusb.Context;
import de.ailis.usb4java.libusb.Device;
import de.ailis.usb4java.libusb.DeviceDescriptor;
import de.ailis.usb4java.libusb.DeviceHandle;
import de.ailis.usb4java.libusb.DeviceList;
import de.ailis.usb4java.libusb.LibUsb;
import de.ailis.usb4java.libusb.LibUsbException;
import de.ailis.usb4java.utils.DescriptorUtils;

/**
 * Dumps the device tree by using the low-level libusb interface.
 * 
 * @author Klaus Reimer <k@ailis.de>
 */
public class DumpDevices
{
    /**
     * Dumps all configuration descriptors of the specified device. Because
     * libusb descriptors are connected to each other (Configuration descriptor
     * references interface descriptors which reference endpoint descriptors)
     * dumping a configuration descriptor also dumps all interface and endpoint
     * descriptors in this configuration.
     * 
     * @param device
     *            The USB device.
     * @param numConfigurations
     *            The number of configurations to dump (Read from the device
     *            descriptor)
     * @throws LibUsbException
     *             When libusb reported an error.
     */
    public static void dumpConfigurationDescriptors(final Device device,
        final int numConfigurations)
        throws LibUsbException
    {
        for (byte i = 0; i < numConfigurations; i += 1)
        {
            final ConfigDescriptor descriptor = new ConfigDescriptor();
            final int result = LibUsb.getConfigDescriptor(device, i, descriptor);
            if (result < 0)
            {
                throw new LibUsbException("Unable to read config descriptor",
                    result);
            }
            try
            {
                System.out.println(descriptor.dump().replaceAll("(?m)^",
                    "  "));
            }
            finally
            {
                // Ensure that the config descriptor is freed
                LibUsb.freeConfigDescriptor(descriptor);
            }
        }
    }

    /**
     * Dumps the specified device to stdout.
     * 
     * @param device
     *            The device to dump.
     * @throws LibUsbException
     *             When libusb reported an error.
     */
    public static void dumpDevice(final Device device) throws LibUsbException
    {
        // Dump device address and bus number
        final int address = LibUsb.getDeviceAddress(device);
        final int busNumber = LibUsb.getBusNumber(device);
        System.out.println(String
            .format("Device %03d/%03d", busNumber, address));

        // Dump port number if available
        final int portNumber = LibUsb.getPortNumber(device);
        if (portNumber != 0)
            System.out.println("Connected to port: " + portNumber);

        // Dump parent device if available
        final Device parent = LibUsb.getParent(device);
        if (parent != null)
        {
            final int parentAddress = LibUsb.getDeviceAddress(parent);
            final int parentBusNumber = LibUsb.getBusNumber(parent);
            System.out.println(String.format("Parent: %03d/%03d",
                parentBusNumber, parentAddress));
        }

        // Dump the device speed
        System.out.println("Speed: "
            + DescriptorUtils.getSpeedName(LibUsb.getDeviceSpeed(device)));

        // Read the device descriptor
        final DeviceDescriptor descriptor = new DeviceDescriptor();
        int result = LibUsb.getDeviceDescriptor(device, descriptor);
        if (result < 0)
            throw new LibUsbException("Unable to read device descriptor",
                result);

        // Try to open the device. This may fail because user has no
        // permission to communicate with the device. This is not
        // important for the dumps, we are just not able to resolve string
        // descriptor numbers to strings in the descriptor dumps.
        DeviceHandle handle = new DeviceHandle();
        result = LibUsb.open(device, handle);
        if (result < 0)
        {
            System.out.println(String.format("Unable to open device: %s. "
                + "Continuing without device handle.",
                LibUsb.strError(result)));
            handle = null;
        }

        // Dump the device descriptor
        System.out.print(descriptor.dump(handle));

        // Dump all configuration descriptors
        dumpConfigurationDescriptors(device, descriptor.bNumConfigurations());

        // Close the device if it was opened
        if (handle != null)
        {
            LibUsb.close(handle);
        }
    }

    /**
     * Main method.
     * 
     * @param args
     *            Command-line arguments (Ignored)
     * @throws LibUsbException
     *             When libusb reported an error which wasn't handled by this
     *             program itself.
     */
    public static void main(final String[] args) throws LibUsbException
    {
        // Create the libusb context
        final Context context = new Context();

        // Initialize the libusb context
        int result = LibUsb.init(context);
        if (result < 0)
            throw new LibUsbException("Unable to initialize libusb", result);

        // Read the USB device list
        final DeviceList list = new DeviceList();
        result = LibUsb.getDeviceList(context, list);
        if (result < 0)
            throw new LibUsbException("Unable to get device list", result);

        try
        {
            // Iterate over all devices and dump them
            for (Device device: list)
            {
                dumpDevice(device);
            }
        }
        finally
        {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }
    }
}
