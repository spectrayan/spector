import re

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/DefaultSpectorMemory.java', 'r') as f:
    content = f.read()

field_insert_pos = content.find('private final MutationPolicy mutationPolicy;')
if field_insert_pos != -1:
    content = content[:field_insert_pos] + 'private final com.spectrayan.spector.memory.sync.QuiesceGuard quiesceGuard = new com.spectrayan.spector.memory.sync.QuiesceGuard();\n    ' + content[field_insert_pos:]

def wrap_method(method_name, content):
    # we need to find occurrences of `public void <method_name>(...`
    # or `public ForgetResult <method_name>(...`
    
    # We will search for 'public ' + something + method_name + '('
    pattern_str = r'public\s+(?:void|com\.spectrayan\.spector\.memory\.model\.(?:Forget|Purge)Result)\s+' + method_name + r'\s*\([^)]*\)\s*(?:throws\s+[^\{]+)?\s*\{'
    
    idx = 0
    while True:
        match = re.search(pattern_str, content[idx:])
        if not match:
            break
        
        start_idx = idx + match.end()
        # Find the matching closing brace
        brace_count = 1
        end_idx = start_idx
        while brace_count > 0 and end_idx < len(content):
            if content[end_idx] == '{':
                brace_count += 1
            elif content[end_idx] == '}':
                brace_count -= 1
            end_idx += 1
            
        if brace_count == 0:
            body = content[start_idx:end_idx-1]
            if 'quiesceGuard.acquireWritePermit()' not in body:
                new_body = "\n        try (var permit = quiesceGuard.acquireWritePermit()) {" + body + "        }\n    "
                content = content[:start_idx] + new_body + content[end_idx-1:]
                idx = start_idx + len(new_body) + 1
            else:
                idx = end_idx
        else:
            idx = start_idx
            
    return content

for method in ['remember', 'forget', 'forgetWithResult', 'purge', 'reinforce', 'consolidate']:
    content = wrap_method(method, content)

with open('memory/spector-memory/src/main/java/com/spectrayan/spector/memory/DefaultSpectorMemory.java', 'w') as f:
    f.write(content)

