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
package com.spectrayan.spector.memory.kernel.layout;

/**
 * Represents an operation on a single {@code float}-valued operand that produces
 * a {@code float}-valued result. This is the primitive type specialization of
 * {@link java.util.function.UnaryOperator} for {@code float}.
 */
@FunctionalInterface
public interface FloatUnaryOperator {
    
    /**
     * Applies this operator to the given operand.
     *
     * @param operand the operand
     * @return the operator result
     */
    float applyAsFloat(float operand);
}
