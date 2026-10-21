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
package com.spectrayan.spector.provider.onnx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import dev.langchain4j.model.embedding.onnx.AbstractInProcessEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxBertBiEncoder;
import dev.langchain4j.model.embedding.onnx.PoolingMode;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;

/**
 * LangChain4j BERT encoder on an ONNX Runtime session whose intra-op thread pool Spector controls.
 *
 * <p>The stock LangChain4j models open their session with ONNX Runtime's default pool (one thread per
 * core). With onnxruntime 1.20 (Java) and the int8 MiniLM model, that default returned a wrong vector
 * for a few percent of calls on an 8-core host, while 1–4 threads gave identical vectors every time.
 * Tokenisation, pooling, normalisation and long-text handling are LangChain4j's unchanged.</p>
 */
public final class ThreadLimitedOnnxEmbeddingModel extends AbstractInProcessEmbeddingModel {

    /** Resource names of the int8 all-MiniLM-L6-v2 files inside the bundled LangChain4j artifact. */
    static final String BUNDLED_MINILM_MODEL = "/all-minilm-l6-v2-q.onnx";
    static final String BUNDLED_MINILM_TOKENIZER = "/all-minilm-l6-v2-q-tokenizer.json";

    private final OnnxBertBiEncoder encoder;
    private final int intraOpThreads;

    private ThreadLimitedOnnxEmbeddingModel(OnnxBertBiEncoder encoder, int intraOpThreads) {
        super(null);
        this.encoder = encoder;
        this.intraOpThreads = intraOpThreads;
    }

    /**
     * Loads a model and tokenizer from disk.
     *
     * @param model          the ONNX file
     * @param tokenizer      the {@code tokenizer.json}
     * @param pooling        pooling mode
     * @param intraOpThreads ONNX Runtime intra-op threads (at least 1)
     * @return the model
     * @throws SpectorConfigException (SPE-110-011) if either file cannot be read or the session fails
     */
    public static ThreadLimitedOnnxEmbeddingModel fromFiles(Path model, Path tokenizer, PoolingMode pooling,
                                                            int intraOpThreads) {
        try {
            return create(Files.readAllBytes(model), Files.readAllBytes(tokenizer), pooling, intraOpThreads,
                    model.getFileName().toString());
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model-path",
                    model.getFileName() + " could not be read");
        }
    }

    /**
     * Loads the int8 all-MiniLM-L6-v2 that ships in {@code langchain4j-embeddings-all-minilm-l6-v2-q}.
     *
     * @param intraOpThreads ONNX Runtime intra-op threads (at least 1)
     * @return the model
     * @throws SpectorConfigException (SPE-110-011) if the artifact is not on the classpath
     */
    public static ThreadLimitedOnnxEmbeddingModel bundledMiniLm(int intraOpThreads) {
        return create(resource(BUNDLED_MINILM_MODEL), resource(BUNDLED_MINILM_TOKENIZER), PoolingMode.MEAN,
                intraOpThreads, "all-MiniLM-L6-v2");
    }

    private static ThreadLimitedOnnxEmbeddingModel create(byte[] model, byte[] tokenizer, PoolingMode pooling,
                                                          int intraOpThreads, String label) {
        if (intraOpThreads < 1) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID,
                    "spector.provider.embedding.intra-op-threads", "must be at least 1, got " + intraOpThreads);
        }
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(intraOpThreads);
            options.setInterOpNumThreads(1);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            OrtSession session = env.createSession(model, options);
            OnnxBertBiEncoder encoder = new OnnxBertBiEncoder(env, session, new ByteArrayInputStream(tokenizer), pooling);
            return new ThreadLimitedOnnxEmbeddingModel(encoder, intraOpThreads);
        } catch (OrtException | RuntimeException e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding",
                    label + " could not be loaded (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static byte[] resource(String name) {
        try (InputStream in = ThreadLimitedOnnxEmbeddingModel.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model",
                        "the bundled all-MiniLM-L6-v2 is not on the classpath (" + name + ")");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new SpectorConfigException(ErrorCode.EMBEDDING_MODEL_INVALID, "spector.provider.embedding.model",
                    "the bundled all-MiniLM-L6-v2 could not be read");
        }
    }

    /** @return the intra-op thread count of the session */
    public int intraOpThreads() {
        return intraOpThreads;
    }

    @Override
    protected OnnxBertBiEncoder model() {
        return encoder;
    }
}
