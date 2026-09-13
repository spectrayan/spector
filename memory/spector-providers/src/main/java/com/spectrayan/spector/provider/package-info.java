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

/**
 * Provider runtime for Spector: discovery, registration, and delegation of
 * embedding and text-generation providers.
 *
 * <p>The classes in this module build on the provider SPI defined in
 * {@code spector-provider-api}. Concrete backends live in the sub-packages.</p>
 *
 * <h3>Key Classes</h3>
 * <ul>
 *   <li>{@link com.spectrayan.spector.provider.ProviderDiscovery} — loads
 *       {@link com.spectrayan.spector.provider.ProviderFactory} implementations via
 *       {@link java.util.ServiceLoader} and creates providers from configuration</li>
 *   <li>{@link com.spectrayan.spector.provider.DefaultProviderRegistry} — thread-safe registry
 *       that auto-activates the first provider registered for each type</li>
 *   <li>{@link com.spectrayan.spector.provider.DelegatingLlmProvider} — forwards generation
 *       calls to the registry's currently active generation provider</li>
 * </ul>
 */
package com.spectrayan.spector.provider;
