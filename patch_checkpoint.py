import re

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/sync/CheckpointEngine.java', 'r') as f:
    content = f.read()

# Add QuiesceGuard field
field_insert_pos = content.find('private final CognitiveMemoryRouter cognitiveRouter;')
if field_insert_pos != -1:
    content = content[:field_insert_pos] + 'private QuiesceGuard quiesceGuard;\n    ' + content[field_insert_pos:]

# Add setter
setter_code = """
    public void setQuiesceGuard(QuiesceGuard quiesceGuard) {
        this.quiesceGuard = quiesceGuard;
    }
"""
idx_set_event = content.find('public void setEventBus')
if idx_set_event != -1:
    content = content[:idx_set_event] + setter_code + '\n    ' + content[idx_set_event:]


# Wrap Steps 1-3
start_marker = "// Step 1: Force all persistent"
end_marker = "// Step 5: Read the WAL high-water mark"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx != -1 and end_idx != -1:
    before = content[:start_idx]
    middle = content[start_idx:end_idx]
    after = content[end_idx:]
    
    indented_middle = "".join(["    " + line + "\n" for line in middle.split("\n")])
    
    wrap_code = """if (quiesceGuard != null) {
            try (var quiesce = quiesceGuard.acquireQuiesce(5, java.util.concurrent.TimeUnit.SECONDS)) {
""" + indented_middle + """                log.info("Quiesce duration: {} ms", quiesceGuard.lastQuiesceDurationNanos() / 1_000_000);
            }
        } else {
""" + indented_middle + """        }

        """
    content = before + wrap_code + after

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/sync/CheckpointEngine.java', 'w') as f:
    f.write(content)
