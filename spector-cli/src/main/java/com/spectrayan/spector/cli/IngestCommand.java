package com.spectrayan.spector.cli;

import com.spectrayan.spector.client.SpectorClient;
import com.spectrayan.spector.client.SpectorClientException;
import com.spectrayan.spector.client.SpectorConnectionException;
import com.spectrayan.spector.client.model.IngestRequest;
import com.spectrayan.spector.client.model.IngestResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ingest a document into the Spector Search engine.
 */
@Command(
        name = "ingest",
        description = "Ingest a document into Spector Search.",
        mixinStandardHelpOptions = true
)
class IngestCommand extends BaseCommand {

    @CommandLine.Option(names = {"--id"}, description = "Document ID (auto-generated if not provided).")
    private String documentId;

    @CommandLine.Option(names = {"--title"}, description = "Document title.")
    private String title;

    @CommandLine.Option(names = {"--content"}, description = "Document content (text). Provide either --content or --file.")
    private String content;

    @CommandLine.Option(names = {"--file"}, description = "Path to file to ingest.")
    private Path file;

    @Override
    public void run() {
        String text = resolveContent();
        if (text == null) {
            err().println("Error: Provide either --content or --file.");
            spec.commandLine().usage(err());
            return;
        }

        try (var client = createClient()) {
            IngestRequest request = new IngestRequest();
            request.setId(documentId);
            request.setTitle(title);
            request.setContent(text);

            IngestResponse response = client.ingest(request);

            if (isJson()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", response.getId());
                result.put("indexed", response.isIndexed());
                result.put("autoEmbedded", response.isAutoEmbedded());
                OutputFormatter.printJson(out(), result);
            } else {
                out().println("Document ingested successfully.");
                out().println("  ID:            " + response.getId());
                out().println("  Indexed:       " + response.isIndexed());
                out().println("  Auto-Embedded: " + response.isAutoEmbedded());
            }
        } catch (SpectorConnectionException e) {
            handleConnectionError(e);
        } catch (SpectorClientException e) {
            err().println("Error: " + e.getMessage());
        }
    }

    private String resolveContent() {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (file != null) {
            try {
                return Files.readString(file);
            } catch (IOException e) {
                err().println("Error: Cannot read file '" + file + "': " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}
