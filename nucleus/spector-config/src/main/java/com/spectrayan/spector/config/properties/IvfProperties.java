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

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.SpectorPropertyConstants;

import java.io.Serializable;
import java.util.Objects;

/**
 * IVF/PQ vector index configuration parameters.
 */
public class IvfProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final IvfProperties DEFAULTS = new IvfProperties(
            SpectorPropertyConstants.DEFAULT_IVF_NLIST,
            SpectorPropertyConstants.DEFAULT_IVF_NPROBE,
            SpectorPropertyConstants.DEFAULT_IVF_PQ_SUBSPACES
    );

    private int nlist;
    private int nprobe;
    private int pqSubspaces;

    public IvfProperties() {
        this(DEFAULTS.nlist, DEFAULTS.nprobe, DEFAULTS.pqSubspaces);
    }

    public IvfProperties(int nlist, int nprobe, int pqSubspaces) {
        this.nlist = nlist;
        this.nprobe = nprobe;
        this.pqSubspaces = pqSubspaces;
    }

    public static IvfProperties from(SpectorProperties props) {
        if (props == null) return DEFAULTS;
        return new IvfProperties(
                props.getInt(SpectorPropertyConstants.IVF_NLIST, DEFAULTS.nlist),
                props.getInt(SpectorPropertyConstants.IVF_NPROBE, DEFAULTS.nprobe),
                props.getInt(SpectorPropertyConstants.IVF_PQ_SUBSPACES, DEFAULTS.pqSubspaces)
        );
    }

    public int getNlist() { return nlist; }
    public void setNlist(int nlist) { this.nlist = nlist; }
    public int nlist() { return nlist; }

    public int getNprobe() { return nprobe; }
    public void setNprobe(int nprobe) { this.nprobe = nprobe; }
    public int nprobe() { return nprobe; }

    public int getPqSubspaces() { return pqSubspaces; }
    public void setPqSubspaces(int pqSubspaces) { this.pqSubspaces = pqSubspaces; }
    public int pqSubspaces() { return pqSubspaces; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IvfProperties that = (IvfProperties) o;
        return nlist == that.nlist && nprobe == that.nprobe && pqSubspaces == that.pqSubspaces;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nlist, nprobe, pqSubspaces);
    }

    @Override
    public String toString() {
        return "IvfProperties{nlist=" + nlist + ", nprobe=" + nprobe + ", pqSubspaces=" + pqSubspaces + '}';
    }
}
