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
package com.spectrayan.spector.cluster.idempotency;

/**
 * Outcome of attempting to reserve or look up an {@code Idempotency-Key} for a mutating request.
 *
 * @param status              reservation status
 * @param response            previously committed response when {@link Status#REPLAYED}, otherwise {@code null}
 * @param existingFingerprint fingerprint of the existing record when {@link Status#MISMATCH} or {@link Status#IN_FLIGHT}
 */
public record IdempotencyReservation(
        Status status,
        StoredIdempotentResponse response,
        RequestFingerprint existingFingerprint
) {

    public enum Status {
        /** Caller acquired the in-flight slot and should execute the write. */
        ACQUIRED,
        /** Identical request already completed within TTL; replay {@link #response()}. */
        REPLAYED,
        /** Key was already used with a different method, path, or payload fingerprint (HTTP 422). */
        MISMATCH,
        /** An identical request with this key is currently in flight (HTTP 409). */
        IN_FLIGHT
    }

    public static IdempotencyReservation acquired(RequestFingerprint fingerprint) {
        return new IdempotencyReservation(Status.ACQUIRED, null, fingerprint);
    }

    public static IdempotencyReservation replayed(StoredIdempotentResponse response, RequestFingerprint fingerprint) {
        return new IdempotencyReservation(Status.REPLAYED, response, fingerprint);
    }

    public static IdempotencyReservation mismatch(RequestFingerprint existingFingerprint) {
        return new IdempotencyReservation(Status.MISMATCH, null, existingFingerprint);
    }

    public static IdempotencyReservation inFlight(RequestFingerprint existingFingerprint) {
        return new IdempotencyReservation(Status.IN_FLIGHT, null, existingFingerprint);
    }
}
