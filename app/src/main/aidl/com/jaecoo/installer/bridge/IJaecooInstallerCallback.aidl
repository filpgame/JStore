// SPDX-FileCopyrightText: 2026 Aurora Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.jaecoo.installer.bridge;

import com.jaecoo.installer.bridge.OperationStatus;

oneway interface IJaecooInstallerCallback {
    void onStatus(in OperationStatus status);
}
