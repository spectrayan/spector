import re

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/DefaultSpectorMemory.java', 'r') as f:
    content = f.read()

# Add QuiesceGuard field
field_insert_pos = content.find('private final MutationPolicy mutationPolicy;')
if field_insert_pos != -1:
    content = content[:field_insert_pos] + 'private final com.spectrayan.spector.memory.sync.QuiesceGuard quiesceGuard = new com.spectrayan.spector.memory.sync.QuiesceGuard();\n    ' + content[field_insert_pos:]

def wrap_method(method_name, content):
    pattern = re.compile(r'(public\s+(?:void|com\.spectrayan\.spector\.memory\.model\.(?:Forget|Purge)Result)\s+' + method_name + r'\s*\([^)]*\)\s*(?:throws\s+[^\{]+)?\s*\{)(.*?)(^\s*\})', re.MULTILINE | re.DOTALL)
    
    def repl(m):
        sig = m.group(1)
        body = m.group(2)
        end_brace = m.group(3)
        
        if 'quiesceGuard.acquireWritePermit()' in body:
            return m.group(0)

        new_body = "\n        try (var permit = quiesceGuard.acquireWritePermit()) {" + \
                   "".join(["\n    " + line for line in body.split("\n")]) + "\n        }"
        return sig + new_body + end_brace

    return pattern.sub(repl, content)

for method in ['remember', 'forget', 'forgetWithResult', 'purge', 'reinforce', 'consolidate']:
    content = wrap_method(method, content)

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/DefaultSpectorMemory.java', 'w') as f:
    f.write(content)
