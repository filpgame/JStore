// SPDX-FileCopyrightText: 2026 Aurora Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.jaecoo.installer.bridge;

import com.jaecoo.installer.bridge.InstallRequest;
import com.jaecoo.installer.bridge.OperationStatus;
import com.jaecoo.installer.bridge.IJaecooInstallerCallback;
import com.jaecoo.installer.bridge.InstallerCapabilities;

interface IJaecooInstallerBridge {
    InstallerCapabilities getCapabilities();
    String submit(in InstallRequest request, IJaecooInstallerCallback callback);
    OperationStatus getStatus(String operationId);
    void observe(String operationId, IJaecooInstallerCallback callback);
    void cancel(String operationId);
}
