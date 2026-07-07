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
package com.spectrayan.spector.synapse.connector.templates;

import com.spectrayan.spector.synapse.connector.ConnectorDto.ConnectorType;
import com.spectrayan.spector.synapse.connector.ConnectorDto.FieldDescriptor;
import com.spectrayan.spector.synapse.connector.ConnectorDto.TemplateDescriptor;
import com.spectrayan.spector.synapse.connector.TemplateRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registers built-in connector templates at startup.
 *
 * <p>Provides out-of-the-box templates for common data sources:
 * File System, Git, Web Crawler, REST API, Database, S3.</p>
 */
@Component
public class BuiltInTemplates {

    private static final Logger log = LoggerFactory.getLogger(BuiltInTemplates.class);

    private final TemplateRegistry registry;

    public BuiltInTemplates(TemplateRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void registerAll() {
        registry.register(fileSystem());
        registry.register(git());
        registry.register(webCrawler());
        registry.register(restApi());
        registry.register(database());
        registry.register(s3());
        log.info("[ConnectorTemplates] Registered {} built-in templates", registry.count());
    }

    private TemplateDescriptor fileSystem() {
        return new TemplateDescriptor(
                "file-system",
                "File System",
                "Ingest documents from a local or mounted directory. Supports recursive scanning with glob filters.",
                ConnectorType.FILE_SYSTEM,
                "📁",
                Map.of(
                        "path", new FieldDescriptor("path", "Directory Path", "string", true, "", "Absolute path to scan"),
                        "patterns", new FieldDescriptor("patterns", "File Patterns", "string", false, "**/*.md,**/*.txt,**/*.pdf", "Comma-separated glob patterns"),
                        "recursive", new FieldDescriptor("recursive", "Recursive", "boolean", false, "true", "Scan subdirectories"),
                        "maxDepth", new FieldDescriptor("maxDepth", "Max Depth", "number", false, "10", "Maximum directory depth")
                ),
                "0 */6 * * *"  // every 6 hours
        );
    }

    private TemplateDescriptor git() {
        return new TemplateDescriptor(
                "git",
                "Git Repository",
                "Clone and ingest files from a Git repository. Supports branch selection and path filtering.",
                ConnectorType.GIT,
                "🔀",
                Map.of(
                        "repoUrl", new FieldDescriptor("repoUrl", "Repository URL", "string", true, "", "Git clone URL (https or ssh)"),
                        "branch", new FieldDescriptor("branch", "Branch", "string", false, "main", "Branch to clone"),
                        "paths", new FieldDescriptor("paths", "Include Paths", "string", false, "", "Comma-separated path prefixes to include"),
                        "token", new FieldDescriptor("token", "Access Token", "password", false, "", "Personal access token for private repos")
                ),
                "0 0 * * *"  // daily at midnight
        );
    }

    private TemplateDescriptor webCrawler() {
        return new TemplateDescriptor(
                "web-crawler",
                "Web Crawler",
                "Crawl web pages and ingest text content. Supports depth-limited crawling with domain restrictions.",
                ConnectorType.WEB_CRAWLER,
                "🌐",
                Map.of(
                        "startUrl", new FieldDescriptor("startUrl", "Start URL", "string", true, "", "URL to start crawling from"),
                        "maxPages", new FieldDescriptor("maxPages", "Max Pages", "number", false, "100", "Maximum pages to crawl"),
                        "maxDepth", new FieldDescriptor("maxDepth", "Max Depth", "number", false, "3", "Maximum link depth"),
                        "allowedDomains", new FieldDescriptor("allowedDomains", "Allowed Domains", "string", false, "", "Comma-separated allowed domains")
                ),
                "0 2 * * *"  // daily at 2 AM
        );
    }

    private TemplateDescriptor restApi() {
        return new TemplateDescriptor(
                "rest-api",
                "REST API",
                "Fetch and ingest data from a REST API endpoint. Supports pagination and authentication.",
                ConnectorType.REST_API,
                "🔗",
                Map.of(
                        "url", new FieldDescriptor("url", "API URL", "string", true, "", "Base URL for the API"),
                        "method", new FieldDescriptor("method", "HTTP Method", "select", false, "GET", "GET or POST"),
                        "headers", new FieldDescriptor("headers", "Headers", "string", false, "", "Custom headers (key:value pairs)"),
                        "authToken", new FieldDescriptor("authToken", "Auth Token", "password", false, "", "Bearer token for authentication")
                ),
                "0 */4 * * *"  // every 4 hours
        );
    }

    private TemplateDescriptor database() {
        return new TemplateDescriptor(
                "database",
                "Database",
                "Query a relational database and ingest rows as memories. Supports JDBC-compatible databases.",
                ConnectorType.DATABASE,
                "🗄️",
                Map.of(
                        "jdbcUrl", new FieldDescriptor("jdbcUrl", "JDBC URL", "string", true, "", "JDBC connection URL"),
                        "username", new FieldDescriptor("username", "Username", "string", false, "", "Database username"),
                        "password", new FieldDescriptor("password", "Password", "password", false, "", "Database password"),
                        "query", new FieldDescriptor("query", "SQL Query", "string", true, "", "SELECT query to execute"),
                        "textColumn", new FieldDescriptor("textColumn", "Text Column", "string", true, "", "Column containing text to ingest")
                ),
                "0 1 * * *"  // daily at 1 AM
        );
    }

    private TemplateDescriptor s3() {
        return new TemplateDescriptor(
                "s3",
                "Amazon S3",
                "Ingest files from an S3 bucket. Supports prefix filtering and file type restrictions.",
                ConnectorType.S3,
                "☁️",
                Map.of(
                        "bucket", new FieldDescriptor("bucket", "Bucket Name", "string", true, "", "S3 bucket name"),
                        "prefix", new FieldDescriptor("prefix", "Key Prefix", "string", false, "", "S3 key prefix to filter"),
                        "region", new FieldDescriptor("region", "AWS Region", "string", false, "us-east-1", "AWS region"),
                        "accessKey", new FieldDescriptor("accessKey", "Access Key", "password", false, "", "AWS access key ID"),
                        "secretKey", new FieldDescriptor("secretKey", "Secret Key", "password", false, "", "AWS secret access key")
                ),
                "0 */12 * * *"  // every 12 hours
        );
    }
}
