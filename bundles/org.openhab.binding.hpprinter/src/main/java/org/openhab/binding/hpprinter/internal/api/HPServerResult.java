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
package org.openhab.binding.hpprinter.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link HPServerResult} is responsible for returning the
 * reading of data from the HP Embedded Web Server.
 *
 * @author Stewart Cossey - Initial contribution
 */
@NonNullByDefault
public class HPServerResult<result> {
    private final RequestStatus status;
    private final @Nullable result data;
    private final String errorMessage;

    public HPServerResult(RequestStatus status, String errorMessage) {
        this.status = status;
        this.data = null;
        this.errorMessage = errorMessage;
    }

    public HPServerResult(result data) {
        this.status = RequestStatus.SUCCESS;
        this.data = data;
        this.errorMessage = "";
    }

    public result getData() {
        if (status != RequestStatus.SUCCESS || data == null) {
            throw new IllegalStateException("No data available for result");
        }
        return data;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public enum RequestStatus {
        SUCCESS,
        TIMEOUT,
        ERROR
    }
}
