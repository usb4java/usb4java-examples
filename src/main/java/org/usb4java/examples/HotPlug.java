/*
 * Copyright (C) 2014 Klaus Reimer <k@ailis.de>
 * See LICENSE.txt for licensing information.
 */

package org.usb4java.examples;

import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.HotplugCallback;
import org.usb4java.HotplugCallbackHandle;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

/**
 * Demonstrates how to use the hotplug feature of libusb.
 * 
 * @author Klaus Reimer <k@ailis.de>
 */
public class HotPlug
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
                // or the specified time of 1 second (Specified in
                // Microseconds) has passed.
                int result = LibUsb.handleEventsTimeout(null, 1000000);
                if (result != LibUsb.SUCCESS)
                    throw new LibUsbException("Unable to handle events", result);
            }
        }
    }

    /**
     * The hotplug callback handler
     */
    static class Callback implements HotplugCallback
    {
        @Override
        public int processEvent(Context context, Device device, int event,
            Object userData)
        {
            DeviceDescriptor descriptor = new DeviceDescriptor();
            int result = LibUsb.getDeviceDescriptor(device, descriptor);
            if (result != LibUsb.SUCCESS)
                throw new LibUsbException("Unable to read device descriptor",
                    result);
            System.out.format("%s: %04x:%04x%n",
                event == LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED ? "Connected" :
                    "Disconnected",
                descriptor.idVendor(), descriptor.idProduct());
            return 0;
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

        // Check if hotplug is available
        if (!LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG))
        {
            System.err.println("libusb doesn't support hotplug on this system");
            System.exit(1);
        }

        // Start the event handling thread
        EventHandlingThread thread = new EventHandlingThread();
        thread.start();

        // Register the hotplug callback
        HotplugCallbackHandle callbackHandle = new HotplugCallbackHandle();
        result = LibUsb.hotplugRegisterCallback(null,
            LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED
                | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
            LibUsb.HOTPLUG_ENUMERATE,
            LibUsb.HOTPLUG_MATCH_ANY,
            LibUsb.HOTPLUG_MATCH_ANY,
            LibUsb.HOTPLUG_MATCH_ANY,
            new Callback(), null, callbackHandle);
        if (result != LibUsb.SUCCESS)
        {
            throw new LibUsbException("Unable to register hotplug callback",
                result);
        }

        // Our faked application. Hit enter key to exit the application.
        System.out.println("Hit enter to exit the demo");
        System.in.read();

        // Unregister the hotplug callback and stop the event handling thread
        thread.abort();
        LibUsb.hotplugDeregisterCallback(null, callbackHandle);
        thread.join();

        // Deinitialize the libusb context
        LibUsb.exit(null);
    }
}
