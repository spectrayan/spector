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
package com.spectrayan.spector.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import picocli.CommandLine;

/**
 * Spring Boot {@link ApplicationRunner} that hosts Picocli CLI execution.
 * <p>
 * Executes the {@link SpectorCtl} root command using {@link SpringPicocliFactory}
 * and captures the exit code for Spring Boot's {@link ExitCodeGenerator}.
 * </p>
 */
@Component
@Order(0)
public class SpectorPicocliRunner implements ApplicationRunner, ExitCodeGenerator {

    private final SpectorCtl spectorCtl;
    private final SpringPicocliFactory factory;
    private int exitCode;

    public SpectorPicocliRunner(SpectorCtl spectorCtl, SpringPicocliFactory factory) {
        this.spectorCtl = spectorCtl;
        this.factory = factory;
    }

    @Override
    public void run(ApplicationArguments args) {
        CommandLine cmd = new CommandLine(spectorCtl, factory)
                .setExecutionExceptionHandler(new SpectorCtl.ExceptionHandler());
        this.exitCode = cmd.execute(args.getSourceArgs());
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
