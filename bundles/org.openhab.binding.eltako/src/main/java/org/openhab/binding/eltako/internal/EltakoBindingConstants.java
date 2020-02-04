/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.eltako.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link EltakoBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Martin Wenske - Initial contribution
 */
@NonNullByDefault
public class EltakoBindingConstants {

    private static final String BINDING_ID = "eltako";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_FUD14 = new ThingTypeUID(BINDING_ID, "FUD14");

    // List of all Channel ids
    public static final String CHANNEL_POWER = "power";
    public static final String CHANNEL_BRIGHTNESS = "brightness";

    // List of all configuration parameters
    public static final String PROPERTY_VENDOR = "vendor";
    public static final String PROPERTY_ID = "id";
    public static final String PROPERTY_PROTOCOL = "protocol";
    public static final String PROPERTY_DEVICE_ID = "DeviceID";
}
