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
package com.spectrayan.spector.cluster.membership;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Static, file- or configuration-based membership source for Phase 1 (Req R7.1, R7.2).
 *
 * <p>Fails readiness loudly if the member list or members file is empty, missing, or malformed (Req R7.3, L2).
 * <b>Never</b> defaults to self-ownership on configuration errors.</p>
 */
public final class StaticMembershipSource implements MembershipSource {

    private final CellMembership membership;

    public StaticMembershipSource(CellMembership membership) {
        this.membership = Objects.requireNonNull(membership, "membership must not be null");
        if (membership.members().isEmpty()) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "members", "static membership must contain at least one member (Req R7.3)");
        }
    }

    public StaticMembershipSource(String cellId, int ringVersion, List<String> members) {
        this(new CellMembership(cellId, ringVersion, members));
    }

    /**
     * Reads membership from a newline-delimited members file (e.g. for Docker Compose).
     *
     * @param cellId      cell identifier
     * @param ringVersion ring generation
     * @param membersFile path to members file
     * @throws SpectorValidationException if the file does not exist or has no valid members
     */
    public StaticMembershipSource(String cellId, int ringVersion, Path membersFile) {
        Objects.requireNonNull(membersFile, "membersFile must not be null");
        if (!Files.exists(membersFile)) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "membersFile", "membership file does not exist: " + membersFile);
        }
        try {
            List<String> lines = Files.readAllLines(membersFile).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
            if (lines.isEmpty()) {
                throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                        "membersFile", "membership file is empty or contains only comments: " + membersFile);
            }
            this.membership = new CellMembership(cellId, ringVersion, lines);
        } catch (IOException e) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "membersFile", "failed to read membership file: " + membersFile, e);
        }
    }

    @Override
    public CellMembership current() {
        return membership;
    }
}
