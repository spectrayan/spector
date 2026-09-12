/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;

/**
 * Configuration properties for namespace storage layout and routing.
 */
public class NamespaceProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private TenantRootedProperties tenantRooted = new TenantRootedProperties();
    private boolean dualReadEnabled = DEFAULT_NAMESPACE_DUAL_READ_ENABLED;

    public NamespaceProperties() {}

    public NamespaceProperties(TenantRootedProperties tenantRooted) {
        if (tenantRooted != null) {
            this.tenantRooted = tenantRooted;
        }
    }

    public TenantRootedProperties getTenantRooted() {
        return tenantRooted;
    }

    public void setTenantRooted(TenantRootedProperties tenantRooted) {
        if (tenantRooted != null) {
            this.tenantRooted = tenantRooted;
        }
    }

    public boolean isTenantRootedEnabled() {
        return tenantRooted != null && tenantRooted.isEnabled();
    }

    public boolean isDualReadEnabled() {
        return dualReadEnabled;
    }

    public void setDualReadEnabled(boolean dualReadEnabled) {
        this.dualReadEnabled = dualReadEnabled;
    }

    public NamespaceProperties copy() {
        NamespaceProperties cp = new NamespaceProperties();
        if (this.tenantRooted != null) {
            cp.setTenantRooted(this.tenantRooted.copy());
        }
        cp.setDualReadEnabled(this.dualReadEnabled);
        return cp;
    }

    public static class TenantRootedProperties implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = DEFAULT_NAMESPACE_TENANT_ROOTED_ENABLED;

        public TenantRootedProperties() {}

        public TenantRootedProperties(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public TenantRootedProperties copy() {
            return new TenantRootedProperties(this.enabled);
        }
    }
}
