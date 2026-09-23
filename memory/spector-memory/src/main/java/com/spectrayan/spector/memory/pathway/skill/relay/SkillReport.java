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
package com.spectrayan.spector.memory.pathway.skill.relay;

import com.spectrayan.spector.memory.pathway.skill.model.SkillBody;
import java.util.List;

public record SkillReport(
    String skillId,
    List<SkillSignal.ParentRef> parents,
    String duplicateOf,
    SkillBody body,
    SkillSignal.Mode mode,
    boolean reinforced
) {
    public static SkillReport empty() {
        return new SkillReport(null, List.of(), null, null, SkillSignal.Mode.DRY_RUN, false);
    }

    public SkillBody extractedBody() {
        return body;
    }
}
