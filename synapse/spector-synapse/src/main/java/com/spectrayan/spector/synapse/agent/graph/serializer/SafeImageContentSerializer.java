/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.agent.graph.serializer;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.image.Image;
import org.bsc.langgraph4j.serializer.Serializer;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.Optional;

public class SafeImageContentSerializer implements Serializer<ImageContent> {

    @Override
    public void write(ImageContent object, ObjectOutput out) throws IOException {
        out.writeObject(object.detailLevel());
        Image image = object.image();
        String urlStr = (image.url() != null) ? image.url().toString() : null;
        writeNullableUTF(urlStr, out);
        String mimeType = (image.mimeType() != null) ? image.mimeType() : "image/png";
        out.writeUTF(mimeType);
        writeNullableUTF(image.base64Data(), out);
    }

    @Override
    public ImageContent read(ObjectInput in) throws IOException, ClassNotFoundException {
        ImageContent.DetailLevel detailLevel = (ImageContent.DetailLevel) in.readObject();
        Optional<String> urlOpt = readNullableUTF(in);
        String mimeType = in.readUTF();
        Optional<String> base64Opt = readNullableUTF(in);
        
        Image.Builder builder = Image.builder();
        urlOpt.map(URI::create).ifPresent(builder::url);
        builder.mimeType(mimeType);
        base64Opt.ifPresent(builder::base64Data);
        
        return ImageContent.from(builder.build(), detailLevel);
    }

    private void writeNullableUTF(String val, ObjectOutput out) throws IOException {
        out.writeBoolean(val != null);
        if (val != null) {
            byte[] bytes = val.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    private Optional<String> readNullableUTF(ObjectInput in) throws IOException {
        if (in.readBoolean()) {
            int length = in.readInt();
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            return Optional.of(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
        return Optional.empty();
    }
}
