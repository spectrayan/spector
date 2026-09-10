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
package com.spectrayan.spector.kernel.api;

import java.nio.file.Path;

/**
 * Factory and lifecycle manager for {@link NamespaceKernel} instances (R4.4).
 */
public final class NamespaceKernels {

    @FunctionalInterface
    public interface Factory {
        NamespaceKernel create(Path directory, KernelSpec spec);
    }

    private static volatile Factory factory;

    public static void registerFactory(Factory f) {
        factory = f;
    }

    private NamespaceKernels() {}

    /**
     * Opens or creates a {@link NamespaceKernel} backed by bundles at the given directory.
     *
     * @param directory directory containing {@code runtime.bundle} and partitions
     * @param spec      kernel sizing and dimensional specification
     * @return an open {@link NamespaceKernel} ready for memory operations
     */
    public static NamespaceKernel open(Path directory, KernelSpec spec) {
        Factory f = factory;
        if (f == null) {
            try {
                Class.forName("com.spectrayan.spector.kernel.store.DefaultNamespaceKernel", true,
                        NamespaceKernels.class.getClassLoader());
                f = factory;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (f != null) {
            return f.create(directory, spec);
        }
        throw new IllegalStateException("No NamespaceKernel factory registered and DefaultNamespaceKernel could not be loaded");
    }
}
