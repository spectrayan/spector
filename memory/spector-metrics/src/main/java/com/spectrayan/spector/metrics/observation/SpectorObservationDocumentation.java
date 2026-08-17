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
package com.spectrayan.spector.metrics.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Declares all Spector cognitive memory observations for Micrometer metrics and distributed tracing.
 */
public enum SpectorObservationDocumentation implements ObservationDocumentation {

    /**
     * Ingestion observation for {@code remember()} operations.
     */
    MEMORY_REMEMBER {
        @Override
        public String getName() {
            return "spector.memory.remember";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.memory";
        }
    },

    /**
     * Recall observation for {@code recall()} query operations.
     */
    MEMORY_RECALL {
        @Override
        public String getName() {
            return "spector.memory.recall";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.memory";
        }
    },

    /**
     * Forget / tombstone observation for {@code forget()} operations.
     */
    MEMORY_FORGET {
        @Override
        public String getName() {
            return "spector.memory.forget";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.memory";
        }
    },

    /**
     * Reinforcement observation for {@code reinforce()} operations.
     */
    MEMORY_REINFORCE {
        @Override
        public String getName() {
            return "spector.memory.reinforce";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.memory";
        }
    },

    /**
     * Consolidation observation for {@code consolidate()} phases.
     */
    MEMORY_CONSOLIDATE {
        @Override
        public String getName() {
            return "spector.memory.consolidate";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.memory";
        }
    },

    /**
     * Storage synchronization observation for {@code sync()} operations.
     */
    MEMORY_SYNC {
        @Override
        public String getName() {
            return "spector.memory.sync";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.memory";
        }
    },

    /**
     * Task queue worker execution observation.
     */
    TASK_QUEUE_PROCESS {
        @Override
        public String getName() {
            return "spector.taskqueue.process";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.taskqueue";
        }
    },

    /**
     * Embedding pipeline observation.
     */
    PIPELINE_EMBEDDING {
        @Override
        public String getName() {
            return "spector.pipeline.embedding";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.pipeline";
        }
    },

    /**
     * LLM pipeline observation.
     */
    PIPELINE_LLM {
        @Override
        public String getName() {
            return "spector.pipeline.llm";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }

        @Override
        public String getPrefix() {
            return "spector.pipeline";
        }
    },
    RECALL_VECTOR_SEARCH {
        @Override
        public String getName() {
            return "spector.recall.vector_search";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.recall";
        }
    },
    RECALL_BM25_SEARCH {
        @Override
        public String getName() {
            return "spector.recall.bm25_search";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.recall";
        }
    },
    RECALL_SCORING {
        @Override
        public String getName() {
            return "spector.recall.scoring";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.recall";
        }
    },
    RECALL_GRAPH_EXPANSION {
        @Override
        public String getName() {
            return "spector.recall.graph_expansion";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.recall";
        }
    },
    RECALL_CONTRADICTION {
        @Override
        public String getName() {
            return "spector.recall.contradiction";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.recall";
        }
    },
    REMEMBER_TAG_EXTRACTION {
        @Override
        public String getName() {
            return "spector.remember.tag_extraction";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.remember";
        }
    },
    REMEMBER_CHUNKING {
        @Override
        public String getName() {
            return "spector.remember.chunking";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.remember";
        }
    },
    REMEMBER_ENTITY_EXTRACTION {
        @Override
        public String getName() {
            return "spector.remember.entity_extraction";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.remember";
        }
    },
    REMEMBER_GRAPH_SYNC {
        @Override
        public String getName() {
            return "spector.remember.graph_sync";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.remember";
        }
    },
    SCORING_COGNITIVE {
        @Override
        public String getName() {
            return "spector.scoring.cognitive";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.scoring";
        }
    },
    SCORING_HABITUATION {
        @Override
        public String getName() {
            return "spector.scoring.habituation";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.scoring";
        }
    },
    SCORING_STDP {
        @Override
        public String getName() {
            return "spector.scoring.stdp";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.scoring";
        }
    },
    SCORING_HEBBIAN {
        @Override
        public String getName() {
            return "spector.scoring.hebbian";
        }
        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeys.values();
        }
        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinalityKeys.values();
        }
        @Override
        public String getPrefix() {
            return "spector.scoring";
        }
    };

    public enum LowCardinalityKeys implements KeyName {
        OPERATION {
            @Override
            public String asString() {
                return "spector.operation";
            }
        },
        TIER {
            @Override
            public String asString() {
                return "spector.tier";
            }
        },
        NAMESPACE {
            @Override
            public String asString() {
                return "spector.namespace";
            }
        },
        STATUS {
            @Override
            public String asString() {
                return "spector.status";
            }
        },
        ERROR {
            @Override
            public String asString() {
                return "error";
            }
        }
    }

    public enum HighCardinalityKeys implements KeyName {
        MEMORY_ID {
            @Override
            public String asString() {
                return "spector.memory_id";
            }
        },
        SESSION_ID {
            @Override
            public String asString() {
                return "spector.session_id";
            }
        },
        QUERY {
            @Override
            public String asString() {
                return "spector.query";
            }
        },
        TASK_ID {
            @Override
            public String asString() {
                return "spector.task_id";
            }
        }
    }
}
