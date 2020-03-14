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
package org.openhab.binding.shelly.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.shelly.internal.coap.ShellyCoapServer;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ShellyProtectedHandler} implements a dummy handler for password protected devices.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class ShellyProtectedHandler extends ShellyBaseHandler {
    private final Logger logger = LoggerFactory.getLogger(ShellyProtectedHandler.class);

    /**
     * Constructor
     *
     * @param thing The thing passed by the HandlerFactory
     * @param bindingConfig configuration of the binding
     * @param coapServer coap server instance
     * @param localIP local IP of the openHAB host
     * @param httpPort port of the openHAB HTTP API
     */
    public ShellyProtectedHandler(Thing thing, ShellyBindingConfiguration bindingConfig,
            @Nullable ShellyCoapServer coapServer, String localIP, int httpPort) {
        super(thing, bindingConfig, coapServer, localIP, httpPort);
    }

    @Override
    public void initialize() {
        logger.debug("Thing is using  {}", this.getClass());
        super.initialize();
    }
}
