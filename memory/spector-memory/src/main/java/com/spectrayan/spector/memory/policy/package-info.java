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
 * The single seam through which a host imposes policy the engine cannot evaluate itself.
 *
 * <p>Two distinct questions arrive here, deliberately kept as separate methods on one interface:</p>
 * <ul>
 *   <li><b>May this data be destroyed?</b> — legal hold, recorded in the account catalog
 *       ({@link com.spectrayan.spector.memory.policy.MutationPolicy#checkDeletion});</li>
 *   <li><b>Is this node still entitled to write?</b> — fence currency, decided by the cell coordinator
 *       ({@link com.spectrayan.spector.memory.policy.MutationPolicy#checkWrite}).</li>
 * </ul>
 *
 * <p>One interface because both are the same shape of problem and two interception mechanisms would be one
 * too many. Two methods because the questions have different inputs, different answers and different failure
 * modes; a single method would need a union-typed request and one exception channel for unrelated
 * refusals.</p>
 *
 * <p>The default implementation permits everything, so embedded and OSS use is unaffected. Enterprise
 * deployments install an implementation over their catalog.</p>
 */
package com.spectrayan.spector.memory.policy;
