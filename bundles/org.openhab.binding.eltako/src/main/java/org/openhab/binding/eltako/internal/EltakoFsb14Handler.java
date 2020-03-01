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

import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Direktes Fahrkommando mit Angabe der Laufzeit in Sek. FUNC = 3F, Typ = 7F (universal). Für jeden Kanal separat.
 *
 * ORG        = 0x07
 * Data_byte3 = Laufzeit in 100ms MSB
 * Data_byte2 = Laufzeit in 100ms LSB, oder Laufzeit in Sekunden 1-255 dez., die Laufzeiteinstellung am Gerät wird ignoriert.
 * Data_byte1 = Kommando:
 *              0x00 = Stopp
 *              0x01 = Auf
 *              0x02 = Ab
 * Data_byte0 = DB0_Bit3 = LRN Button
 *              (0 = Lerntelegramm, 1 = Datentelegramm)
 *              DB0_Bit2 = Aktor für Taster blockieren/freigeben
 *              (0 = freigeben, 1 = blockieren)
 *              DB0_Bit1 = Umschaltung Laufzeit in Sekunden oder in 100ms.
 *              (0 = Laufzeit nur in DB2 in Sekunden)
 *              (1 = Laufzeit in DB3(MSB)+DB2(LSB) in 100ms.)
 *
 * Lerntelegramm DB3..DB0 muss so aussehen: 0xFF, 0xF8, 0x0D, 0x80
 *
 * Mit eingelernten Tastern kann jederzeit unterbrochen werden!
*/
/**
 * The {@link EltakoFsb14Handler} is responsible for processing device specific commands.
 *
 * @author Martin Wenske - Initial contribution
 */
public class EltakoFsb14Handler extends EltakoGenericHandler {

    /*
     * Logger instance to create log entries
     */
    private final Logger logger = LoggerFactory.getLogger(EltakoGenericHandler.class);

    public EltakoFsb14Handler(Thing thing) {
        super(thing);
    }

    /**
     * Event handler is called in case a channel has received a command
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Log event to console
        logger.debug("Channel {} received command {} with class {}", channelUID, command, command.getClass());
    }
}
