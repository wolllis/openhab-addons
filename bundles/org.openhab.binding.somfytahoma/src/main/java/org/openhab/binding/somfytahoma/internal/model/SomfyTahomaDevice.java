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
package org.openhab.binding.somfytahoma.internal.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link SomfyTahomaDevice} holds information about a device bound
 * to TahomaLink account.
 *
 * @author Ondrej Pecta - Initial contribution
 */
@NonNullByDefault
public class SomfyTahomaDevice {

    private String uiClass = "";
    private String widget = "";
    private String deviceURL = "";
    private String label = "";
    private String oid = "";
    private SomfyTahomaDeviceDefinition definition = new SomfyTahomaDeviceDefinition();
    private List<SomfyTahomaState> states = new ArrayList<>();

    public String getLabel() {
        return label;
    }

    public String getDeviceURL() {
        return deviceURL;
    }

    public String getOid() {
        return oid;
    }

    public String getUiClass() {
        return uiClass;
    }

    public String getWidget() {
        return widget;
    }

    public SomfyTahomaDeviceDefinition getDefinition() {
        return definition;
    }

    public List<SomfyTahomaState> getStates() {
        return states;
    }
}
