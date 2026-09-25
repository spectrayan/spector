with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/sync/CheckpointEngine.java', 'r') as f:
    content = f.read()

content = content.replace('                log.info("Quiesce duration: {} ms", quiesceGuard.lastQuiesceDurationNanos() / 1_000_000);\n            }\n        } else {', 
                          '            }\n            log.info("Quiesce duration: {} ms", quiesceGuard.lastQuiesceDurationNanos() / 1_000_000);\n        } else {')

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/sync/CheckpointEngine.java', 'w') as f:
    f.write(content)
