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
package com.spectrayan.spector.connector.model;

/**
 * Built-in connector type identifiers.
 *
 * <p>These are <em>string constants</em> rather than an enum to allow
 * custom connector types without code changes (admins can define new
 * types via custom YAML templates). Built-in types are used by
 * {@link TemplateDescriptor} and {@link RouteAdapter} for matching.</p>
 */
public final class ConnectorType {
    private ConnectorType() {}

    /** Local file system watcher. */
    public static final String FILE_WATCH = "FILE_WATCH";
    /** AWS S3 / MinIO bucket polling. */
    public static final String S3 = "S3";
    /** HTTP/REST API polling. */
    public static final String REST_API = "REST_API";
    /** Inbound HTTP webhook receiver. */
    public static final String WEBHOOK = "WEBHOOK";
    /** JDBC database query polling. */
    public static final String DATABASE = "DATABASE";
    /** Slack messaging. */
    public static final String SLACK = "SLACK";
    /** Email via SMTP. */
    public static final String EMAIL = "EMAIL";
    /** Apache Kafka / message queue. */
    public static final String KAFKA = "KAFKA";
    /** Google Drive document ingestion. */
    public static final String GOOGLE_DRIVE = "GOOGLE_DRIVE";
    /** Notion page/database ingestion. */
    public static final String NOTION = "NOTION";
    /** GitHub repository ingestion (issues, PRs, README, wiki). */
    public static final String GITHUB = "GITHUB";
    /** MongoDB collection polling. */
    public static final String MONGODB = "MONGODB";
    /** Direct Camel endpoint (testing/programmatic use). */
    public static final String DIRECT = "DIRECT";
    /** Custom Camel YAML DSL route. */
    public static final String CUSTOM_YAML = "CUSTOM_YAML";
}
