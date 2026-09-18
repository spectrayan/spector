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
package com.spectrayan.spector.synapse.config.sql;

import com.spectrayan.spector.synapse.error.SynapseDatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlQueryLoaderTest {

    private SqlQueryLoader sqlLoader;

    @BeforeEach
    void setUp() {
        sqlLoader = new SqlQueryLoader();
        sqlLoader.prewarm();
    }

    @Test
    @DisplayName("Loads existing SQL query without .sql extension")
    void loadsQueryWithoutExtension() {
        String query = sqlLoader.load("users/find-by-username");
        assertThat(query).isNotBlank();
        assertThat(query).contains("SELECT user_id, username");
        assertThat(query).contains("FROM users");
    }

    @Test
    @DisplayName("Loads existing SQL query with .sql extension")
    void loadsQueryWithExtension() {
        String query = sqlLoader.load("users/insert-user.sql");
        assertThat(query).isNotBlank();
        assertThat(query).contains("INSERT INTO users");
    }

    @Test
    @DisplayName("Loads routes and credentials queries properly")
    void loadsOtherDomainQueries() {
        String routeQuery = sqlLoader.load("routes/merge-route");
        assertThat(routeQuery).contains("MERGE INTO connector_routes");

        String credQuery = sqlLoader.load("credentials/find-by-name");
        assertThat(credQuery).contains("SELECT credential_id");
    }

    @Test
    @DisplayName("Throws SynapseDatabaseException when query file does not exist")
    void throwsOnMissingFile() {
        assertThatThrownBy(() -> sqlLoader.load("nonexistent/query"))
                .isInstanceOf(SynapseDatabaseException.class)
                .hasMessageContaining("loadSqlQuery");
    }
}
