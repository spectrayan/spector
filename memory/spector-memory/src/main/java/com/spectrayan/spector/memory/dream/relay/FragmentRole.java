/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Enum representing typed components of an episodic scene decomposition.
 * Biological analog: Distinct cortical regions encode different aspects of an event 
 * (e.g., spatial context in hippocampus, objects in inferotemporal cortex, affect in amygdala).
 *
 * @since 1.4.0
 */
public enum FragmentRole {
    AGENT,
    ACTION,
    OBJECT,
    LOCATION,
    TEMPORAL,
    AFFECT
}
